package org.suppressingfire.wemi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.data.RuleEntity
import org.suppressingfire.wemi.data.RuleScheduler
import org.suppressingfire.wemi.data.inferRoomFromName
import org.suppressingfire.wemi.network.LanNetworkSnapshot
import org.suppressingfire.wemi.network.SsdpScanReport
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.ScannedAp
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.SetupDeviceDescriptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class BottomTab {
    DEVICES,
    RULES,
    MORE
}

class SwitchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SwitchRepository = SwitchRepository(
        dao = SwitchDatabase.getDatabase(application).switchDao(),
        context = application.applicationContext
    )

    val isDemoShowroomMode: StateFlow<Boolean> = repository.isDemoShowroomMode

    private val networkMonitor = NetworkMonitor(
        context = application.applicationContext,
        fallbackIpResolver = { repository.detectCurrentLanIp() }
    )
    val lanNetworkState: StateFlow<LanNetworkSnapshot> = networkMonitor.networkState

    private val ruleScheduler = RuleScheduler(
        scope = viewModelScope,
        onAutoOffExpired = { deviceId ->
            val updated = repository.turnOffDeviceById(deviceId)
            if (updated != null) {
                showTransientStatus("${updated.name}: Turned OFF automatically by Auto-Off Timer")
            }
        },
        onScheduleRuleTriggered = { rule ->
            repository.runRuleNow(rule)
            showTransientStatus("Scheduled Rule Executed: ${rule.name}")
        }
    )
    val activeAutoOffCountdownsSec: StateFlow<Map<Int, Int>> = ruleScheduler.activeCountdownsSec

    val devices: StateFlow<List<DeviceEntity>> = repository.allDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (repository.isDemoShowroomMode.value) SwitchRepository.defaultClassicDevices() else emptyList()
        )

    val homeNetworks: StateFlow<List<HomeNetworkEntity>> = repository.allHomeNetworks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (repository.isDemoShowroomMode.value) SwitchRepository.defaultHomeNetworks() else emptyList()
        )

    val rules: StateFlow<List<RuleEntity>> = repository.allRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (repository.isDemoShowroomMode.value) SwitchRepository.defaultClassicRules() else emptyList()
        )

    val soapLogs: StateFlow<List<SoapLogEntry>> = repository.soapLogs
    val latestSsdpReport: StateFlow<SsdpScanReport?> = repository.latestSsdpReport

    private val _activeNetworkKey = MutableStateFlow(DEFAULT_MAIN_HOME_KEY)
    val activeNetworkKey: StateFlow<String> = _activeNetworkKey.asStateFlow()

    private val _isReconnectingHome = MutableStateFlow(false)
    val isReconnectingHome: StateFlow<Boolean> = _isReconnectingHome.asStateFlow()

    private val _showAddHomeDialog = MutableStateFlow(false)
    val showAddHomeDialog: StateFlow<Boolean> = _showAddHomeDialog.asStateFlow()

    private val provisionApUiController = ProvisionApUiController(
        scope = viewModelScope,
        repository = repository,
        networkMonitor = networkMonitor,
        activeNetworkKeyProvider = { _activeNetworkKey.value },
        onShowTransientStatus = { msg -> showTransientStatus(msg) }
    )
    val showProvisionApDialog: StateFlow<Boolean> = provisionApUiController.showProvisionApDialog
    val scannedSetupAps: StateFlow<List<ScannedAp>> = provisionApUiController.scannedSetupAps
    val detectedSetupDevice: StateFlow<SetupDeviceDescriptor?> = provisionApUiController.detectedSetupDevice
    val setupApStatusMessage: StateFlow<String?> = provisionApUiController.setupApStatusMessage
    val setupDebugSnapshot: StateFlow<SetupDebugSnapshot> = provisionApUiController.setupDebugSnapshot
    val isScanningSetupAp: StateFlow<Boolean> = provisionApUiController.isScanningSetupAp
    val isProvisioningAp: StateFlow<Boolean> = provisionApUiController.isProvisioningAp

    private val _selectedTab = MutableStateFlow(BottomTab.DEVICES)
    val selectedTab: StateFlow<BottomTab> = _selectedTab.asStateFlow()

    private val _isGroupedByRoom = MutableStateFlow(true)
    val isGroupedByRoom: StateFlow<Boolean> = _isGroupedByRoom.asStateFlow()

    private val _selectedRoomFilter = MutableStateFlow<String?>(null)
    val selectedRoomFilter: StateFlow<String?> = _selectedRoomFilter.asStateFlow()

    private val _collapsedRooms = MutableStateFlow<Set<String>>(emptySet())
    val collapsedRooms: StateFlow<Set<String>> = _collapsedRooms.asStateFlow()

    private val _togglingRooms = MutableStateFlow<Set<String>>(emptySet())
    val togglingRooms: StateFlow<Set<String>> = _togglingRooms.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _showSsdpScannerSheet = MutableStateFlow(false)
    val showSsdpScannerSheet: StateFlow<Boolean> = _showSsdpScannerSheet.asStateFlow()

    private val _selectedSearchTarget = MutableStateFlow("urn:Belkin:service:basicevent:1")
    val selectedSearchTarget: StateFlow<String> = _selectedSearchTarget.asStateFlow()

    private val _expandedDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedDeviceIds: StateFlow<Set<Int>> = _expandedDeviceIds.asStateFlow()

    private val _togglingDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val togglingDeviceIds: StateFlow<Set<Int>> = _togglingDeviceIds.asStateFlow()

    private val _editingDevice = MutableStateFlow<DeviceEntity?>(null)
    val editingDevice: StateFlow<DeviceEntity?> = _editingDevice.asStateFlow()

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
            val initialKey = repository.ensureSeeded()
            if (_activeNetworkKey.value == DEFAULT_MAIN_HOME_KEY) {
                _activeNetworkKey.value = initialKey
            }
            if (!isDemoShowroomMode.value) {
                reconnectCurrentHomeNetwork()
            }
        }

        // Phase 4: Start live zero-permission Wi-Fi / LAN subnet monitoring
        networkMonitor.startMonitoring { snapshot ->
            viewModelScope.launch {
                if (snapshot.isWemoSetupApSubnet) {
                    showTransientStatus(
                        "Connected to Smart Switch Setup AP (10.22.22.1) • Tap 'Provision New Setup AP' to join Home Wi-Fi"
                    )
                } else if (!isDemoShowroomMode.value) {
                    val matchedHome = repository.resolveOrCreateHomeForSubnet(
                        subnetPrefix = snapshot.subnetPrefix,
                        gatewayIp = snapshot.gatewayIp
                    )
                    if (matchedHome != null && matchedHome.networkKey != _activeNetworkKey.value) {
                        _activeNetworkKey.value = matchedHome.networkKey
                        reconnectCurrentHomeNetwork()
                    }
                }
            }
        }

        // Phase 5: Keep Auto-Off countdown timers and Schedule loop synced with live device/rule states
        ruleScheduler.startScheduleLoop { rules.value }
        viewModelScope.launch {
            combine(devices, rules) { devList, ruleList -> Pair(devList, ruleList) }
                .collectLatest { (devList, ruleList) ->
                    devList.forEach { dev ->
                        ruleScheduler.onDeviceStateUpdated(dev, ruleList)
                    }
                }
        }

        // Phase 3 (Confirmed A1): Hybrid UPnP GENA Push Subscriptions + 30s Fallback Poll in Real LAN Mode
        if (!SwitchRepository.isRunningInRobolectric()) {
            viewModelScope.launch {
                var tick = 0
                while (isActive) {
                    delay(30_000L)
                    if (!isDemoShowroomMode.value) {
                        val key = _activeNetworkKey.value
                        repository.pollActiveHomeStates(key)
                        if (tick % 8 == 0) {
                            // Renew GENA 300s subscriptions every ~240s
                            repository.refreshGenaSubscriptionsForHome(key)
                        }
                        tick++
                    }
                }
            }
        }
    }

    private fun currentWifiSsid(): String {
        val currentKey = _activeNetworkKey.value
        return homeNetworks.value.find { it.networkKey == currentKey }?.ssidLabel ?: "WeMi-Home-5G"
    }

    /**
     * Switches the active Wi-Fi Home network and automatically reconnects/rediscovers
     * all remembered WeMi/WeMo switches on that Wi-Fi network.
     */
    fun selectHomeNetwork(networkKey: String) {
        _activeNetworkKey.value = networkKey
        _selectedRoomFilter.value = null
        reconnectCurrentHomeNetwork()
    }

    /**
     * Reconnects and rediscovers all remembered switches saved under the active Wi-Fi Home.
     */
    fun reconnectCurrentHomeNetwork() {
        val targetKey = _activeNetworkKey.value
        viewModelScope.launch {
            _isReconnectingHome.value = true
            val summary = repository.reconnectAndRediscoverHome(targetKey)
            if (!isDemoShowroomMode.value) {
                repository.refreshGenaSubscriptionsForHome(targetKey)
            }
            _isReconnectingHome.value = false
            showTransientStatus(
                "Welcome back to ${summary.homeName} (${summary.ssidLabel}): Reconnected ${summary.reconnectedCount}/${summary.totalRememberedCount} remembered switches (Avg ${summary.averageLatencyMs}ms)"
            )
        }
    }

    fun openAddHomeDialog() {
        _showAddHomeDialog.value = true
    }

    fun closeAddHomeDialog() {
        _showAddHomeDialog.value = false
    }

    fun addWifiHomeNetwork(homeName: String, ssidLabel: String, subnetPrefix: String) {
        _showAddHomeDialog.value = false
        viewModelScope.launch {
            val created = repository.saveOrUpdateHomeNetwork(
                existingKey = null,
                homeName = homeName,
                ssidLabel = ssidLabel,
                subnetPrefix = subnetPrefix
            )
            _activeNetworkKey.value = created.networkKey
            _selectedRoomFilter.value = null
            showTransientStatus(
                "Remembered Wi-Fi Home '${created.homeName}' (${created.ssidLabel} • ${created.subnetPrefix}.0/24)"
            )
        }
    }

    fun updateWifiHomeNetwork(
        networkKey: String,
        homeName: String,
        ssidLabel: String,
        subnetPrefix: String
    ) {
        viewModelScope.launch {
            val updated = repository.saveOrUpdateHomeNetwork(
                existingKey = networkKey,
                homeName = homeName,
                ssidLabel = ssidLabel,
                subnetPrefix = subnetPrefix
            )
            _activeNetworkKey.value = updated.networkKey
            showTransientStatus(
                "Updated Home '${updated.homeName}' (${updated.ssidLabel} • ${updated.subnetPrefix}.0/24)"
            )
        }
    }

    /**
     * Resets all remembered Wi-Fi Homes, discovered switches, and room groupings in Real LAN Mode,
     * re-seeding a clean 'My Home' on the current local subnet.
     */
    fun resetLanHomesSwitchesAndRooms(rediscoverAfterReset: Boolean = true) {
        _selectedRoomFilter.value = null
        _collapsedRooms.value = emptySet()
        _expandedDeviceIds.value = emptySet()
        _isEditMode.value = false
        viewModelScope.launch {
            val freshKey = repository.resetLanHomesSwitchesAndRooms()
            _activeNetworkKey.value = freshKey
            if (rediscoverAfterReset) {
                showTransientStatus("Reset Wi-Fi Homes, switches & rooms • Scanning local LAN...")
                refreshDevicesFromNetwork()
            } else {
                showTransientStatus("Reset all Wi-Fi Homes, discovered switches, and rooms")
            }
        }
    }

    fun detectCurrentLanIp(): String {
        return repository.detectCurrentLanIp()
    }

    fun stepDeviceBrightness(device: DeviceEntity, deltaPercent: Int) {
        if (!device.isReachable || device.parsedType != DeviceType.LIGHTBULB_DIMMER) return
        val next = (device.brightness + deltaPercent).coerceIn(10, 100)
        updateBrightness(device, next)
        showTransientStatus("${device.name}: Brightness ${next}% via TV Remote")
    }

    fun applyTvScene(sceneKey: String) {
        viewModelScope.launch {
            val activeKey = _activeNetworkKey.value
            val currentHomeDevices = devices.value.filter { it.networkKey == activeKey }
            when (sceneKey) {
                "MOVIE_MODE" -> {
                    val bulbs = currentHomeDevices.filter {
                        it.isReachable && it.parsedType == DeviceType.LIGHTBULB_DIMMER
                    }
                    bulbs.forEach { repository.updateDeviceBrightness(it, 20) }
                    showTransientStatus("TV Movie Mode: Dimmed ${bulbs.size} smart bulb(s) to 20%")
                }
                "HOLIDAY_ON" -> {
                    val holidaySwitches = currentHomeDevices.filter {
                        it.isReachable &&
                            it.parsedType == DeviceType.SWITCH_PLUG &&
                            !it.isOn
                    }
                    holidaySwitches.forEach { repository.toggleDevicePower(it) }
                    showTransientStatus("Holiday Scene: All smart plugs turned ON")
                }
                "BEDTIME_OFF" -> {
                    turnAllSwitches(false)
                    showTransientStatus("Bedtime Scene: Turned OFF all switches across all rooms")
                }
            }
        }
    }

    fun selectTab(tab: BottomTab) {
        _selectedTab.value = tab
        if (tab != BottomTab.DEVICES) {
            _isEditMode.value = false
        }
    }

    fun toggleGroupByRoom() {
        _isGroupedByRoom.value = !_isGroupedByRoom.value
    }

    fun selectRoomFilter(roomName: String?) {
        _selectedRoomFilter.value = roomName
    }

    fun toggleRoomCollapse(roomName: String) {
        val current = _collapsedRooms.value
        _collapsedRooms.value = if (roomName in current) {
            current - roomName
        } else {
            current + roomName
        }
    }

    /**
     * Toggles all reachable switches/bulbs in [roomName] ON or OFF at once on the active Wi-Fi Home.
     */
    fun toggleRoomPower(roomName: String) {
        viewModelScope.launch {
            val activeKey = _activeNetworkKey.value
            val roomSwitches = devices.value.filter {
                it.networkKey == activeKey &&
                    it.roomName.equals(roomName, ignoreCase = true) &&
                    it.isReachable &&
                    it.parsedType != DeviceType.MOTION_SENSOR
            }
            if (roomSwitches.isEmpty()) return@launch
            val anyOn = roomSwitches.any { it.isOn }
            val targetOn = !anyOn

            _togglingRooms.value = _togglingRooms.value + roomName
            val count = repository.toggleRoomPower(roomName, targetOn, activeKey)
            _togglingRooms.value = _togglingRooms.value - roomName
            showTransientStatus(
                "$roomName: Turned ${if (targetOn) "ON" else "OFF"} $count light switch(es)"
            )
        }
    }

    fun updateRoomBrightness(roomName: String, brightness: Int) {
        viewModelScope.launch {
            val activeKey = _activeNetworkKey.value
            val count = repository.updateRoomBrightness(roomName, brightness, activeKey)
            showTransientStatus("$roomName: Set $count dimmable light(s) to $brightness%")
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

    fun toggleDevicePower(device: DeviceEntity) {
        viewModelScope.launch {
            _togglingDeviceIds.value = _togglingDeviceIds.value + device.id
            val updated = repository.toggleDevicePower(device)
            _togglingDeviceIds.value = _togglingDeviceIds.value - device.id
            if (updated.isReachable) {
                showTransientStatus(
                    "${updated.name}: Turned ${if (updated.isOn) "ON" else "OFF"} (${updated.latencyMs}ms • ${updated.ipAddress}:${updated.port})"
                )
            } else {
                showTransientStatus(
                    "${updated.name}: Offline • Unreachable at ${updated.ipAddress}:${updated.port} (State reverted)"
                )
            }
        }
    }

    fun updateBrightness(device: DeviceEntity, brightness: Int) {
        viewModelScope.launch {
            val updated = repository.updateDeviceBrightness(device, brightness)
            if (!updated.isReachable) {
                showTransientStatus(
                    "${updated.name}: Offline • Could not set brightness at ${updated.ipAddress}:${updated.port}"
                )
            }
        }
    }

    fun simulateOrRefreshMotion(device: DeviceEntity) {
        viewModelScope.launch {
            repository.triggerMotionEvent(device)
            showTransientStatus("Motion sensed on ${device.name}")
        }
    }

    fun reconnectDevice(device: DeviceEntity) {
        viewModelScope.launch {
            _togglingDeviceIds.value = _togglingDeviceIds.value + device.id
            val updated = repository.reconnectDevice(device)
            _togglingDeviceIds.value = _togglingDeviceIds.value - device.id
            if (updated.isReachable) {
                showTransientStatus(
                    "${updated.name} Active (${updated.latencyMs}ms RTT) at ${updated.ipAddress}:${updated.port}"
                )
            } else {
                showTransientStatus(
                    "${updated.name} is Offline (No response at ${updated.ipAddress})"
                )
            }
        }
    }

    fun pingDeviceLatency(device: DeviceEntity) {
        viewModelScope.launch {
            _togglingDeviceIds.value = _togglingDeviceIds.value + device.id
            val updated = repository.pingDeviceLatency(device)
            _togglingDeviceIds.value = _togglingDeviceIds.value - device.id
            if (updated.isReachable) {
                showTransientStatus(
                    "${updated.name}: Connection Active • ${updated.latencyMs}ms latency (${updated.ipAddress}:${updated.port})"
                )
            } else {
                showTransientStatus(
                    "${updated.name}: Offline • Unreachable at ${updated.ipAddress}:${updated.port}"
                )
            }
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
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val activeKey = _activeNetworkKey.value
                val activeSsid = currentWifiSsid()
                val report = repository.executeSsdpScan(
                    searchTarget = searchTarget,
                    autoAdoptNew = autoAdoptNew,
                    activeNetworkKey = activeKey,
                    activeWifiSsid = activeSsid
                )
                showTransientStatus(
                    "SSDP Scan on $activeSsid (${report.scanDurationMs}ms): Found ${report.discoveredDevices.size} UPnP smart switch devices"
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun adoptSsdpDevice(discovered: SsdpDiscoveredDevice) {
        viewModelScope.launch {
            val saved = repository.adoptDiscoveredDevice(
                discovered = discovered,
                activeNetworkKey = _activeNetworkKey.value,
                activeWifiSsid = currentWifiSsid()
            )
            showTransientStatus("Remembered ${saved.name} in ${saved.roomName} on ${saved.wifiSsid}")
        }
    }

    fun adoptAllSsdpDevices() {
        viewModelScope.launch {
            val list = latestSsdpReport.value?.discoveredDevices.orEmpty()
            val added = repository.adoptDiscoveredDevices(
                discoveredList = list,
                activeNetworkKey = _activeNetworkKey.value,
                activeWifiSsid = currentWifiSsid()
            )
            showTransientStatus(
                if (added > 0) "Adopted $added newly discovered smart switch(es) on ${currentWifiSsid()}"
                else "All ${list.size} SSDP-discovered devices are remembered on ${currentWifiSsid()}"
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
        val existing = devices.value.find { it.id == id }
        val resolvedName = name.trim().ifEmpty { "WeMi Light Switch" }
        val resolvedRoom = roomName.trim().ifEmpty { inferRoomFromName(resolvedName) }
        val activeKey = existing?.networkKey ?: _activeNetworkKey.value
        val activeSsid = existing?.wifiSsid ?: currentWifiSsid()
        val sortOrder = existing?.sortOrder ?: devices.value.size
        _showAddDeviceDialog.value = false
        _editingDevice.value = null

        viewModelScope.launch {
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
                serialNumber = existing?.serialNumber ?: "221517K01018NEW",
                firmwareVersion = existing?.firmwareVersion ?: "WeMo_WW_2.00.11532.PVT",
                hardwareModel = existing?.hardwareModel ?: "LightSwitch",
                macAddress = existing?.macAddress.orEmpty(),
                udn = existing?.udn.orEmpty(),
                signalStrengthPercent = existing?.signalStrengthPercent ?: 85,
                nightModeEnabled = existing?.nightModeEnabled ?: false,
                sortOrder = sortOrder,
                autoOffMinutes = autoOffMinutes.coerceIn(0, 240)
            )
            repository.saveDevice(entity, syncFriendlyNameToHardware = syncFriendlyNameToHardware)
            showTransientStatus(
                if (syncFriendlyNameToHardware) {
                    "Saved ${entity.name} in ${entity.roomName} & synced hardware FriendlyName"
                } else {
                    "Remembered ${entity.name} in ${entity.roomName} (${entity.wifiSsid})"
                }
            )
        }
    }

    fun deleteDevice(device: DeviceEntity) {
        _editingDevice.value = null
        viewModelScope.launch {
            repository.deleteDevice(device.id)
            showTransientStatus("Removed ${device.name}")
        }
    }

    fun moveDevice(device: DeviceEntity, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveDeviceOrder(device, moveUp)
        }
    }

    fun turnAllSwitches(turnOn: Boolean) {
        viewModelScope.launch {
            val activeKey = _activeNetworkKey.value
            val targets = devices.value.filter {
                it.networkKey == activeKey &&
                    it.isReachable &&
                    it.parsedType != DeviceType.MOTION_SENSOR &&
                    it.isOn != turnOn
            }
            targets.forEach { dev ->
                repository.toggleDevicePower(dev)
            }
            showTransientStatus(
                if (turnOn) "All reachable switches on ${currentWifiSsid()} turned ON"
                else "All reachable switches on ${currentWifiSsid()} turned OFF"
            )
        }
    }

    fun toggleRule(rule: RuleEntity) {
        viewModelScope.launch {
            repository.toggleRule(rule)
        }
    }

    fun runRuleNow(rule: RuleEntity) {
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
        targetDevice: DeviceEntity,
        triggerSummary: String,
        targetStateOn: Boolean,
        targetBrightness: Int
    ) {
        _showAddRuleDialog.value = false
        viewModelScope.launch {
            val actionSummary = buildString {
                append(if (targetStateOn) "Turn ON " else "Turn OFF ")
                append(targetDevice.name)
                if (targetStateOn && targetDevice.parsedType == DeviceType.LIGHTBULB_DIMMER) {
                    append(" ($targetBrightness%)")
                }
            }
            repository.addRule(
                RuleEntity(
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
            showTransientStatus("Created rule: $name")
        }
    }

    fun deleteRule(rule: RuleEntity) {
        viewModelScope.launch {
            repository.deleteRule(rule.id)
            showTransientStatus("Deleted rule: ${rule.name}")
        }
    }

    fun toggleDimmerNightMode(device: DeviceEntity) {
        viewModelScope.launch {
            _togglingDeviceIds.value = _togglingDeviceIds.value + device.id
            val updated = repository.toggleDimmerNightMode(device)
            _togglingDeviceIds.value = _togglingDeviceIds.value - device.id
            if (updated.isReachable) {
                showTransientStatus(
                    "${updated.name}: Hardware Night Mode (20% Cap) ${if (updated.nightModeEnabled) "ENABLED" else "DISABLED"}"
                )
            } else {
                showTransientStatus(
                    "${updated.name}: Offline • Could not configure Night Mode at ${updated.ipAddress}:${updated.port}"
                )
            }
        }
    }

    fun openProvisionApDialog() = provisionApUiController.openProvisionApDialog()

    fun closeProvisionApDialog() = provisionApUiController.closeProvisionApDialog()

    fun scanSetupApNetworks(apHost: String = "10.22.22.1") =
        provisionApUiController.scanSetupApNetworks(apHost)

    fun provisionSetupAp(
        apHost: String = "10.22.22.1",
        ssid: String,
        password: String,
        channel: Int = 6,
        authMode: String = "WPA2PSK",
        encryptionType: String = "AES",
        encryptionMethod: Int = 0,
        addPasswordLengths: Boolean? = null,
        customDeviceName: String = "",
        roomName: String = "",
        deviceType: DeviceType? = null,
        keepDialogOpenOnSuccess: Boolean = false
    ) {
        provisionApUiController.provisionSetupAp(
            apHost = apHost,
            ssid = ssid,
            password = password,
            channel = channel,
            authMode = authMode,
            encryptionType = encryptionType,
            encryptionMethod = encryptionMethod,
            addPasswordLengths = addPasswordLengths,
            customDeviceName = customDeviceName,
            roomName = roomName,
            deviceType = deviceType,
            keepDialogOpenOnSuccess = keepDialogOpenOnSuccess
        )
    }

    fun toggleDemoShowroomMode(enabled: Boolean = !isDemoShowroomMode.value) {
        viewModelScope.launch {
            val nextKey = repository.setDemoShowroomMode(enabled)
            _activeNetworkKey.value = nextKey
            _selectedRoomFilter.value = null
            if (enabled) {
                repository.genaManager.stopServer()
                showTransientStatus("Demo Showroom Mode Enabled: Loaded 13 showcase devices across 3 Wi-Fi Homes")
            } else {
                reconnectCurrentHomeNetwork()
                showTransientStatus("Real LAN Mode Active: Scanning local Wi-Fi for Belkin's discontinued smart switch platform")
            }
        }
    }

    fun resetToClassicScreenshotDevices() {
        _activeNetworkKey.value = DEFAULT_MAIN_HOME_KEY
        _selectedRoomFilter.value = null
        viewModelScope.launch {
            repository.resetToClassicScreenshotPreset()
            showTransientStatus("Restored remembered Wi-Fi Homes and classic devices")
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

    override fun onCleared() {
        super.onCleared()
        networkMonitor.stopMonitoring()
        ruleScheduler.stopAll()
        repository.close()
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
