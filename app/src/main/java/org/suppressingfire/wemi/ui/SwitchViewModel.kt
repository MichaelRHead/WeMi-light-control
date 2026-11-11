package org.suppressingfire.wemi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.RuleEntity
import org.suppressingfire.wemi.data.RuleScheduler
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.data.inferRoomFromName
import org.suppressingfire.wemi.network.LanNetworkSnapshot
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.ScannedAp
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.SetupDeviceDescriptor
import org.suppressingfire.wemi.network.SsdpScanReport

enum class BottomTab {
    DEVICES,
    RULES,
    MORE
}

/**
 * Typed 1-press lighting scenes exposed in the Android TV 10-foot header (`wemi TV`).
 */
enum class TvScene(val wireKey: String) {
    MOVIE_MODE("MOVIE_MODE"),
    HOLIDAY_ON("HOLIDAY_ON"),
    BEDTIME_OFF("BEDTIME_OFF");

    companion object {
        fun fromWireKey(raw: String): TvScene? =
            entries.firstOrNull { it.wireKey.equals(raw.trim(), ignoreCase = true) }
    }
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

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _useWeMiWordmark = MutableStateFlow(true)
    val useWeMiWordmark: StateFlow<Boolean> = _useWeMiWordmark.asStateFlow()

    private val homeAndRulesController = HomeAndRulesUiController(
        scope = viewModelScope,
        repository = repository,
        onClearRoomFilterAndSelection = {
            _selectedRoomFilter.value = null
            _collapsedRooms.value = emptySet()
            _expandedDeviceIds.value = emptySet()
            _isEditMode.value = false
        },
        onShowTransientStatus = { msg -> showTransientStatus(msg) }
    )
    val activeNetworkKey: StateFlow<String> = homeAndRulesController.activeNetworkKey
    val isReconnectingHome: StateFlow<Boolean> = homeAndRulesController.isReconnectingHome
    val showAddHomeDialog: StateFlow<Boolean> = homeAndRulesController.showAddHomeDialog
    val showAddRuleDialog: StateFlow<Boolean> = homeAndRulesController.showAddRuleDialog

    private val provisionApUiController = ProvisionApUiController(
        scope = viewModelScope,
        repository = repository,
        networkMonitor = networkMonitor,
        activeNetworkKeyProvider = { activeNetworkKey.value },
        onShowTransientStatus = { msg -> showTransientStatus(msg) }
    )
    val showProvisionApDialog: StateFlow<Boolean> = provisionApUiController.showProvisionApDialog
    val scannedSetupAps: StateFlow<List<ScannedAp>> = provisionApUiController.scannedSetupAps
    val detectedSetupDevice: StateFlow<SetupDeviceDescriptor?> = provisionApUiController.detectedSetupDevice
    val setupApStatusMessage: StateFlow<String?> = provisionApUiController.setupApStatusMessage
    val setupDebugSnapshot: StateFlow<SetupDebugSnapshot> = provisionApUiController.setupDebugSnapshot
    val isScanningSetupAp: StateFlow<Boolean> = provisionApUiController.isScanningSetupAp
    val isProvisioningAp: StateFlow<Boolean> = provisionApUiController.isProvisioningAp

