package org.suppressingfire.wemi.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.data.inferRoomFromName
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.XmlAndSkuParser

/**
 * Cohesive ViewModel-scoped UI controller for per-device and per-room switch actions, SSDP
 * discovery scanning & adoption, and manual device CRUD dialogs.
 *
 * Guarantees:
 * - Cancellation-safe `try / finally` spinner cleanup for [togglingRooms], [togglingDeviceIds],
 *   and [isRefreshing].
 * - Synchronous modal dismissal (`_showAddDeviceDialog`, `_editingDevice`) before background
 *   `Dispatchers.IO` probes.
 * - Immediate `<100ms` UPnP GENA push subscription registration in Real LAN Mode whenever
 *   switches are discovered, adopted, manually saved, or reconnected.
 */
internal class DeviceActionsUiController(
    private val scope: CoroutineScope,
    private val repository: SwitchRepository,
    private val activeNetworkKeyProvider: () -> String,
    private val currentWifiSsidProvider: () -> String,
    private val currentDevicesProvider: () -> List<DeviceEntity>,
    private val onShowTransientStatus: (String) -> Unit
) {
    private val _togglingRooms = MutableStateFlow<Set<String>>(emptySet())
    val togglingRooms: StateFlow<Set<String>> = _togglingRooms.asStateFlow()

    private val _togglingDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val togglingDeviceIds: StateFlow<Set<Int>> = _togglingDeviceIds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _showSsdpScannerSheet = MutableStateFlow(false)
    val showSsdpScannerSheet: StateFlow<Boolean> = _showSsdpScannerSheet.asStateFlow()

    private val _selectedSearchTarget = MutableStateFlow(XmlAndSkuParser.URN_BASIC_EVENT)
    val selectedSearchTarget: StateFlow<String> = _selectedSearchTarget.asStateFlow()

    private val _editingDevice = MutableStateFlow<DeviceEntity?>(null)
    val editingDevice: StateFlow<DeviceEntity?> = _editingDevice.asStateFlow()

    private val _showAddDeviceDialog = MutableStateFlow(false)
    val showAddDeviceDialog: StateFlow<Boolean> = _showAddDeviceDialog.asStateFlow()

    private suspend fun refreshGenaIfRealLan(networkKey: String) {
        if (!repository.isDemoShowroomMode.value) {
            repository.refreshGenaSubscriptionsForHome(networkKey)
        }
    }

    fun toggleRoomPower(roomName: String) {
        scope.launch {
            val activeKey = activeNetworkKeyProvider()
            val roomSwitches = currentDevicesProvider().filter {
                it.networkKey == activeKey &&
                    it.roomName.equals(roomName, ignoreCase = true) &&
                    it.isReachable &&
                    it.parsedType != DeviceType.MOTION_SENSOR
            }
            if (roomSwitches.isEmpty()) return@launch
            val targetOn = !roomSwitches.any { it.isOn }

            _togglingRooms.update { it + roomName }
            try {
                val count = repository.toggleRoomPower(roomName, targetOn, activeKey)
                onShowTransientStatus(
                    "$roomName: Turned ${if (targetOn) "ON" else "OFF"} $count light switch(es)"
                )
            } finally {
                _togglingRooms.update { it - roomName }
            }
        }
    }

    fun updateRoomBrightness(roomName: String, brightness: Int) {
        scope.launch {
            val count = repository.updateRoomBrightness(roomName, brightness, activeNetworkKeyProvider())
            onShowTransientStatus("$roomName: Set $count dimmable light(s) to $brightness%")
        }
    }

    fun toggleDevicePower(device: DeviceEntity) {
        scope.launch {
            _togglingDeviceIds.update { it + device.id }
            try {
                val updated = repository.toggleDevicePower(device)
                if (updated.isReachable) {
                    onShowTransientStatus(
                        "${updated.name}: Turned ${if (updated.isOn) "ON" else "OFF"} (${updated.latencyMs}ms • ${updated.ipAddress}:${updated.port})"
                    )
                } else {
                    onShowTransientStatus(
                        "${updated.name}: Offline • Unreachable at ${updated.ipAddress}:${updated.port} (State reverted)"
                    )
                }
            } finally {
                _togglingDeviceIds.update { it - device.id }
            }
        }
    }

    fun updateBrightness(device: DeviceEntity, brightness: Int) {
        scope.launch {
            val updated = repository.updateDeviceBrightness(device, brightness)
            if (!updated.isReachable) {
                onShowTransientStatus(
                    "${updated.name}: Offline • Could not set brightness at ${updated.ipAddress}:${updated.port}"
                )
            }
        }
    }

    fun simulateOrRefreshMotion(device: DeviceEntity) {
        scope.launch {
            repository.triggerMotionEvent(device)
            onShowTransientStatus("Motion sensed on ${device.name}")
        }
    }

    fun reconnectDevice(device: DeviceEntity) {
        scope.launch {
            _togglingDeviceIds.update { it + device.id }
            try {
                val updated = repository.reconnectDevice(device)
                if (updated.isReachable) {
                    refreshGenaIfRealLan(updated.networkKey)
                    onShowTransientStatus(
                        "${updated.name} Active (${updated.latencyMs}ms RTT) at ${updated.ipAddress}:${updated.port}"
                    )
                } else {
                    onShowTransientStatus(
                        "${updated.name} is Offline (No response at ${updated.ipAddress})"
                    )
                }
            } finally {
                _togglingDeviceIds.update { it - device.id }
            }
        }
    }

    fun pingDeviceLatency(device: DeviceEntity) {
        scope.launch {
            _togglingDeviceIds.update { it + device.id }
            try {
                val updated = repository.pingDeviceLatency(device)
                if (updated.isReachable) {
                    onShowTransientStatus(
                        "${updated.name}: Connection Active • ${updated.latencyMs}ms latency (${updated.ipAddress}:${updated.port})"
                    )
                } else {
                    onShowTransientStatus(
                        "${updated.name}: Offline • Unreachable at ${updated.ipAddress}:${updated.port}"
                    )
                }
            } finally {
                _togglingDeviceIds.update { it - device.id }
            }
        }
    }

    fun toggleDimmerNightMode(device: DeviceEntity) {
        scope.launch {
            _togglingDeviceIds.update { it + device.id }
            try {
                val updated = repository.toggleDimmerNightMode(device)
                if (updated.isReachable) {
                    onShowTransientStatus(
                        "${updated.name}: Hardware Night Mode (20% Cap) ${if (updated.nightModeEnabled) "ENABLED" else "DISABLED"}"
                    )
                } else {
                    onShowTransientStatus(
                        "${updated.name}: Offline • Could not configure Night Mode at ${updated.ipAddress}:${updated.port}"
                    )
                }
            } finally {
                _togglingDeviceIds.update { it - device.id }
            }
        }
    }

    fun turnAllSwitches(turnOn: Boolean) {
        scope.launch {
            val activeKey = activeNetworkKeyProvider()
            val targets = currentDevicesProvider().filter {
                it.networkKey == activeKey &&
                    it.isReachable &&
                    it.parsedType != DeviceType.MOTION_SENSOR &&
                    it.isOn != turnOn
            }
            val targetIds = targets.map { it.id }.toSet()
            _togglingDeviceIds.update { it + targetIds }
            try {
                coroutineScope {
                    targets.map { dev ->
                        async {
                            try {
                                repository.setDevicePowerExplicit(dev, turnOn)
                            } finally {
                                _togglingDeviceIds.update { it - dev.id }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _togglingDeviceIds.update { it - targetIds }
            }
            val ssid = currentWifiSsidProvider()
            onShowTransientStatus(
                if (turnOn) "All reachable switches on $ssid turned ON"
                else "All reachable switches on $ssid turned OFF"
            )
        }
    }

    fun openSsdpScannerAndScan(searchTarget: String = _selectedSearchTarget.value) {
        _selectedSearchTarget.value = searchTarget
        _showSsdpScannerSheet.value = true
        runSsdpScan(searchTarget = searchTarget, autoAdoptNew = false)
    }

    fun closeSsdpScanner() {
        _showSsdpScannerSheet.value = false
    }

    fun selectSearchTargetAndScan(searchTarget: String) {
        _selectedSearchTarget.value = searchTarget
        runSsdpScan(searchTarget = searchTarget, autoAdoptNew = false)
    }

    fun refreshDevicesFromNetwork() {
        runSsdpScan(searchTarget = _selectedSearchTarget.value, autoAdoptNew = true)
    }

    private fun runSsdpScan(searchTarget: String, autoAdoptNew: Boolean) {
        if (_isRefreshing.value) return
        scope.launch {
            _isRefreshing.value = true
            try {
                val activeKey = activeNetworkKeyProvider()
                val activeSsid = currentWifiSsidProvider()
                val report = repository.executeSsdpScan(
                    searchTarget = searchTarget,
                    autoAdoptNew = autoAdoptNew,
                    activeNetworkKey = activeKey,
                    activeWifiSsid = activeSsid
                )
                refreshGenaIfRealLan(activeKey)
                onShowTransientStatus(
                    "SSDP Scan on $activeSsid (${report.scanDurationMs}ms): Found ${report.discoveredDevices.size} UPnP smart switch devices"
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun adoptSsdpDevice(discovered: SsdpDiscoveredDevice) {
        scope.launch {
            val activeKey = activeNetworkKeyProvider()
            val saved = repository.adoptDiscoveredDevice(
                discovered = discovered,
                activeNetworkKey = activeKey,
                activeWifiSsid = currentWifiSsidProvider()
            )
            refreshGenaIfRealLan(saved.networkKey)
            onShowTransientStatus("Remembered ${saved.name} in ${saved.roomName} on ${saved.wifiSsid}")
        }
    }

    fun adoptAllSsdpDevices() {
        scope.launch {
            val activeKey = activeNetworkKeyProvider()
            val ssid = currentWifiSsidProvider()
            val list = repository.latestSsdpReport.value?.discoveredDevices.orEmpty()
            val added = repository.adoptDiscoveredDevices(
                discoveredList = list,
                activeNetworkKey = activeKey,
                activeWifiSsid = ssid
            )
            refreshGenaIfRealLan(activeKey)
            onShowTransientStatus(
                if (added > 0) "Adopted $added newly discovered smart switch(es) on $ssid"
                else "All ${list.size} SSDP-discovered devices are remembered on $ssid"
            )
        }
    }

    fun openAddDeviceDialog() {
        _showAddDeviceDialog.value = true
    }

    fun closeAddDeviceDialog() {
        _showAddDeviceDialog.value = false
    }

    fun openEditDeviceDialog(device: DeviceEntity) {
        _editingDevice.value = device
    }

    fun closeEditDeviceDialog() {
        _editingDevice.value = null
    }

    fun saveDevice(
        id: Int,
        name: String,
        roomName: String,
        deviceType: DeviceType,
        ipAddress: String,
        port: Int,
        brightness: Int,
        isReachable: Boolean,
        isOn: Boolean,
        autoOffMinutes: Int,
        syncFriendlyNameToHardware: Boolean = false
    ) {
        val devices = currentDevicesProvider()
        val existing = devices.find { it.id == id }
        val resolvedName = name.trim().ifEmpty { "WeMi Light Switch" }
        val resolvedRoom = roomName.trim().ifEmpty { inferRoomFromName(resolvedName) }
        val activeKey = existing?.networkKey ?: activeNetworkKeyProvider()
        val activeSsid = existing?.wifiSsid ?: currentWifiSsidProvider()
        val sortOrder = existing?.sortOrder ?: devices.size
        _showAddDeviceDialog.value = false
        _editingDevice.value = null

        scope.launch {
            val entity = DeviceEntity(
                id = id,
                name = resolvedName,
                roomName = resolvedRoom,
                networkKey = activeKey,
                wifiSsid = activeSsid,
                deviceType = deviceType.name,
                ipAddress = ipAddress.trim().ifEmpty { "192.168.1.115" },
                port = port.coerceIn(1, 65535),
                isOn = isOn,
                isReachable = isReachable,
                brightness = brightness.coerceIn(1, 100),
                latencyMs = existing?.latencyMs ?: 14,
                lastCheckedTime = existing?.lastCheckedTime ?: "Connected now",
                lastMotionTime = existing?.lastMotionTime ?: "4:15:15 PM",
                serialNumber = existing?.serialNumber ?: LanRuntimeUtils.DEFAULT_NEW_PLACEHOLDER_SERIAL,
                firmwareVersion = existing?.firmwareVersion ?: "WeMo_WW_2.00.11532.PVT",
                hardwareModel = existing?.hardwareModel ?: "LightSwitch",
                macAddress = existing?.macAddress.orEmpty(),
                udn = existing?.udn.orEmpty(),
                signalStrengthPercent = existing?.signalStrengthPercent ?: 85,
                nightModeEnabled = existing?.nightModeEnabled ?: false,
                sortOrder = sortOrder,
                autoOffMinutes = autoOffMinutes.coerceIn(0, 240)
            )
            val saved = repository.saveDevice(entity, syncFriendlyNameToHardware = syncFriendlyNameToHardware)
            if (saved.isReachable) {
                refreshGenaIfRealLan(saved.networkKey)
            }
            onShowTransientStatus(
                if (syncFriendlyNameToHardware) {
                    "Saved ${saved.name} in ${saved.roomName} & synced hardware FriendlyName"
                } else {
                    "Remembered ${saved.name} in ${saved.roomName} (${saved.wifiSsid})"
                }
            )
        }
    }

    fun deleteDevice(device: DeviceEntity) {
        _editingDevice.value = null
        scope.launch {
            repository.deleteDevice(device.id)
            onShowTransientStatus("Removed ${device.name}")
        }
    }

    fun moveDevice(device: DeviceEntity, moveUp: Boolean, withinRoomOnly: Boolean = false) {
        scope.launch {
            repository.moveDeviceOrder(device, moveUp, withinRoomOnly)
        }
    }
}
