package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SoapLogEntry
import com.example.data.WeMoDatabase
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.data.WeMoRepository
import com.example.data.WeMoRuleEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WeMoBottomTab {
    DEVICES,
    RULES,
    MORE
}

class WeMoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WeMoRepository = WeMoRepository(
        WeMoDatabase.getDatabase(application).wemoDao()
    )

    val devices: StateFlow<List<WeMoDeviceEntity>> = repository.allDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WeMoRepository.defaultClassicDevices()
        )

    val rules: StateFlow<List<WeMoRuleEntity>> = repository.allRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WeMoRepository.defaultClassicRules()
        )

    val soapLogs: StateFlow<List<SoapLogEntry>> = repository.soapLogs

    private val _selectedTab = MutableStateFlow(WeMoBottomTab.DEVICES)
    val selectedTab: StateFlow<WeMoBottomTab> = _selectedTab.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _expandedDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedDeviceIds: StateFlow<Set<Int>> = _expandedDeviceIds.asStateFlow()

    private val _togglingDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val togglingDeviceIds: StateFlow<Set<Int>> = _togglingDeviceIds.asStateFlow()

    private val _editingDevice = MutableStateFlow<WeMoDeviceEntity?>(null)
    val editingDevice: StateFlow<WeMoDeviceEntity?> = _editingDevice.asStateFlow()

    private val _showAddDeviceDialog = MutableStateFlow(false)
    val showAddDeviceDialog: StateFlow<Boolean> = _showAddDeviceDialog.asStateFlow()

    private val _showAddRuleDialog = MutableStateFlow(false)
    val showAddRuleDialog: StateFlow<Boolean> = _showAddRuleDialog.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _useWeMiWordmark = MutableStateFlow(true)
    val useWeMiWordmark: StateFlow<Boolean> = _useWeMiWordmark.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    fun selectTab(tab: WeMoBottomTab) {
        _selectedTab.value = tab
        if (tab != WeMoBottomTab.DEVICES) {
            _isEditMode.value = false
        }
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
    }

    fun toggleExpandDevice(deviceId: Int) {
        val current = _expandedDeviceIds.value
        _expandedDeviceIds.value = if (deviceId in current) {
            current - deviceId
        } else {
            current + deviceId
        }
    }

    fun toggleDevicePower(device: WeMoDeviceEntity) {
        viewModelScope.launch {
            _togglingDeviceIds.value = _togglingDeviceIds.value + device.id
            val updated = repository.toggleDevicePower(device)
            _togglingDeviceIds.value = _togglingDeviceIds.value - device.id
            showTransientStatus(
                "${updated.name}: Turned ${if (updated.isOn) "ON" else "OFF"} (${updated.ipAddress}:${updated.port})"
            )
        }
    }

    fun updateBrightness(device: WeMoDeviceEntity, brightness: Int) {
        viewModelScope.launch {
            repository.updateDeviceBrightness(device, brightness)
        }
    }

    fun simulateOrRefreshMotion(device: WeMoDeviceEntity) {
        viewModelScope.launch {
            repository.triggerMotionEvent(device)
            showTransientStatus("Motion sensed on ${device.name}")
        }
    }

    fun reconnectDevice(device: WeMoDeviceEntity) {
        viewModelScope.launch {
            _togglingDeviceIds.value = _togglingDeviceIds.value + device.id
            repository.reconnectDevice(device)
            _togglingDeviceIds.value = _togglingDeviceIds.value - device.id
            showTransientStatus("${device.name} reconnected at ${device.ipAddress}:${device.port}")
        }
    }

    fun markDeviceOffline(device: WeMoDeviceEntity, offline: Boolean) {
        viewModelScope.launch {
            repository.setDeviceReachableState(device, !offline)
            showTransientStatus(
                if (offline) "${device.name} marked as Not Detected"
                else "${device.name} marked as Online"
            )
        }
    }

    fun refreshDevicesFromNetwork() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val addedCount = repository.refreshAndDiscoverAll()
            delay(350)
            _isRefreshing.value = false
            val msg = if (addedCount > 0) {
                "SSDP Scan complete: Discovered $addedCount new switch(es) on Wi-Fi"
            } else {
                "SSDP Scan & SOAP Poll complete (${devices.value.count { it.isReachable }} active)"
            }
            showTransientStatus(msg)
        }
    }

    fun openAddDeviceDialog() {
        _showAddDeviceDialog.value = true
    }

    fun closeAddDeviceDialog() {
        _showAddDeviceDialog.value = false
    }

    fun openEditDeviceDialog(device: WeMoDeviceEntity) {
        _editingDevice.value = device
    }

    fun closeEditDeviceDialog() {
        _editingDevice.value = null
    }

    fun saveDevice(
        id: Int,
        name: String,
        deviceType: WeMoDeviceType,
        ipAddress: String,
        port: Int,
        brightness: Int,
        isReachable: Boolean,
        isOn: Boolean,
        autoOffMinutes: Int
    ) {
        viewModelScope.launch {
            val existing = devices.value.find { it.id == id }
            val entity = WeMoDeviceEntity(
                id = id,
                name = name.trim().ifEmpty { "WeMi Light Switch" },
                deviceType = deviceType.name,
                ipAddress = ipAddress.trim().ifEmpty { "192.168.1.115" },
                port = port.coerceIn(1, 65535),
                isOn = isOn,
                isReachable = isReachable,
                brightness = brightness.coerceIn(1, 100),
                lastMotionTime = existing?.lastMotionTime ?: "4:15:15 PM",
                serialNumber = existing?.serialNumber ?: "221517K01018NEW",
                sortOrder = existing?.sortOrder ?: devices.value.size,
                autoOffMinutes = autoOffMinutes.coerceIn(0, 240)
            )
            repository.saveDevice(entity)
            _showAddDeviceDialog.value = false
            _editingDevice.value = null
            showTransientStatus("Saved ${entity.name} (${entity.ipAddress}:${entity.port})")
        }
    }

    fun deleteDevice(device: WeMoDeviceEntity) {
        viewModelScope.launch {
            repository.deleteDevice(device.id)
            _editingDevice.value = null
            showTransientStatus("Removed ${device.name}")
        }
    }

    fun moveDevice(device: WeMoDeviceEntity, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveDeviceOrder(device, moveUp)
        }
    }

    fun turnAllSwitches(turnOn: Boolean) {
        viewModelScope.launch {
            val targets = devices.value.filter {
                it.isReachable && it.parsedType != WeMoDeviceType.MOTION_SENSOR && it.isOn != turnOn
            }
            targets.forEach { dev ->
                repository.toggleDevicePower(dev)
            }
            showTransientStatus(
                if (turnOn) "All reachable switches turned ON" else "All reachable switches turned OFF"
            )
        }
    }

    fun toggleRule(rule: WeMoRuleEntity) {
        viewModelScope.launch {
            repository.toggleRule(rule)
        }
    }

    fun runRuleNow(rule: WeMoRuleEntity) {
        viewModelScope.launch {
            repository.runRuleNow(rule)
            showTransientStatus("Executed rule: ${rule.name}")
        }
    }

    fun openAddRuleDialog() {
        _showAddRuleDialog.value = true
    }

    fun closeAddRuleDialog() {
        _showAddRuleDialog.value = false
    }

    fun addRule(
        name: String,
        ruleType: String,
        targetDevice: WeMoDeviceEntity,
        triggerSummary: String,
        targetStateOn: Boolean,
        targetBrightness: Int
    ) {
        viewModelScope.launch {
            val actionSummary = buildString {
                append(if (targetStateOn) "Turn ON " else "Turn OFF ")
                append(targetDevice.name)
                if (targetStateOn && targetDevice.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER) {
                    append(" ($targetBrightness%)")
                }
            }
            repository.addRule(
                WeMoRuleEntity(
                    name = name.trim().ifEmpty { "Custom Switch Rule" },
                    ruleType = ruleType,
                    targetDeviceId = targetDevice.id,
                    targetDeviceName = targetDevice.name,
                    triggerSummary = triggerSummary.trim().ifEmpty { "Daily at 7:00 PM" },
                    actionSummary = actionSummary,
                    targetStateOn = targetStateOn,
                    targetBrightness = targetBrightness.coerceIn(1, 100),
                    isEnabled = true
                )
            )
            _showAddRuleDialog.value = false
            showTransientStatus("Created rule: $name")
        }
    }

    fun deleteRule(rule: WeMoRuleEntity) {
        viewModelScope.launch {
            repository.deleteRule(rule.id)
            showTransientStatus("Deleted rule: ${rule.name}")
        }
    }

    fun resetToClassicScreenshotDevices() {
        viewModelScope.launch {
            repository.resetToClassicScreenshotPreset()
            showTransientStatus("Restored classic 8 devices from screenshot")
        }
    }

    fun toggleBrandWordmark() {
        _useWeMiWordmark.value = !_useWeMiWordmark.value
    }

    fun clearSoapLogs() {
        repository.clearLogs()
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    private fun showTransientStatus(message: String) {
        viewModelScope.launch {
            _statusBannerMessage.value = message
            delay(3200)
            if (_statusBannerMessage.value == message) {
                _statusBannerMessage.value = null
            }
        }
    }
}
