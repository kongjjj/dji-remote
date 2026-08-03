package com.dimadesu.djiremote

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.dimadesu.djiremote.dji.DjiModel
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDeviceState
import com.dimadesu.djiremote.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect

class NotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var phoneTemperature: Float = 0f

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                phoneTemperature = temp / 10f
                updateNotification()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "device_status_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_STREAM = "com.dimadesu.djiremote.ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "com.dimadesu.djiremote.ACTION_STOP_STREAM"
        const val ACTION_EXIT_APP = "com.dimadesu.djiremote.ACTION_EXIT_APP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        serviceScope.launch {
            combine(DjiRepository.devices, DjiRepository.lastUsedDeviceId, AppSettings.isDarkMode) { devices, lastId, isDark ->
                val device = devices.find { it.id == lastId } ?: devices.firstOrNull()
                Triple(device, isDark, lastId)
            }.collect { (device, _, _) ->
                updateNotification(device)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STREAM -> {
                val lastId = DjiRepository.lastUsedDeviceId.value
                val device = DjiRepository.devices.value.find { it.id == lastId }
                device?.let { DjiModel.startStreaming(this, it) }
            }
            ACTION_STOP_STREAM -> {
                val lastId = DjiRepository.lastUsedDeviceId.value
                val device = DjiRepository.devices.value.find { it.id == lastId }
                device?.let { DjiModel.stopStreaming(it) }
            }
            ACTION_EXIT_APP -> {
                // 1. Stop all streaming
                DjiModel.stopAllStreaming()

                // 2. Stop floating bubble service
                stopService(Intent(this, FloatingBubbleService::class.java))

                // 3. Close activities
                val exitIntent = Intent("com.dimadesu.djiremote.ACTION_EXIT_APP")
                sendBroadcast(exitIntent)

                // 4. Stop itself
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()

                // 5. Full exit after delay to allow broadcast to be processed and state saved
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }, 500)

                return START_NOT_STICKY
            }
        }

        // Initial notification to start foreground
        val device = DjiRepository.devices.value.find { it.id == DjiRepository.lastUsedDeviceId.value }
            ?: DjiRepository.devices.value.firstOrNull()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(device), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(device))
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(device: com.dimadesu.djiremote.dji.SettingsDjiDevice? = null) {
        val currentDevice = device ?: DjiRepository.devices.value.find { it.id == DjiRepository.lastUsedDeviceId.value }
        ?: DjiRepository.devices.value.firstOrNull()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(currentDevice))
    }

    private fun buildNotification(device: com.dimadesu.djiremote.dji.SettingsDjiDevice?): Notification {
        val deviceName = device?.name ?: getString(R.string.no_device)
        val batteryInfo = device?.batteryPercentage?.let { getString(R.string.dji_battery, it) } ?: ""
        val tempInfo = getString(R.string.phone_temp, phoneTemperature)

        val statusText = when (device?.state) {
            SettingsDjiDeviceState.IDLE -> getString(R.string.state_idle)
            SettingsDjiDeviceState.STREAMING -> getString(R.string.state_streaming)
            SettingsDjiDeviceState.CONNECTING -> getString(R.string.state_connecting)
            SettingsDjiDeviceState.RECONNECTING -> getString(R.string.state_reconnecting)
            else -> device?.state?.name ?: getString(R.string.state_unknown)
        }

        val contentText = "$statusText | $batteryInfo | $tempInfo"

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(deviceName)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // Set accent color based on dark mode
        val isDarkMode = AppSettings.isDarkMode.value
        val accentColor = if (isDarkMode) 0xFFD0BCFF.toInt() else 0xFF6650a4.toInt()
        builder.setColor(accentColor)
        builder.setColorized(true)

        // Add actions
        if (device != null) {
            if (device.isStarted && device.state == SettingsDjiDeviceState.STREAMING) {
                val stopIntent = Intent(this, NotificationService::class.java).setAction(ACTION_STOP_STREAM)
                val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(0, getString(R.string.stop_stream), stopPendingIntent)
            } else if (!device.isStarted) {
                val startIntent = Intent(this, NotificationService::class.java).setAction(ACTION_START_STREAM)
                val startPendingIntent = PendingIntent.getService(this, 2, startIntent, PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(0, getString(R.string.start_stream), startPendingIntent)
            }
        }

        // Add Exit App button
        val exitIntent = Intent(this, NotificationService::class.java).setAction(ACTION_EXIT_APP)
        val exitPendingIntent = PendingIntent.getService(this, 3, exitIntent, PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(0, getString(R.string.exit_app), exitPendingIntent)

        return builder.build()
    }
}