    init {
        viewModelScope.launch {
            val initialKey = repository.ensureSeeded()
            if (activeNetworkKey.value == DEFAULT_MAIN_HOME_KEY) {
                homeAndRulesController.setActiveNetworkKey(initialKey)
            }
            if (!isDemoShowroomMode.value) {
                reconnectCurrentHomeNetwork()
            }
        }

        // Start live zero-permission Wi-Fi / LAN subnet monitoring
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
                    if (matchedHome != null && matchedHome.networkKey != activeNetworkKey.value) {
                        homeAndRulesController.setActiveNetworkKey(matchedHome.networkKey)
                        reconnectCurrentHomeNetwork()
                    }
                }
            }
        }

        // Keep Auto-Off countdown timers and Schedule loop synced with live device/rule states
        ruleScheduler.startScheduleLoop { rules.value }
        viewModelScope.launch {
            combine(devices, rules) { devList, ruleList -> Pair(devList, ruleList) }
                .collectLatest { (devList, ruleList) ->
                    devList.forEach { dev ->
                        ruleScheduler.onDeviceStateUpdated(dev, ruleList)
                    }
                }
        }

        // Hybrid UPnP GENA Push Subscriptions + 30s Fallback Poll in Real LAN Mode
        if (!SwitchRepository.isRunningInRobolectric()) {
            viewModelScope.launch {
                var tick = 0
                while (isActive) {
                    delay(30_000L)
                    if (!isDemoShowroomMode.value) {
                        val key = activeNetworkKey.value
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
        val currentKey = activeNetworkKey.value
        return homeNetworks.value.find { it.networkKey == currentKey }?.ssidLabel ?: "WeMi-Home-5G"
    }

    fun selectHomeNetwork(networkKey: String) =
        homeAndRulesController.selectHomeNetwork(networkKey) { _selectedRoomFilter.value = null }

    fun reconnectCurrentHomeNetwork() = homeAndRulesController.reconnectCurrentHomeNetwork()

    fun openAddHomeDialog() = homeAndRulesController.openAddHomeDialog()

    fun closeAddHomeDialog() = homeAndRulesController.closeAddHomeDialog()

    fun addWifiHomeNetwork(homeName: String, ssidLabel: String, subnetPrefix: String) =
        homeAndRulesController.addWifiHomeNetwork(homeName, ssidLabel, subnetPrefix) {
            _selectedRoomFilter.value = null
        }

    fun updateWifiHomeNetwork(networkKey: String, homeName: String, ssidLabel: String, subnetPrefix: String) =
        homeAndRulesController.updateWifiHomeNetwork(networkKey, homeName, ssidLabel, subnetPrefix)

    fun resetLanHomesSwitchesAndRooms(rediscoverAfterReset: Boolean = true) =
        homeAndRulesController.resetLanHomesSwitchesAndRooms(rediscoverAfterReset) {
            refreshDevicesFromNetwork()
        }

    fun detectCurrentLanIp(): String = repository.detectCurrentLanIp()

    fun stepDeviceBrightness(device: DeviceEntity, deltaPercent: Int) {
        if (!device.isReachable || device.parsedType != DeviceType.LIGHTBULB_DIMMER) return
        val next = (device.brightness + deltaPercent).coerceIn(10, 100)
        updateBrightness(device, next)
        showTransientStatus("${device.name}: Brightness ${next}% via TV Remote")
    }

    fun applyTvScene(sceneKey: String) =
        homeAndRulesController.applyTvScene(
            sceneKey = sceneKey,
            currentDevices = { devices.value },
            onTurnAllSwitches = { turnOn -> turnAllSwitches(turnOn) }
        )

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
        _collapsedRooms.value = if (roomName in current) current - roomName else current + roomName
    }

    fun toggleRoomPower(roomName: String) {
        viewModelScope.launch {
            val activeKey = activeNetworkKey.value
            val roomSwitches = devices.value.filter {
                it.networkKey == activeKey &&
                    it.roomName.equals(roomName, ignoreCase = true) &&
                    it.isReachable &&
                    it.parsedType != DeviceType.MOTION_SENSOR
            }
            if (roomSwitches.isEmpty()) return@launch
            val targetOn = !roomSwitches.any { it.isOn }

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
            val count = repository.updateRoomBrightness(roomName, brightness, activeNetworkKey.value)
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
        _expandedDeviceIds.value = if (deviceId in current) current - deviceId else current + deviceId
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
                val activeKey = activeNetworkKey.value
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
                activeNetworkKey = activeNetworkKey.value,
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
                activeNetworkKey = activeNetworkKey.value,
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
        val activeKey = existing?.networkKey ?: activeNetworkKey.value
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
            val activeKey = activeNetworkKey.value
            val targets = devices.value.filter {
                it.networkKey == activeKey &&
                    it.isReachable &&
                    it.parsedType != DeviceType.MOTION_SENSOR &&
                    it.isOn != turnOn
            }
            targets.forEach { dev -> repository.toggleDevicePower(dev) }
            showTransientStatus(
                if (turnOn) "All reachable switches on ${currentWifiSsid()} turned ON"
                else "All reachable switches on ${currentWifiSsid()} turned OFF"
            )
        }
    }

    fun toggleRule(rule: RuleEntity) = homeAndRulesController.toggleRule(rule)

    fun runRuleNow(rule: RuleEntity) = homeAndRulesController.runRuleNow(rule)

    fun openAddRuleDialog() = homeAndRulesController.openAddRuleDialog()

    fun closeAddRuleDialog() = homeAndRulesController.closeAddRuleDialog()

    fun addRule(
        name: String,
        ruleType: String,
        targetDevice: DeviceEntity,
        triggerSummary: String,
        targetStateOn: Boolean,
        targetBrightness: Int
    ) = homeAndRulesController.addRule(
        name = name,
        ruleType = ruleType,
        targetDevice = targetDevice,
        triggerSummary = triggerSummary,
        targetStateOn = targetStateOn,
        targetBrightness = targetBrightness
    )

    fun deleteRule(rule: RuleEntity) = homeAndRulesController.deleteRule(rule)

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

    fun toggleDemoShowroomMode(enabled: Boolean = !isDemoShowroomMode.value) =
        homeAndRulesController.toggleDemoShowroomMode(enabled) { _selectedRoomFilter.value = null }

    fun resetToClassicScreenshotDevices() =
        homeAndRulesController.resetToClassicScreenshotDevices { _selectedRoomFilter.value = null }

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
