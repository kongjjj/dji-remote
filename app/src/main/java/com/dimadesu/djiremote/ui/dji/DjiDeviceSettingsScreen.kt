package com.dimadesu.djiremote.ui.dji

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dimadesu.djiremote.R
import com.dimadesu.djiremote.dji.DjiFileLogger
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.dji.SettingsDjiDeviceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjiDeviceSettingsScreen(
    device: SettingsDjiDevice,
    onBack: () -> Unit = {},
    onOpenScanner: () -> Unit = {}
) {
    // Observe the live device from the repository so UI recomposes on state/isStarted changes
    val allDevices by DjiRepository.devices.collectAsState()
    val liveDevice = allDevices.firstOrNull { it.id == device.id } ?: device

    var name by remember { mutableStateOf(liveDevice.name) }
    var ssid by remember { mutableStateOf(liveDevice.wifiSsid) }
    var password by remember { mutableStateOf(liveDevice.wifiPassword) }
    var rtmpUrl by remember { mutableStateOf(liveDevice.rtmpUrl) }
    var resolution by remember { mutableStateOf(liveDevice.resolution) }
    var bitrate by remember { mutableStateOf(liveDevice.bitrate) }
    var imageStabilization by remember { mutableStateOf(liveDevice.imageStabilization) }

    var expandedResolution by remember { mutableStateOf(false) }
    var expandedBitrate by remember { mutableStateOf(false) }
    var expandedImageStab by remember { mutableStateOf(false) }
    var expandedFps by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val resolutionOptions = listOf("1080p", "720p", "480p")
    val bitrateOptions = listOf(
        20_000_000,
        16_000_000,
        12_000_000,
        10_000_000,
        8_000_000,
        6_000_000,
        5_000_000,
        4_000_000,
        3_000_000,
        2_000_000,
        1_000_000
    )

    fun imageStabToString(value: com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization): String {
        return when (value) {
            com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.OFF -> "Off"
            com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.ROCK_STEADY -> "RockSteady"
            com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.ROCK_STEADY_PLUS -> "RockSteady+"
            com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.HORIZON_BALANCING -> "HorizonBalancing"
            com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.HORIZON_STEADY -> "HorizonSteady"
        }
    }

    fun stringToImageStab(value: String): com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization {
        return when (value) {
            "RockSteady" -> com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.ROCK_STEADY
            "RockSteady+" -> com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.ROCK_STEADY_PLUS
            "HorizonBalancing" -> com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.HORIZON_BALANCING
            "HorizonSteady" -> com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.HORIZON_STEADY
            else -> com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.OFF
        }
    }

    val imageStabOptions = listOf("Off", "RockSteady", "RockSteady+", "HorizonBalancing", "HorizonSteady")

    // Save config to repository whenever user leaves the screen.
    DisposableEffect(Unit) {
        onDispose {
            val latest = DjiRepository.devices.value.firstOrNull { it.id == device.id } ?: return@onDispose
            DjiRepository.updateDevice(latest.copy(
                name = name,
                wifiSsid = ssid,
                wifiPassword = password,
                rtmpUrl = rtmpUrl,
                resolution = resolution,
                bitrate = bitrate,
                imageStabilization = imageStabilization
            ))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Name section
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.name)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        )

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Device section
        Text(stringResource(R.string.device), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenScanner,
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        ) {
            Text(liveDevice.bluetoothPeripheralName ?: stringResource(R.string.select_device))
        }
        if (liveDevice.bluetoothPeripheralAddress != null) {
            Text(
                text = liveDevice.bluetoothPeripheralAddress!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // WiFi section
        Text(stringResource(R.string.wifi), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text(stringResource(R.string.ssid)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wifi_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // RTMP section
        Text(stringResource(R.string.rtmp), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = rtmpUrl,
            onValueChange = { rtmpUrl = it },
            label = { Text(stringResource(R.string.url)) },
            placeholder = { Text("rtmp://server/live/stream") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        )

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Settings section
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // Resolution dropdown
        ExposedDropdownMenuBox(
            expanded = expandedResolution,
            onExpandedChange = { expandedResolution = !expandedResolution }
        ) {
            TextField(
                value = resolution,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.resolution)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedResolution) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                enabled = !liveDevice.isStarted
            )
            ExposedDropdownMenu(
                expanded = expandedResolution,
                onDismissRequest = { expandedResolution = false }
            ) {
                resolutionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            resolution = option
                            expandedResolution = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bitrate dropdown
        ExposedDropdownMenuBox(
            expanded = expandedBitrate,
            onExpandedChange = { expandedBitrate = !expandedBitrate }
        ) {
            TextField(
                value = "${bitrate / 1_000_000} Mbps",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.bitrate)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBitrate) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                enabled = !liveDevice.isStarted
            )
            ExposedDropdownMenu(
                expanded = expandedBitrate,
                onDismissRequest = { expandedBitrate = false }
            ) {
                bitrateOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option / 1_000_000} Mbps") },
                        onClick = {
                            bitrate = option
                            expandedBitrate = false
                        }
                    )
                }
            }
        }

        // Image Stabilization dropdown (Always enabled in UI based on your reference)
        if (liveDevice.model.hasImageStabilization()) {
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandedImageStab,
                onExpandedChange = { expandedImageStab = !expandedImageStab }
            ) {
                TextField(
                    value = imageStabToString(imageStabilization),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.image_stabilization)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedImageStab) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    enabled = !liveDevice.isStarted
                )
                ExposedDropdownMenu(
                    expanded = expandedImageStab,
                    onDismissRequest = { expandedImageStab = false }
                ) {
                    imageStabOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                imageStabilization = stringToImageStab(option)
                                expandedImageStab = false
                            }
                        )
                    }
                }
            }
        }

        // FPS picker (Osmo Pocket 3 & 4)
        if (liveDevice.model == com.dimadesu.djiremote.dji.SettingsDjiDeviceModel.OSMO_POCKET_3 ||
            liveDevice.model == com.dimadesu.djiremote.dji.SettingsDjiDeviceModel.OSMO_POCKET_4) {
            val fpsOptions = listOf(25, 30)
            var fps by remember { mutableStateOf(liveDevice.fps) }
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandedFps,
                onExpandedChange = { expandedFps = !expandedFps }
            ) {
                TextField(
                    value = "$fps",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.fps)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFps) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    enabled = !liveDevice.isStarted
                )
                ExposedDropdownMenu(
                    expanded = expandedFps,
                    onDismissRequest = { expandedFps = false }
                ) {
                    fpsOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("$option") },
                            onClick = {
                                fps = option
                                expandedFps = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.bitrate_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Status section
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = when (liveDevice.state) {
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.IDLE -> stringResource(R.string.state_idle)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.DISCOVERING -> stringResource(R.string.state_discovering)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.CONNECTING -> stringResource(R.string.state_connecting)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.PAIRING -> stringResource(R.string.state_pairing)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.STOPPING_STREAM -> stringResource(R.string.state_stopping_stream)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.PREPARING_STREAM -> stringResource(R.string.state_preparing_stream)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.SETTING_UP_WIFI -> stringResource(R.string.state_setting_up_wifi)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.WIFI_SETUP_FAILED -> stringResource(R.string.state_wifi_setup_failed)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.CONFIGURING -> stringResource(R.string.state_configuring)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.STARTING_STREAM -> stringResource(R.string.state_starting_stream)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.STREAMING -> stringResource(R.string.state_streaming)
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceState.RECONNECTING -> stringResource(R.string.state_reconnecting)
                    else -> stringResource(R.string.state_unknown)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Start/Stop button section
        val buttonText = when {
            !liveDevice.isStarted -> stringResource(R.string.start_stream)
            liveDevice.state == SettingsDjiDeviceState.RECONNECTING -> stringResource(R.string.reconnecting)
            liveDevice.state == SettingsDjiDeviceState.STREAMING -> stringResource(R.string.stop_stream)
            else -> stringResource(R.string.preparing)
        }

        Button(
            onClick = {
                if (liveDevice.isStarted) {
                    com.dimadesu.djiremote.dji.DjiModel.stopStreaming(liveDevice)
                } else {
                    // Save UI state to liveDevice object before passing to startStreaming
                    liveDevice.name = name
                    liveDevice.wifiSsid = ssid
                    liveDevice.wifiPassword = password
                    liveDevice.rtmpUrl = rtmpUrl
                    liveDevice.resolution = resolution
                    liveDevice.bitrate = bitrate
                    liveDevice.imageStabilization = imageStabilization
                    DjiRepository.updateDevice(liveDevice)

                    com.dimadesu.djiremote.dji.DjiModel.startStreaming(context, liveDevice)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (liveDevice.isStarted)
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else ButtonDefaults.buttonColors()
        ) {
            Text(buttonText)
        }

        // Share debug logs button
        if (!com.dimadesu.djiremote.dji.DEBUG_LOGGING_ENABLED) return@Column

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                val file = DjiFileLogger.getFile()
                if (file != null) {
                    val logText = file.readText()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, logText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share debug logs"))
                } else {
                    android.widget.Toast.makeText(context, context.getString(R.string.no_logs_toast), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.share_debug_logs))
        }
    }
}
