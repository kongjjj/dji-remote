package com.dimadesu.djiremote.ui.dji

import android.app.Activity
import java.util.UUID
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dimadesu.djiremote.R
import com.dimadesu.djiremote.dji.DjiBackupManager
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.dji.SettingsDjiDeviceState
import com.dimadesu.djiremote.dji.DjiModel
import com.dimadesu.djiremote.settings.AppSettings
import com.dimadesu.djiremote.utils.HotspotManager

@Composable
fun DjiDevicesSettingsScreen(onOpenDevice: (SettingsDjiDevice) -> Unit) {
    val context = LocalContext.current
    val devices by DjiRepository.devices.collectAsState()
    val lastUsedDeviceId by DjiRepository.lastUsedDeviceId.collectAsState()
    val isDarkMode by AppSettings.isDarkMode.collectAsState()
    val showStatusNotification by AppSettings.showStatusNotification.collectAsState()
    val isHotspotEnabled by HotspotManager.isHotspotEnabled
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<SettingsDjiDevice?>(null) }
    var showLanguageOptions by remember { mutableStateOf(false) }
    var showBackupOptions by remember { mutableStateOf(false) }

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

    DisposableEffect(Unit) {
        HotspotManager.registerReceiver(context)
        onDispose {
            HotspotManager.unregisterReceiver(context)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
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

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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

    if (showDeleteConfirmation && (deviceToDelete != null)) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_device)) },
            text = { Text(stringResource(R.string.delete_device_confirm, deviceToDelete!!.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deviceToDelete?.let { DjiRepository.removeDevice(it.id) }
                        showDeleteConfirmation = false
                        deviceToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBackupOptions) {
        AlertDialog(
            onDismissRequest = { showBackupOptions = false },
            title = { Text(stringResource(R.string.backup_restore)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            createDocumentLauncher.launch("dji_devices_backup.bak")
                            showBackupOptions = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_devices))
                    }
                    Button(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/octet-stream", "application/x-trash", "*/*"))
                            showBackupOptions = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_devices))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupOptions = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLanguageOptions) {
        AlertDialog(
            onDismissRequest = { showLanguageOptions = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                val languages = listOf(
                    "en" to "English",
                    "zh-HK" to "繁體中文(香港)",
                    "zh-TW" to "繁體中文(台灣)",
                    "zh-CN" to "簡體中文",
                    "ja" to "日本語",
                    "ko" to "한국어",
                    "it" to "Italiano",
                    "de" to "Deutsch",
                    "fr" to "Français",
                    "es" to "Español",
                    "pt" to "Português",
                    "ru" to "Русский",
                    "sa" to "संस्कृतम्",
                    "th" to "ไทย",
                    "vi" to "Tiếng Việt"
                )
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                val effectiveLocale = if (!currentLocales.isEmpty) currentLocales[0] else context.resources.configuration.locales[0]
                val currentLangTag = effectiveLocale?.toLanguageTag() ?: "en"

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                    items(languages) { (code, name) ->
                        val isSelected = when (code) {
                            "en" -> currentLangTag.startsWith("en", ignoreCase = true)
                            "zh-HK" -> currentLangTag.contains("HK", ignoreCase = true)
                            "zh-TW" -> currentLangTag.contains("TW", ignoreCase = true) && !currentLangTag.contains("HK", ignoreCase = true)
                            "zh-CN" -> (currentLangTag.contains("CN", ignoreCase = true) || currentLangTag.contains("Hans", ignoreCase = true)) && !currentLangTag.contains("TW", ignoreCase = true) && !currentLangTag.contains("HK", ignoreCase = true) && !currentLangTag.contains("Hant", ignoreCase = true)
                            else -> currentLangTag.startsWith(code, ignoreCase = true)
                        }
                        
                        TextButton(
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
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageOptions = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Left side (55%) - Scrollable device list
            LazyColumn(
                modifier = Modifier.weight(0.55f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (devices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.no_devices),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.add_device_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(devices) { device ->
                        DeviceCard(device, onOpenDevice, onDelete = {
                            deviceToDelete = it
                            showDeleteConfirmation = true
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right side (45%) - Fixed controls and action buttons
            Column(
                modifier = Modifier.weight(0.45f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Consolidated Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1.2f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.dji_devices),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        if (!android.provider.Settings.canDrawOverlays(context)) {
                                            val intent = Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                "package:${context.packageName}".toUri()
                                            )
                                            context.startActivity(intent)
                                        } else {
                                            val intent = Intent(context, com.dimadesu.djiremote.FloatingBubbleService::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                            (context as? Activity)?.moveTaskToBack(true)
                                        }
                                    } else {
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHotspotEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Settings and Action buttons
                ActionButtons(
                    showStatusNotification = showStatusNotification,
                    onToggleNotification = { AppSettings.toggleStatusNotification(context) },
                    onShowLanguage = { showLanguageOptions = true },
                    onShowBackup = { showBackupOptions = true },
                    devices = devices,
                    lastUsedDeviceId = lastUsedDeviceId,
                    context = context
                )
            }
        }
    } else {
        // Portrait mode
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
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    if (!android.provider.Settings.canDrawOverlays(context)) {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            "package:${context.packageName}".toUri()
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(context, com.dimadesu.djiremote.FloatingBubbleService::class.java)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(intent)
                                        } else {
                                            context.startService(intent)
                                        }
                                        (context as? Activity)?.moveTaskToBack(true)
                                    }
                                } else {
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHotspotEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.add_device_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        DeviceCard(device, onOpenDevice, onDelete = {
                            deviceToDelete = it
                            showDeleteConfirmation = true
                        })
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionButtons(
                    showStatusNotification = showStatusNotification,
                    onToggleNotification = { AppSettings.toggleStatusNotification(context) },
                    onShowLanguage = { showLanguageOptions = true },
                    onShowBackup = { showBackupOptions = true },
                    devices = devices,
                    lastUsedDeviceId = lastUsedDeviceId,
                    context = context
                )
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: SettingsDjiDevice,
    onOpenDevice: (SettingsDjiDevice) -> Unit,
    onDelete: (SettingsDjiDevice) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenDevice(device) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (device.batteryPercentage != null) {
                        Icon(
                            imageVector = Icons.Default.BatteryStd,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${device.batteryPercentage}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                        style = MaterialTheme.typography.bodySmall,
                        color = when (device.state) {
                            SettingsDjiDeviceState.STREAMING -> Color(0xFF4CAF50)
                            SettingsDjiDeviceState.WIFI_SETUP_FAILED -> MaterialTheme.colorScheme.error
                            SettingsDjiDeviceState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    IconButton(
                        onClick = { onDelete(device) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val deviceDisplay = device.bluetoothPeripheralName ?: stringResource(R.string.no_device)
                    Text(
                        text = deviceDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                val bitrateMbps = "${device.bitrate / 1_000_000}M"
                val stabShort = when (device.imageStabilization) {
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.OFF -> "Off"
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.ROCK_STEADY -> "RS"
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.ROCK_STEADY_PLUS -> "RS+"
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.HORIZON_BALANCING -> "HB"
                    com.dimadesu.djiremote.dji.SettingsDjiDeviceImageStabilization.HORIZON_STEADY -> "HS"
                }
                
                Text(
                    text = "${device.resolution}, $bitrateMbps, $stabShort",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ActionButtons(
    showStatusNotification: Boolean,
    onToggleNotification: () -> Unit,
    onShowLanguage: () -> Unit,
    onShowBackup: () -> Unit,
    devices: List<SettingsDjiDevice>,
    lastUsedDeviceId: UUID?,
    context: android.content.Context
) {
    Button(
        onClick = onToggleNotification,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(
            if (showStatusNotification) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (showStatusNotification) stringResource(R.string.disable_status_notification) else stringResource(R.string.enable_status_notification),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Button(
        onClick = onShowLanguage,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.language))
    }

    Button(
        onClick = onShowBackup,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.backup))
    }

    val lastDeviceToUse = devices.find { it.id == lastUsedDeviceId } ?: devices.firstOrNull()
    if (lastDeviceToUse != null) {
        Button(
            onClick = {
                if (lastDeviceToUse.isStarted) {
                    DjiModel.stopStreaming(lastDeviceToUse)
                } else {
                    DjiModel.startStreaming(context, lastDeviceToUse)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (lastDeviceToUse.isStarted)
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
        ) {
            Icon(if (lastDeviceToUse.isStarted) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (lastDeviceToUse.isStarted) stringResource(R.string.stop_stream) else stringResource(R.string.continue_last_stream))
        }
    }
}
