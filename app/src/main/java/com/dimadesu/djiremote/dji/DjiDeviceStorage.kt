package com.dimadesu.djiremote.dji

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object DjiDeviceStorage {
    private const val PREFS_NAME = "dji_devices"
    private const val KEY_DEVICES = "devices"
    private const val KEY_LAST_USED_ID = "last_used_id"

    private val gson = Gson()

    fun saveDevices(context: Context, devices: List<SettingsDjiDevice>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(devices)
        prefs.edit().putString(KEY_DEVICES, json).apply()
    }

    fun loadDevices(context: Context): List<SettingsDjiDevice> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DEVICES, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<SettingsDjiDevice>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLastUsedDeviceId(context: Context, id: UUID?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_USED_ID, id?.toString()).apply()
    }

    fun loadLastUsedDeviceId(context: Context): UUID? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idString = prefs.getString(KEY_LAST_USED_ID, null) ?: return null
        return try {
            UUID.fromString(idString)
        } catch (e: Exception) {
            null
        }
    }
}
