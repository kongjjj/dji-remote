package com.dimadesu.djiremote.dji

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.OutputStreamWriter
import java.util.UUID

object DjiBackupManager {
    private val gson = Gson()

    fun exportSettings(context: Context, uri: Uri): Boolean {
        return try {
            val devices = DjiRepository.devices.value
            val json = gson.toJson(devices)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importSettings(context: Context, uri: Uri): Int {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return -1

            val type = object : TypeToken<List<SettingsDjiDevice>>() {}.type
            val importedDevices: List<SettingsDjiDevice> = gson.fromJson(json, type) ?: return -1

            val currentDevices = DjiRepository.devices.value
            var addedCount = 0

            importedDevices.forEach { imported ->
                val isDuplicate = currentDevices.any { existing ->
                    existing.bluetoothPeripheralAddress == imported.bluetoothPeripheralAddress &&
                            existing.wifiSsid == imported.wifiSsid &&
                            existing.rtmpUrl == imported.rtmpUrl
                }

                if (!isDuplicate) {
                    // Create a new UUID to avoid collisions if imported from another device's backup
                    val deviceToAdd = imported.copy(id = UUID.randomUUID())
                    DjiRepository.addDevice(deviceToAdd)
                    addedCount++
                }
            }
            addedCount
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }
}
