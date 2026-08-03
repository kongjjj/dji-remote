package com.dimadesu.djiremote.dji

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "DjiDevice"

// Set to true to enable file logging and the "Share debug logs" button.
// Keep false for production to avoid unbounded log file growth during long streams.
const val DEBUG_LOGGING_ENABLED = false

// File logger — writes timestamped log lines to dji_debug.txt on external storage.
// Useful for remote testers who can't use adb.
object DjiFileLogger {
    private var logFile: File? = null

    fun init(context: Context) {
        if (!DEBUG_LOGGING_ENABLED) return
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, "dji_debug.txt").also {
            it.writeText("") // Clear previous session
        }
    }

    fun log(msg: String) {
        if (!DEBUG_LOGGING_ENABLED) return
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        try { logFile?.appendText("$ts $msg\n") } catch (_: Exception) {}
    }

    fun getPath(): String? = logFile?.absolutePath

    /** Returns the log file if it exists and is non-empty, or null. */
    fun getFile(): File? = logFile?.takeIf { it.exists() && it.length() > 0 }
}

// UUIDs from the iOS implementation (16-bit) expanded to 128-bit base
val FFF4_UUID: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
val FFF5_UUID: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")

// Transaction ids / targets / types ported from the iOS implementation
private const val PAIR_TRANSACTION_ID = 0x8092
private const val STOP_STREAMING_TRANSACTION_ID = 0xEAC8
private const val PREPARING_TO_LIVESTREAM_TRANSACTION_ID = 0x8C12
private const val SETUP_WIFI_TRANSACTION_ID = 0x8C19
private const val START_STREAMING_TRANSACTION_ID = 0x8C2C
private const val CONFIGURE_TRANSACTION_ID = 0x8C2D

private const val PAIR_TARGET = 0x0702
private const val STOP_STREAMING_TARGET = 0x0802
private const val PREPARING_TO_LIVESTREAM_TARGET = 0x0802
private const val SETUP_WIFI_TARGET = 0x0702
private const val CONFIGURE_TARGET = 0x0102
private const val START_STREAMING_TARGET = 0x0802

private const val PAIR_TYPE = 0x450740
private const val STOP_STREAMING_TYPE = 0x8E0240
private const val PREPARING_TO_LIVESTREAM_TYPE = 0xE10240
private const val SETUP_WIFI_TYPE = 0x470740
private const val CONFIGURE_TYPE = 0x8E0240
private const val START_STREAMING_TYPE = 0x780840

private const val PAIR_PIN_CODE = "mbln"

enum class DjiDeviceState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    CHECKING_IF_PAIRED,
    PAIRING,
    CLEANING_UP,
    PREPARING_STREAM,
    SETTING_UP_WIFI,
    WIFI_SETUP_FAILED,
    CONFIGURING,
    STARTING_STREAM,
    STREAMING,
    STOPPING_STREAM,
    RECONNECTING
}

interface DjiDeviceDelegate {
    fun djiDeviceStreamingState(device: DjiDevice, state: DjiDeviceState)
    fun djiDeviceBatteryPercentage(device: DjiDevice, batteryPercentage: Int)
}

