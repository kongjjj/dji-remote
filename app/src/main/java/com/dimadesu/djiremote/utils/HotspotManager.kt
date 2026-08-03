package com.dimadesu.djiremote.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.lang.reflect.Method

object HotspotManager {
    private val _isHotspotEnabled = mutableStateOf(false)
    val isHotspotEnabled: State<Boolean> = _isHotspotEnabled

    // AP state constants (hidden in SDK)
    private const val WIFI_AP_STATE_DISABLING = 10
    private const val WIFI_AP_STATE_DISABLED = 11
    private const val WIFI_AP_STATE_ENABLING = 12
    private const val WIFI_AP_STATE_ENABLED = 13
    private const val WIFI_AP_STATE_FAILED = 14

    private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_WIFI_AP_STATE = "wifi_state"

    private var receiver: BroadcastReceiver? = null

    fun updateState(context: Context) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            val method: Method = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
            val state = method.invoke(wifiManager) as Int
            _isHotspotEnabled.value = (state == WIFI_AP_STATE_ENABLED || state == WIFI_AP_STATE_ENABLING)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: assume disabled if we can't detect
            _isHotspotEnabled.value = false
        }
    }

    fun registerReceiver(context: Context) {
        val appContext = context.applicationContext
        updateState(appContext)

        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_WIFI_AP_STATE_CHANGED) {
                    val state = intent.getIntExtra(EXTRA_WIFI_AP_STATE, WIFI_AP_STATE_FAILED)
                    _isHotspotEnabled.value = (state == WIFI_AP_STATE_ENABLED || state == WIFI_AP_STATE_ENABLING)
                }
            }
        }

        val filter = IntentFilter(ACTION_WIFI_AP_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    fun unregisterReceiver(context: Context) {
        val appContext = context.applicationContext
        receiver?.let {
            try {
                appContext.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
            receiver = null
        }
    }

    fun openHotspotSettings(context: Context) {
        val intent = Intent().apply {
            action = Intent.ACTION_MAIN
            setClassName("com.android.settings", "com.android.settings.TetherSettings")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general wireless settings if tether settings fails
            val fallbackIntent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }
}
