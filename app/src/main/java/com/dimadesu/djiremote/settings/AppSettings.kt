package com.dimadesu.djiremote.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_STATUS_NOTIFICATION = "status_notification"

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _showStatusNotification = MutableStateFlow(false)
    val showStatusNotification: StateFlow<Boolean> = _showStatusNotification

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isDarkMode.value = prefs.getBoolean(KEY_DARK_MODE, false)
        _showStatusNotification.value = prefs.getBoolean(KEY_STATUS_NOTIFICATION, false)

        // Language is handled by AppCompatDelegate automatically if set,
        // but we can ensure it's applied if we want a specific default.
        // If no language is set, it will follow system or English as per strings.xml
    }

    fun setLanguage(context: Context, languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)

        // Restart the app to apply changes cleanly
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        (context as? android.app.Activity)?.finish()
    }

    fun toggleDarkMode(context: Context) {
        _isDarkMode.value = !_isDarkMode.value
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, _isDarkMode.value).apply()
    }

    fun toggleStatusNotification(context: Context) {
        _showStatusNotification.value = !_showStatusNotification.value
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_STATUS_NOTIFICATION, _showStatusNotification.value).apply()

        // Trigger service start/stop
        val intent = android.content.Intent(context, com.dimadesu.djiremote.NotificationService::class.java)
        if (_showStatusNotification.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }
}
