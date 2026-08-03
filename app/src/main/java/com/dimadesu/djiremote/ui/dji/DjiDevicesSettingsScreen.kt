package com.dimadesu.djiremote.ui.dji

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dimadesu.djiremote.R
import com.dimadesu.djiremote.dji.*
import com.dimadesu.djiremote.settings.AppSettings
import com.dimadesu.djiremote.utils.HotspotManager
import android.os.Build
import android.content.Intent
import android.app.Activity

@Composable
fun DjiDevicesSettingsScreen(onOpenDevice: (SettingsDjiDevice) -> Unit) {
    val devices by DjiRepository.devices.collectAsState()
    val lastUsedId by DjiRepository.lastUsedDeviceId.collectAsState()
    val isDarkMode by AppSettings.isDarkMode.collectAsState()
    val showStatusNotification by AppSettings.showStatusNotification.collectAsState()

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showBackupOptions by remember { mutableStateOf(false) }
    var showLanguageOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isHotspotEnabled by HotspotManager.isHotspotEnabled

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                HotspotManager.updateState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            val success = DjiBackupManager.exportSettings(context, it)
            if (success) {
                android.widget.Toast.makeText(context, context.getString(R.string.export_success), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, context.getString(R.string.export_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val count = DjiBackupManager.importSettings(context, it)
            when {
                count > 0 -> android.widget.Toast.makeText(context, context.getString(R.string.imported_n_settings, count), android.widget.Toast.LENGTH_SHORT).show()
                count == 0 -> android.widget.Toast.makeText(context, context.getString(R.string.no_new_settings), android.widget.Toast.LENGTH_SHORT).show()
                else -> android.widget.Toast.makeText(context, context.getString(R.string.import_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        HotspotManager.registerReceiver(context)
        onDispose {
            HotspotManager.unregisterReceiver(context)
        }
    }

    if (showDeleteConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.confirm_remove_last_setup)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        devices.lastOrNull()?.let { DjiRepository.removeDevice(it.id) }
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBackupOptions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBackupOptions = false },
            title = { Text(stringResource(R.string.export_import_settings)) },
            text = { Text(stringResource(R.string.select_operation)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBackupOptions = false
                        exportLauncher.launch("dji_backup.bak")
                    }
                ) {
                    Text(stringResource(R.string.export))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBackupOptions = false
                        importLauncher.launch("*/*")
                    }
                ) {
                    Text(stringResource(R.string.import_str))
                }
            }
        )
    }

    if (showLanguageOptions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLanguageOptions = false },
            title = { Text(stringResource(R.string.set_language)) },
            text = {
                Column {
                    val languages = listOf(
                        "en" to stringResource(R.string.english),
                        "zh-TW" to stringResource(R.string.traditional_chinese),
                        "zh-CN" to stringResource(R.string.simplified_chinese),
                        "ja" to stringResource(R.string.japanese),
                        "ko" to stringResource(R.string.korean),
                        "it" to stringResource(R.string.italian),
                        "de" to stringResource(R.string.german),
                        "fr" to stringResource(R.string.french),
                        "es" to stringResource(R.string.spanish),
                        "pt" to stringResource(R.string.portuguese),
                        "ru" to stringResource(R.string.russian),
                        "sa" to stringResource(R.string.sanskrit),
                        "th" to stringResource(R.string.thai),
                        "vi" to stringResource(R.string.vietnamese)
                    )
                    val currentLocales = AppCompatDelegate.getApplicationLocales()
                    val currentLangCode = if (currentLocales.isEmpty) "en" else currentLocales.get(0)?.toLanguageTag() ?: "en"

                    languages.forEach { (code, name) ->
                        val isSelected = currentLangCode == code || (currentLangCode.startsWith("en") && code == "en")

                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (!isSelected) {
                                    AppSettings.setLanguage(context, code)
                                }
                                showLanguageOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(name)
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showLanguageOptions = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1.2f),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.dji_devices),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (!android.provider.Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(context, com.dimadesu.djiremote.FloatingBubbleService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    // Move app to back
                                    (context as? Activity)?.moveTaskToBack(true)
                                }
                            } else {
                                // Older versions might not need runtime overlay permission or handled differently
                                val intent = Intent(context, com.dimadesu.djiremote.FloatingBubbleService::class.java)
                                context.startService(intent)
                                (context as? Activity)?.moveTaskToBack(true)
                            }
                        }
                )
                IconButton(
                    onClick = { AppSettings.toggleDarkMode(context) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                        contentDescription = "Toggle Theme",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.weight(2f),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { HotspotManager.openHotspotSettings(context) },
                    modifier = Modifier.padding(end = 4.dp).weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isHotspotEnabled) Color(0xFF4CAF50) else androidx.compose.material3.MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isHotspotEnabled) stringResource(R.string.close_hotspot) else stringResource(R.string.open_hotspot),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                Button(
                    onClick = {
                        val d = SettingsDjiDevice(name = context.getString(R.string.device) + " ${devices.size + 1}")
                        DjiRepository.addDevice(d)
                    },
                    modifier = Modifier.wrapContentWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.create), maxLines = 1)
                }
            }
        }

        if (devices.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.no_devices),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.add_device_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices) { device ->
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onOpenDevice(device) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = device.name,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                                )
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    if (device.batteryPercentage != null) {
                                        Text(
                                            text = "🔋 ${device.batteryPercentage}%",
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = when (device.state) {
                                            SettingsDjiDeviceState.IDLE -> stringResource(R.string.state_idle)
                                            SettingsDjiDeviceState.DISCOVERING -> stringResource(R.string.state_discovering)
                                            SettingsDjiDeviceState.CONNECTING -> stringResource(R.string.state_connecting)
                                            SettingsDjiDeviceState.PAIRING -> stringResource(R.string.state_pairing)
                                            SettingsDjiDeviceState.STOPPING_STREAM -> stringResource(R.string.state_stopping_stream)
                                            SettingsDjiDeviceState.PREPARING_STREAM -> stringResource(R.string.state_preparing_stream)
                                            SettingsDjiDeviceState.SETTING_UP_WIFI -> stringResource(R.string.state_setting_up_wifi)
                                            SettingsDjiDeviceState.WIFI_SETUP_FAILED -> stringResource(R.string.state_wifi_setup_failed)
                                            SettingsDjiDeviceState.CONFIGURING -> stringResource(R.string.state_configuring)
                                            SettingsDjiDeviceState.STARTING_STREAM -> stringResource(R.string.state_starting_stream)
                                            SettingsDjiDeviceState.STREAMING -> stringResource(R.string.state_streaming)
                                            SettingsDjiDeviceState.RECONNECTING -> stringResource(R.string.state_reconnecting)
                                            else -> stringResource(R.string.state_unknown)
                                        },
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = when (device.state) {
                                            SettingsDjiDeviceState.STREAMING ->
                                                androidx.compose.material3.MaterialTheme.colorScheme.primary
                                            SettingsDjiDeviceState.WIFI_SETUP_FAILED ->
                                                androidx.compose.material3.MaterialTheme.colorScheme.error
                                            SettingsDjiDeviceState.RECONNECTING ->  // 新增
                                                androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                                            else ->
                                                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.Bottom
                            ) {
                                // 左側：藍牙名稱
                                Column(modifier = Modifier.weight(1f)) {
                                    if (device.bluetoothPeripheralName != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = device.bluetoothPeripheralName!!,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 右側：設定摘要 (解析度, 位元率, 穩定)
                                val stabShort = when (device.imageStabilization) {
                                    SettingsDjiDeviceImageStabilization.OFF -> "Off"
                                    SettingsDjiDeviceImageStabilization.ROCK_STEADY -> "RS"
                                    SettingsDjiDeviceImageStabilization.ROCK_STEADY_PLUS -> "RS+"
                                    SettingsDjiDeviceImageStabilization.HORIZON_BALANCING -> "HB"
                                    SettingsDjiDeviceImageStabilization.HORIZON_STEADY -> "HS"
                                }
                                val bitrateMbps = "${device.bitrate / 1_000_000}M"

                                Text(
                                    text = "${device.resolution}, $bitrateMbps, $stabShort",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    showDeleteConfirmation = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.remove_last_setup))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                showLanguageOptions = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.set_language))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { AppSettings.toggleStatusNotification(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (showStatusNotification) stringResource(R.string.disable_status_notification)
                else stringResource(R.string.enable_status_notification)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                showBackupOptions = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.export_import_settings))
        }

        val lastUsedDevice = devices.firstOrNull { it.id == lastUsedId }
        if (lastUsedDevice != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (lastUsedDevice.isStarted) {
                        DjiModel.stopStreaming(lastUsedDevice)
                    } else {
                        DjiModel.startStreaming(context, lastUsedDevice)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (lastUsedDevice.isStarted) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (lastUsedDevice.isStarted) stringResource(R.string.stop_stream) else stringResource(R.string.continue_last_stream))
            }
        }
    }
}