class DjiDevice(private val context: Context) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var fff5Characteristic: BluetoothGattCharacteristic? = null
    private var fff4Characteristic: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    // Write queue
    private val writeChunkSize = 20 // Fixed 20-byte chunks like working Android reference
    private val writeQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var isWriting: Boolean = false
    private val writeIntervalMs: Long = 30L // pacing between writes for NO_RESPONSE

    // RX reassembly buffer for multi-chunk message reassembly
    private var rxBuffer = ByteArray(0)

    // Descriptor write queue
    private val descriptorWriteQueue: ArrayDeque<BluetoothGattDescriptor> = ArrayDeque()
    private var isWritingDescriptor: Boolean = false

    // state machine fields
    private var wifiSsid: String? = null
    private var wifiPassword: String? = null
    private var rtmpUrl: String? = null
    private var resolution: SettingsDjiDeviceResolution? = null
    private var fps: Int = 30
    private var bitrateKbps: Int = 4000
    private var imageStabilization: SettingsDjiDeviceImageStabilization? = null
    private var deviceAddress: String? = null
    private var model: SettingsDjiDeviceModel = SettingsDjiDeviceModel.UNKNOWN
    private var batteryPercentage: Int? = null

    private var state: DjiDeviceState = DjiDeviceState.IDLE
    var delegate: DjiDeviceDelegate? = null

    // timers (handler + runnable)
    private var startStreamingRunnable: Runnable? = null
    private var stopStreamingRunnable: Runnable? = null

    // 重連相關
    private var userStoppedManually: Boolean = false

    // Write to both logcat and the debug log file.
    private fun djiLog(msg: String) {
        Log.d(TAG, msg)
        DjiFileLogger.log(msg)
    }

    fun startLiveStream(
        address: String,
        wifiSsid: String,
        wifiPassword: String,
        rtmpUrl: String,
        resolution: SettingsDjiDeviceResolution,
        fps: Int,
        bitrateKbps: Int,
        imageStabilization: SettingsDjiDeviceImageStabilization,
        model: SettingsDjiDeviceModel
    ) {
        userStoppedManually = false

        DjiFileLogger.init(context)
        djiLog("=== startLiveStream: address=$address, model=$model ===")
        djiLog("  WiFi: $wifiSsid")
        djiLog("  RTMP: $rtmpUrl")
        djiLog("  Resolution: $resolution, FPS: $fps, Bitrate: ${bitrateKbps}kbps")
        djiLog("  Log file: ${DjiFileLogger.getPath()}")

        // configure
        this.wifiSsid = wifiSsid
        this.wifiPassword = wifiPassword
        this.rtmpUrl = rtmpUrl
        this.resolution = resolution
        this.fps = fps
        this.bitrateKbps = bitrateKbps
        this.imageStabilization = imageStabilization
        this.deviceAddress = address
        this.model = model

        reset()
        startStartStreamingTimer()
        setState(DjiDeviceState.DISCOVERING)
        Log.d(TAG, "Connecting to device at $address...")
        connectToAddress(address)
    }

    fun stopLiveStream() {
        if (state == DjiDeviceState.IDLE) return

        userStoppedManually = true

        stopStartStreamingTimer()
        startStopStreamingTimer()
        sendStopStream()
        setState(DjiDeviceState.STOPPING_STREAM)
    }

    fun getBatteryPercentage(): Int? = batteryPercentage

    private fun reset() {
        stopStartStreamingTimer()
        stopStopStreamingTimer()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        fff5Characteristic = null
        batteryPercentage = null
        rxBuffer = ByteArray(0)
        setState(DjiDeviceState.IDLE)
    }

    private fun startStartStreamingTimer() {
        stopStartStreamingTimer()
        startStreamingRunnable = Runnable { startStreamingTimerExpired() }
        mainHandler.postDelayed(startStreamingRunnable!!, 60_000)
    }

    private fun stopStartStreamingTimer() {
        startStreamingRunnable?.let { mainHandler.removeCallbacks(it) }
        startStreamingRunnable = null
    }

    private fun startStreamingTimerExpired() {
        reset()
    }

    private fun startStopStreamingTimer() {
        stopStopStreamingTimer()
        stopStreamingRunnable = Runnable { stopStreamingTimerExpired() }
        mainHandler.postDelayed(stopStreamingRunnable!!, 10_000)
    }

    private fun stopStopStreamingTimer() {
        stopStreamingRunnable?.let { mainHandler.removeCallbacks(it) }
        stopStreamingRunnable = null
    }

    private fun stopStreamingTimerExpired() {
        reset()
    }

    private fun setState(newState: DjiDeviceState) {
        if (newState == state) return
        djiLog("State: $state -> $newState")
        state = newState
        delegate?.djiDeviceStreamingState(this, state)
    }

    fun getState(): DjiDeviceState = state

    fun connectToAddress(address: String) {
        Log.d(TAG, "connectToAddress: $address")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter is null!")
            return
        }
        val device = adapter.getRemoteDevice(address)
        Log.d(TAG, "Got remote device, bond state: ${device.bondState}")

        // DJI uses application-level pairing via the "mbln" message
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        setState(DjiDeviceState.CONNECTING)
    }

    private fun sendPairingMessage() {
        Log.d(TAG, "========== SENDING PAIRING MESSAGE ==========")

        // Send pair message via FFF5 (same path as all other messages)
        val pairPayload = DjiPairMessagePayload(PAIR_PIN_CODE).encode()
        val msg = DjiMessage(PAIR_TARGET, PAIR_TRANSACTION_ID, PAIR_TYPE, pairPayload)
        writeMessage(msg)
    }

    private fun writeNextDescriptor() {
        if (isWritingDescriptor) return
        val descriptor = descriptorWriteQueue.removeFirstOrNull()
        if (descriptor == null) {
            Log.d(TAG, "writeNextDescriptor: queue empty")
            return
        }

        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.e(TAG, "writeNextDescriptor: gatt is null!")
            return
        }

        Log.d(TAG, "Writing descriptor ${descriptor.characteristic.uuid}...")
        isWritingDescriptor = true

        // Use NOTIFY (0x0100) for ALL characteristics - DJI camera doesn't support INDICATE on Android
        val descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE // 0x01 00

        // Use new API for Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeDescriptor(descriptor, descriptorValue)
            Log.d(TAG, "  Descriptor write initiated (new API): result=$result, value=0x0100 (NOTIFY)")
        } else {
            descriptor.value = descriptorValue
            val result = gatt.writeDescriptor(descriptor)
            Log.d(TAG, "  Descriptor write initiated (old API): result=$result, value=0x0100 (NOTIFY)")
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        setState(DjiDeviceState.IDLE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected! Requesting MTU...")
                try {
                    gatt.requestMtu(128)
                } catch (e: Exception) {
                    Log.w(TAG, "MTU request failed, discovering services anyway")
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from device (status=$status)")

                if (!userStoppedManually) {
                    djiLog("Unexpected disconnection. Retrying in 5 seconds...")
                    setState(DjiDeviceState.RECONNECTING)

                    // Cleanup previous gatt to prevent leaks
                    bluetoothGatt?.close()
                    bluetoothGatt = null

                    mainHandler.postDelayed({
                        if (!userStoppedManually) {
                            deviceAddress?.let { connectToAddress(it) }
                        }
                    }, 5000)
                } else {
                    reset()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
            Log.d(TAG, "MTU negotiated, discovering services...")
            gatt.discoverServices()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite: descriptor=${descriptor.uuid}, status=$status, characteristic=${descriptor.characteristic.uuid}, state=$state")
            isWritingDescriptor = false

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Check if this was the FFF4 characteristic's notification descriptor
                if (descriptor.characteristic.uuid == FFF4_UUID && descriptorWriteQueue.isEmpty()) {
                    // Guard on state like Moblin does
                    if (state != DjiDeviceState.CONNECTING) {
                        Log.w(TAG, "FFF4 notification enabled but not in CONNECTING state, ignoring")
                        return
                    }
                    Log.d(TAG, "FFF4 notifications enabled in CONNECTING state")
                    setState(DjiDeviceState.CHECKING_IF_PAIRED)
                    sendPairingMessage()

                } else if (!descriptorWriteQueue.isEmpty()) {
                    writeNextDescriptor()
                }
            } else {
                Log.e(TAG, "⚠️ Descriptor write failed with status $status")
                if (!descriptorWriteQueue.isEmpty()) {
                    writeNextDescriptor()
                } else if (state == DjiDeviceState.CONNECTING) {
                    Log.w(TAG, "⚠️ All descriptor writes failed, attempting pairing anyway")
                    setState(DjiDeviceState.CHECKING_IF_PAIRED)
                    sendPairingMessage()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed!")
                return
            }
            val serviceList = gatt.services
            Log.d(TAG, "Found ${serviceList.size} services")
            for (service in serviceList) {
                val fff5 = service.getCharacteristic(FFF5_UUID)
                val fff4 = service.getCharacteristic(FFF4_UUID)

                if (fff5 != null || fff4 != null) {
                    if (fff5 != null) fff5Characteristic = fff5
                    if (fff4 != null) fff4Characteristic = fff4

                    val fff4Descriptor = fff4?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

                    for (c in service.characteristics) {
                        if (c.uuid == FFF5_UUID) continue
                        gatt.setCharacteristicNotification(c, true)

                        if (c.uuid != FFF4_UUID) {
                            val descriptor = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            if (descriptor != null) {
                                descriptorWriteQueue.add(descriptor)
                            }
                        }
                    }

                    if (fff4Descriptor != null) {
                        descriptorWriteQueue.add(fff4Descriptor)
                    }
                    break
                }
            }

            if (fff5Characteristic == null) {
                Log.e(TAG, "FFF5 characteristic not found!")
                return
            }

            if (descriptorWriteQueue.isEmpty()) {
                setState(DjiDeviceState.CHECKING_IF_PAIRED)
                sendPairingMessage()
            } else {
                writeNextDescriptor()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            processIncomingData(characteristic, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return
            processIncomingData(characteristic, value)
        }

        private fun processIncomingData(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (value.isEmpty()) return
            if (value[0] != 0x55.toByte()) return

            rxBuffer = value.copyOf()

            while (rxBuffer.size >= 2) {
                if (rxBuffer[0] != 0x55.toByte()) {
                    rxBuffer = rxBuffer.copyOfRange(1, rxBuffer.size)
                    continue
                }

                val messageLength = rxBuffer[1].toInt() and 0xFF
                if (messageLength < 11) {
                    rxBuffer = rxBuffer.copyOfRange(1, rxBuffer.size)
                    continue
                }

                if (rxBuffer.size < messageLength) break

                val completeMessage = rxBuffer.copyOfRange(0, messageLength)
                rxBuffer = rxBuffer.copyOfRange(messageLength, rxBuffer.size)

                try {
                    val message = DjiMessage.fromBytes(completeMessage)
                    handleDecodedMessage(message)
                } catch (e: Exception) {
                    Log.e(TAG, "RX: Corrupt message discarded: ${e.message}")
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicWrite: characteristic=${characteristic.uuid}, status=$status")
        }
    }

    private fun handleDecodedMessage(message: DjiMessage) {
        djiLog("RX decoded: state=$state id=0x${message.id.toString(16)} target=0x${message.target.toString(16)} type=0x${message.type.toString(16)} payload=${message.payload.joinToString("") { "%02X".format(it) }}")
        when (state) {
            DjiDeviceState.CHECKING_IF_PAIRED -> processCheckingIfPaired(message)
            DjiDeviceState.PAIRING -> processPairing()
            DjiDeviceState.CLEANING_UP -> processCleaningUp(message)
            DjiDeviceState.PREPARING_STREAM -> processPreparingStream(message)
            DjiDeviceState.SETTING_UP_WIFI -> processSettingUpWifi(message)
            DjiDeviceState.CONFIGURING -> processConfiguring(message)
            DjiDeviceState.STARTING_STREAM -> processStartingStream(message)
            DjiDeviceState.STREAMING -> processStreaming(message)
            DjiDeviceState.STOPPING_STREAM -> processStoppingStream(message)
            DjiDeviceState.RECONNECTING -> {
                djiLog("Message ignored in RECONNECTING state")
            }
            else -> {
                djiLog("Message in state $state ignored")
            }
        }
    }

    fun writeMessage(message: DjiMessage) {
        val bytes = message.encode()
        djiLog("TX: target=0x${message.target.toString(16)} id=0x${message.id.toString(16)} type=0x${message.type.toString(16)} ${bytes.size}B: ${bytes.joinToString("") { "%02X".format(it) }}")
        enqueueWrite(bytes)
    }

    private fun enqueueWrite(value: ByteArray) {
        var offset = 0
        while (offset < value.size) {
            val end = minOf(offset + writeChunkSize, value.size)
            val chunk = value.copyOfRange(offset, end)
            writeQueue.addLast(chunk)
            offset = end
        }
        startWriteLoopIfNeeded()
    }

    private fun startWriteLoopIfNeeded() {
        if (isWriting) return
        isWriting = true
        mainHandler.post { writeNextChunk() }
    }

    private fun writeNextChunk() {
        val chunk = writeQueue.removeFirstOrNull()
        if (chunk == null) {
            isWriting = false
            return
        }
        val char = fff5Characteristic ?: run {
            writeQueue.clear()
            isWriting = false
            return
        }

        val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                char,
                chunk,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) ?: BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION
        } else {
            char.value = chunk
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            if (bluetoothGatt?.writeCharacteristic(char) == true) {
                BluetoothStatusCodes.SUCCESS
            } else {
                BluetoothGatt.GATT_FAILURE
            }
        }
        mainHandler.postDelayed({ writeNextChunk() }, writeIntervalMs)
    }

    private fun sendStopStream() {
        val payload = DjiStopStreamingMessagePayload().encode()
        val message = DjiMessage(STOP_STREAMING_TARGET, STOP_STREAMING_TRANSACTION_ID, STOP_STREAMING_TYPE, payload)
        writeMessage(message)
    }

    private fun processCheckingIfPaired(response: DjiMessage) {
        if (response.id != PAIR_TRANSACTION_ID) return
        if (response.payload.contentEquals(byteArrayOf(0, 1))) {
            processPairing()
        } else {
            setState(DjiDeviceState.PAIRING)
        }
    }

    private fun processPairing() {
        sendStopStream()
        setState(DjiDeviceState.CLEANING_UP)
    }

    private fun processCleaningUp(response: DjiMessage) {
        if (response.id != STOP_STREAMING_TRANSACTION_ID) return
        val payload = DjiPreparingToLivestreamMessagePayload().encode()
        val message = DjiMessage(PREPARING_TO_LIVESTREAM_TARGET, PREPARING_TO_LIVESTREAM_TRANSACTION_ID, PREPARING_TO_LIVESTREAM_TYPE, payload)
        writeMessage(message)
        setState(DjiDeviceState.PREPARING_STREAM)
    }

    private fun processPreparingStream(response: DjiMessage) {
        if (response.id != PREPARING_TO_LIVESTREAM_TRANSACTION_ID) return
        val ssid = wifiSsid ?: return
        val pwd = wifiPassword ?: return
        val payload = DjiSetupWifiMessagePayload(ssid, pwd).encode()
        val message = DjiMessage(SETUP_WIFI_TARGET, SETUP_WIFI_TRANSACTION_ID, SETUP_WIFI_TYPE, payload)
        writeMessage(message)
        setState(DjiDeviceState.SETTING_UP_WIFI)
    }

    private fun processSettingUpWifi(response: DjiMessage) {
        if (response.id != SETUP_WIFI_TRANSACTION_ID) return
        if (!response.payload.contentEquals(byteArrayOf(0x00, 0x00))) {
            djiLog("WiFi setup FAILED")
            if (!userStoppedManually) {
                mainHandler.postDelayed({
                    if (!userStoppedManually) {
                        deviceAddress?.let { connectToAddress(it) }
                    }
                }, 5000)
                setState(DjiDeviceState.RECONNECTING)
            } else {
                reset()
            }
            setState(DjiDeviceState.WIFI_SETUP_FAILED)
            return
        }
        djiLog("WiFi setup OK")
        when (model) {
            SettingsDjiDeviceModel.OSMO_ACTION_2, SettingsDjiDeviceModel.OSMO_ACTION_3 -> {
                sendStartStreaming()
            }
            SettingsDjiDeviceModel.OSMO_ACTION_4 -> {
                val imageStab = imageStabilization ?: return
                val payload = DjiConfigureMessagePayload(imageStab, oa5 = false).encode()
                val message = DjiMessage(CONFIGURE_TARGET, CONFIGURE_TRANSACTION_ID, CONFIGURE_TYPE, payload)
                writeMessage(message)
                setState(DjiDeviceState.CONFIGURING)
            }
            SettingsDjiDeviceModel.OSMO_ACTION_5_PRO, SettingsDjiDeviceModel.OSMO_ACTION_6, SettingsDjiDeviceModel.OSMO_360 -> {
                val imageStab = imageStabilization ?: return
                val payload = DjiConfigureMessagePayload(imageStab, oa5 = true).encode()
                val message = DjiMessage(CONFIGURE_TARGET, CONFIGURE_TRANSACTION_ID, CONFIGURE_TYPE, payload)
                writeMessage(message)
                setState(DjiDeviceState.CONFIGURING)
            }
            SettingsDjiDeviceModel.OSMO_POCKET_3, SettingsDjiDeviceModel.OSMO_POCKET_4, SettingsDjiDeviceModel.UNKNOWN -> {
                sendStartStreaming()
            }
        }
    }

    private fun processConfiguring(response: DjiMessage) {
        if (response.id != CONFIGURE_TRANSACTION_ID) return
        sendStartStreaming()
    }

    private fun sendStartStreaming() {
        val rtmp = rtmpUrl ?: return
        val res = resolution ?: return
        val oa5 = model.hasNewProtocol()
        val payload = DjiStartStreamingMessagePayload(rtmp, res, fps, bitrateKbps, model).encode()
        val message = DjiMessage(START_STREAMING_TARGET, START_STREAMING_TRANSACTION_ID, START_STREAMING_TYPE, payload)
        writeMessage(message)

        if (oa5) {
            val confirmPayload = DjiConfirmStartStreamingMessagePayload().encode()
            val confirmMsg = DjiMessage(STOP_STREAMING_TARGET, STOP_STREAMING_TRANSACTION_ID, STOP_STREAMING_TYPE, confirmPayload)
            writeMessage(confirmMsg)
        }

        setState(DjiDeviceState.STARTING_STREAM)
    }

    private fun processStartingStream(response: DjiMessage) {
        if (response.id != START_STREAMING_TRANSACTION_ID) return
        setState(DjiDeviceState.STREAMING)
        stopStartStreamingTimer()
    }

    private fun processStreaming(message: DjiMessage) {
        if (message.type == 0x020D00 && message.payload.size >= 21) {
            val newBattery = message.payload[20].toInt()
            if (newBattery != batteryPercentage) {
                batteryPercentage = newBattery
                delegate?.djiDeviceBatteryPercentage(this, newBattery)
            }
        }
    }

    private fun processStoppingStream(response: DjiMessage) {
        if (response.id == STOP_STREAMING_TRANSACTION_ID) {
            reset()
        }
    }
}
