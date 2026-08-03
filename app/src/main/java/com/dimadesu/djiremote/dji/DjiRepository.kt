package com.dimadesu.djiremote.dji

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference
import java.util.UUID

object DjiRepository {
    private val _devices = MutableStateFlow<List<SettingsDjiDevice>>(emptyList())
    val devices: StateFlow<List<SettingsDjiDevice>> = _devices

    private val _lastUsedDeviceId = MutableStateFlow<UUID?>(null)
    val lastUsedDeviceId: StateFlow<UUID?> = _lastUsedDeviceId

    private var contextRef: WeakReference<Context>? = null
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        this.contextRef = WeakReference(context.applicationContext)
        isInitialized = true

        // Load devices from storage
        _devices.value = DjiDeviceStorage.loadDevices(context)
        _lastUsedDeviceId.value = DjiDeviceStorage.loadLastUsedDeviceId(context)

        // If no devices, seed with example
        if (_devices.value.isEmpty()) {
            _devices.value = listOf(SettingsDjiDevice(name = "ActionCam 1"))
            saveToStorage()
        }
    }

    private fun saveToStorage() {
        contextRef?.get()?.let { ctx ->
            DjiDeviceStorage.saveDevices(ctx, _devices.value)
        }
    }

    fun addDevice(device: SettingsDjiDevice) {
        _devices.value += device
        saveToStorage()
    }

    fun removeDevice(id: UUID) {
        _devices.value = _devices.value.filterNot { it.id == id }
        saveToStorage()
    }

    fun updateDevice(device: SettingsDjiDevice) {
        // Use copy() to ensure StateFlow detects the change (same-reference mutation is invisible)
        val updated = device.copy()
        _devices.value = _devices.value.map { if (it.id == updated.id) updated else it }
        saveToStorage()
    }

    fun updateLastUsedDevice(id: UUID) {
        _lastUsedDeviceId.value = id
        contextRef?.get()?.let { DjiDeviceStorage.saveLastUsedDeviceId(it, id) }
    }
}

// Removed unused DjiScanner object
