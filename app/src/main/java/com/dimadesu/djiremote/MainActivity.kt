package com.dimadesu.djiremote

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.settings.AppSettings
import com.dimadesu.djiremote.ui.dji.DjiDeviceSettingsScreen
import com.dimadesu.djiremote.ui.dji.DjiDevicesSettingsScreen
import com.dimadesu.djiremote.ui.dji.DjiBleScannerScreen
import com.dimadesu.djiremote.ui.theme.DJIRemoteTheme
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {
    private val exitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.dimadesu.djiremote.ACTION_EXIT_APP") {
                finishAndRemoveTask()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startNotificationServiceIfEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository with context
        com.dimadesu.djiremote.dji.DjiRepository.initialize(this)
        AppSettings.initialize(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNotificationServiceIfEnabled()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(exitReceiver, IntentFilter("com.dimadesu.djiremote.ACTION_EXIT_APP"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(exitReceiver, IntentFilter("com.dimadesu.djiremote.ACTION_EXIT_APP"))
        }

        enableEdgeToEdge()
        setContent {
            val isDarkMode by AppSettings.isDarkMode.collectAsState()
            DJIRemoteTheme(darkTheme = isDarkMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var screen by remember { mutableStateOf("devices") }
                    var selectedDevice by remember { mutableStateOf<SettingsDjiDevice?>(null) }

                    BackHandler(enabled = screen != "devices") {
                        screen = when (screen) {
                            "device" -> "devices"
                            "device-scanner" -> "device"
                            else -> "devices"
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (screen) {
                            "devices" -> {
                                DjiDevicesSettingsScreen(onOpenDevice = {
                                    selectedDevice = it
                                    screen = "device"
                                })
                            }
                            "device" -> {
                                selectedDevice?.let { d ->
                                    DjiDeviceSettingsScreen(
                                        device = d,
                                        onBack = { screen = "devices" },
                                        onOpenScanner = {
                                            screen = "device-scanner"
                                        }
                                    )
                                }
                            }
                            "device-scanner" -> {
                                DjiBleScannerScreen(
                                    onSelect = { address, name, model ->
                                        selectedDevice?.let { d ->
                                            val latest = com.dimadesu.djiremote.dji.DjiRepository.devices.value
                                                .firstOrNull { it.id == d.id } ?: d
                                            com.dimadesu.djiremote.dji.DjiRepository.updateDevice(latest.copy(
                                                bluetoothPeripheralAddress = address,
                                                bluetoothPeripheralName = name,
                                                model = model
                                            ))
                                        }
                                        screen = "device"
                                    },
                                    onBack = { screen = "device" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startNotificationServiceIfEnabled() {
        if (AppSettings.showStatusNotification.value) {
            val intent = Intent(this, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(exitReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
}