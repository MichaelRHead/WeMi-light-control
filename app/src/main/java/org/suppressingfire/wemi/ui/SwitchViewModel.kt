package org.suppressingfire.wemi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareLocationInfo
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.LanNetworkSnapshot
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.ScannedAp
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.SetupDeviceDescriptor
import org.suppressingfire.wemi.network.SsdpScanReport
import org.suppressingfire.wemi.widget.WidgetUpdater

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

class SwitchViewModel internal constructor(
    application: Application,
    repositoryOverride: SwitchRepository?
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, null)

    private val repository: SwitchRepository = repositoryOverride ?: SwitchRepository(
        dao = SwitchDatabase.getDatabase(application).switchDao(),
        context = application.applicationContext
    )

    val isDemoShowroomMode: StateFlow<Boolean> = repository.isDemoShowroomMode

    private val networkMonitor = NetworkMonitor(
        context = application.applicationContext,
        fallbackIpResolver = { repository.detectCurrentLanIp() }
    )
    val lanNetworkState: StateFlow<LanNetworkSnapshot> = networkMonitor.networkState

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

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _expandedDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedDeviceIds: StateFlow<Set<Int>> = _expandedDeviceIds.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()
    private var statusBannerJob: Job? = null

    private val homeAndRulesController: HomeAndRulesUiController = HomeAndRulesUiController(
        scope = viewModelScope,
        repository = repository,
        onClearRoomFilterAndSelection = {
            _selectedRoomFilter.value = null
            _collapsedRooms.value = emptySet()
            _expandedDeviceIds.value = emptySet()
            _isEditMode.value = false
            hardwareRulesController.dismissAllDialogs()
            householdSync.dismissAllDialogs()
        },
        onShowTransientStatus = ::showTransientStatus
    )
    val activeNetworkKey: StateFlow<String> = homeAndRulesController.activeNetworkKey
    val isReconnectingHome: StateFlow<Boolean> = homeAndRulesController.isReconnectingHome
    val showAddHomeDialog: StateFlow<Boolean> = homeAndRulesController.showAddHomeDialog

    private val hardwareRulesController: HardwareRulesUiController = HardwareRulesUiController(
        scope = viewModelScope, repository = repository,
        activeNetworkKeyProvider = { activeNetworkKey.value }, onShowTransientStatus = ::showTransientStatus
    )
    val hardwareRulesByDeviceId: StateFlow<Map<Int, DeviceHardwareRulesSnapshot>> =
        hardwareRulesController.hardwareRulesByDeviceId
    val inspectingRuleDeviceIds: StateFlow<Set<Int>> = hardwareRulesController.inspectingRuleDeviceIds
    val isScanningAllDeviceRules: StateFlow<Boolean> = hardwareRulesController.isScanningAllDeviceRules
    val hardwareRuleEditorRequest: StateFlow<HardwareRuleEditorRequest?> = hardwareRulesController.editorRequest
    val solarLocationDialogDevice: StateFlow<DeviceEntity?> = hardwareRulesController.locationDialogDevice
    val backupRestoreDialogDevice: StateFlow<DeviceEntity?> = hardwareRulesController.backupDialogDevice
    val pendingDeleteHardwareRule: StateFlow<Pair<DeviceEntity, HardwareRule>?> =
        hardwareRulesController.pendingDeleteRule

    internal val householdSync: HouseholdSyncUiController = HouseholdSyncUiController(
        scope = viewModelScope,
        appContext = application.applicationContext,
        repository = repository,
        onActiveNetworkKeyUpdated = { resolvedKey ->
            homeAndRulesController.setActiveNetworkKey(resolvedKey)
            _selectedRoomFilter.value = null
            _collapsedRooms.value = emptySet()
            _expandedDeviceIds.value = emptySet()
        },
        onAfterImportApplied = {
            if (!isDemoShowroomMode.value) reconnectCurrentHomeNetwork()
        },
        onShowTransientStatus = ::showTransientStatus
    )
    val householdSyncState: StateFlow<HouseholdSyncUiState> = householdSync.uiState

    private val deviceActionsController = DeviceActionsUiController(
        scope = viewModelScope, repository = repository,
        activeNetworkKeyProvider = { activeNetworkKey.value },
        currentWifiSsidProvider = ::currentWifiSsid,
        currentDevicesProvider = { devices.value },
        onShowTransientStatus = ::showTransientStatus
    )
    val togglingRooms: StateFlow<Set<String>> = deviceActionsController.togglingRooms
    val togglingDeviceIds: StateFlow<Set<Int>> = deviceActionsController.togglingDeviceIds
    val isRefreshing: StateFlow<Boolean> = deviceActionsController.isRefreshing
    val showSsdpScannerSheet: StateFlow<Boolean> = deviceActionsController.showSsdpScannerSheet
    val selectedSearchTarget: StateFlow<String> = deviceActionsController.selectedSearchTarget
    val editingDevice: StateFlow<DeviceEntity?> = deviceActionsController.editingDevice
    val showAddDeviceDialog: StateFlow<Boolean> = deviceActionsController.showAddDeviceDialog

    private val provisionApUiController = ProvisionApUiController(
        scope = viewModelScope, repository = repository, networkMonitor = networkMonitor,
        activeNetworkKeyProvider = { activeNetworkKey.value }, onShowTransientStatus = ::showTransientStatus
    )
    val showProvisionApDialog: StateFlow<Boolean> = provisionApUiController.showProvisionApDialog
    val scannedSetupAps: StateFlow<List<ScannedAp>> = provisionApUiController.scannedSetupAps
    val detectedSetupDevice: StateFlow<SetupDeviceDescriptor?> = provisionApUiController.detectedSetupDevice
    val setupApStatusMessage: StateFlow<String?> = provisionApUiController.setupApStatusMessage
    val setupDebugSnapshot: StateFlow<SetupDebugSnapshot> = provisionApUiController.setupDebugSnapshot
    val isScanningSetupAp: StateFlow<Boolean> = provisionApUiController.isScanningSetupAp
    val isProvisioningAp: StateFlow<Boolean> = provisionApUiController.isProvisioningAp

    init {
        val startedInDemoMode = isDemoShowroomMode.value
        viewModelScope.launch {
            val initialKey = repository.ensureSeeded()
            if (activeNetworkKey.value == DEFAULT_MAIN_HOME_KEY && isDemoShowroomMode.value == startedInDemoMode) {
                homeAndRulesController.setActiveNetworkKey(initialKey)
            }
            if (!startedInDemoMode && !isDemoShowroomMode.value) {
                reconnectCurrentHomeNetwork()
            }
        }

        // Start live zero-permission Wi-Fi / LAN subnet monitoring
        networkMonitor.startMonitoring { snapshot ->
            viewModelScope.launch {
                if (snapshot.isWemoSetupApSubnet) {
                    showTransientStatus(
                        "Connected to Smart Switch Setup AP (10.22.22.1) • Tap 'Smart Switch Setup AP Detected' to join Home Wi-Fi"
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

        viewModelScope.launch {
            devices.collectLatest { devList ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    WidgetUpdater.refreshWidgetsForDevices(getApplication(), devList)
                }
            }
        }

        // Hybrid UPnP GENA Push Subscriptions + 30s Fallback Poll in Real LAN Mode
        if (!SwitchRepository.isRunningInRobolectric()) {
            viewModelScope.launch {
                var tick = 0
                while (isActive) {
                    val localIp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.detectCurrentLanIp()
                    }
                    delay(if (LanRuntimeUtils.isCarrierGradeNatIpv4(localIp)) 15_000L else 30_000L)
                    if (!isDemoShowroomMode.value) {
                        val key = activeNetworkKey.value
                        repository.pollActiveHomeStates(key)
                        if (tick % 8 == 0) {
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
        homeAndRulesController.selectHomeNetwork(networkKey) {
            _selectedRoomFilter.value = null
            hardwareRulesController.dismissAllDialogs()
        }

    fun reconnectCurrentHomeNetwork() = homeAndRulesController.reconnectCurrentHomeNetwork()

    fun openAddHomeDialog() = homeAndRulesController.openAddHomeDialog()

    fun closeAddHomeDialog() = homeAndRulesController.closeAddHomeDialog()

    fun addWifiHomeNetwork(homeName: String, ssidLabel: String, subnetPrefix: String) =
        homeAndRulesController.addWifiHomeNetwork(homeName, ssidLabel, subnetPrefix) {
            _selectedRoomFilter.value = null
            hardwareRulesController.dismissAllDialogs()
        }

    fun resetLanHomesSwitchesAndRooms(rediscoverAfterReset: Boolean = true) {
        provisionApUiController.clearDiagnosticsState()
        homeAndRulesController.resetLanHomesSwitchesAndRooms(rediscoverAfterReset) { refreshDevicesFromNetwork() }
    }

    fun detectCurrentLanIp(): String = repository.detectCurrentLanIp()

    fun stepDeviceBrightness(device: DeviceEntity, deltaPercent: Int) =
        deviceActionsController.stepDeviceBrightness(device, deltaPercent)

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

    fun toggleGroupByRoom() { _isGroupedByRoom.value = !_isGroupedByRoom.value }
    fun selectRoomFilter(roomName: String?) { _selectedRoomFilter.value = roomName }
    fun toggleRoomCollapse(roomName: String) {
        val current = _collapsedRooms.value
        _collapsedRooms.value = if (roomName in current) current - roomName else current + roomName
    }
    fun toggleRoomPower(roomName: String) = deviceActionsController.toggleRoomPower(roomName)
    fun updateRoomBrightness(roomName: String, brightness: Int) =
        deviceActionsController.updateRoomBrightness(roomName, brightness)
    fun toggleEditMode() { _isEditMode.value = !_isEditMode.value }
    fun setEditMode(enabled: Boolean) { _isEditMode.value = enabled }
    fun toggleExpandDevice(deviceId: Int) {
        val current = _expandedDeviceIds.value
        _expandedDeviceIds.value = if (deviceId in current) current - deviceId else current + deviceId
    }
    fun toggleDevicePower(device: DeviceEntity) = deviceActionsController.toggleDevicePower(device)
    fun updateBrightness(device: DeviceEntity, brightness: Int) =
        deviceActionsController.updateBrightness(device, brightness)
    fun simulateOrRefreshMotion(device: DeviceEntity) = deviceActionsController.simulateOrRefreshMotion(device)
    fun reconnectDevice(device: DeviceEntity) = deviceActionsController.reconnectDevice(device)
    fun pingDeviceLatency(device: DeviceEntity) = deviceActionsController.pingDeviceLatency(device)
    fun openSsdpScannerAndScan(searchTarget: String = selectedSearchTarget.value, manualSubnetOverride: String? = null) =
        deviceActionsController.openSsdpScannerAndScan(searchTarget, manualSubnetOverride)
    fun scanManualSubnet(subnetPrefix: String) = deviceActionsController.scanManualSubnet(subnetPrefix)
    fun closeSsdpScanner() = deviceActionsController.closeSsdpScanner()
    fun selectSearchTargetAndScan(searchTarget: String) = deviceActionsController.selectSearchTargetAndScan(searchTarget)
    fun refreshDevicesFromNetwork() = deviceActionsController.refreshDevicesFromNetwork()
    fun adoptSsdpDevice(discovered: SsdpDiscoveredDevice) = deviceActionsController.adoptSsdpDevice(discovered)
    fun adoptAllSsdpDevices() = deviceActionsController.adoptAllSsdpDevices()
    fun openAddDeviceDialog() = deviceActionsController.openAddDeviceDialog()
    fun closeAddDeviceDialog() = deviceActionsController.closeAddDeviceDialog()
    fun openEditDeviceDialog(device: DeviceEntity) = deviceActionsController.openEditDeviceDialog(device)
    fun closeEditDeviceDialog() = deviceActionsController.closeEditDeviceDialog()

    fun saveDevice(
        id: Int, name: String, roomName: String, deviceType: DeviceType, ipAddress: String,
        port: Int, brightness: Int, isReachable: Boolean, isOn: Boolean, autoOffMinutes: Int,
        syncFriendlyNameToHardware: Boolean = false
    ) = deviceActionsController.saveDevice(
        id, name, roomName, deviceType, ipAddress, port, brightness,
        isReachable, isOn, autoOffMinutes, syncFriendlyNameToHardware
    )

    fun deleteDevice(device: DeviceEntity) {
        hardwareRulesController.dismissDialogsForDevice(device.id)
        deviceActionsController.deleteDevice(device)
    }

    fun moveDevice(device: DeviceEntity, moveUp: Boolean) = deviceActionsController.moveDevice(
        device = device, moveUp = moveUp,
        withinRoomOnly = _isGroupedByRoom.value || _selectedRoomFilter.value != null
    )

    fun turnAllSwitches(turnOn: Boolean) = deviceActionsController.turnAllSwitches(turnOn)

    fun inspectDeviceHardwareRules(device: DeviceEntity, forceRecheck: Boolean = false) =
        hardwareRulesController.inspectDeviceHardwareRules(device, forceRecheck)
    fun inspectAllHomeHardwareRules(forceRecheck: Boolean = false) =
        hardwareRulesController.inspectAllHomeHardwareRules(forceRecheck)
    fun openAddHardwareRuleDialog(device: DeviceEntity? = null) = hardwareRulesController.openAddHardwareRuleDialog(device)
    fun openEditHardwareRuleDialog(device: DeviceEntity, rule: HardwareRule) =
        hardwareRulesController.openEditHardwareRuleDialog(device, rule)
    fun closeHardwareRuleEditorDialog() = hardwareRulesController.closeHardwareRuleEditorDialog()
    fun saveHardwareRule(device: DeviceEntity, draft: HardwareRuleDraft) = hardwareRulesController.saveHardwareRule(device, draft)
    fun toggleHardwareRule(device: DeviceEntity, rule: HardwareRule, enabled: Boolean) =
        hardwareRulesController.toggleHardwareRule(device, rule, enabled)
    fun requestDeleteHardwareRule(device: DeviceEntity, rule: HardwareRule) =
        hardwareRulesController.requestDeleteHardwareRule(device, rule)
    fun cancelDeleteHardwareRule() = hardwareRulesController.cancelDeleteHardwareRule()
    fun confirmDeleteHardwareRule() = hardwareRulesController.confirmDeleteHardwareRule()
    fun syncDeviceClock(device: DeviceEntity) = hardwareRulesController.syncDeviceClock(device)
    fun openSolarLocationDialog(device: DeviceEntity) = hardwareRulesController.openSolarLocationDialog(device)
    fun closeSolarLocationDialog() = hardwareRulesController.closeSolarLocationDialog()
    fun saveSolarLocation(device: DeviceEntity, location: HardwareLocationInfo) =
        hardwareRulesController.saveSolarLocation(device, location)
    fun openBackupRestoreDialog(device: DeviceEntity) = hardwareRulesController.openBackupRestoreDialog(device)
    fun closeBackupRestoreDialog() = hardwareRulesController.closeBackupRestoreDialog()
    fun restoreHardwareRulesBackup(device: DeviceEntity, backupFilePath: String) =
        hardwareRulesController.restoreBackup(device, backupFilePath)
    fun dismissHardwareRuleDialogs() = hardwareRulesController.dismissAllDialogs()

    fun toggleDimmerNightMode(device: DeviceEntity) = deviceActionsController.toggleDimmerNightMode(device)
    fun openProvisionApDialog() = provisionApUiController.openProvisionApDialog()
    fun closeProvisionApDialog() = provisionApUiController.closeProvisionApDialog()
    fun scanSetupApNetworks(apHost: String = "10.22.22.1") = provisionApUiController.scanSetupApNetworks(apHost)

    fun provisionSetupAp(
        apHost: String = "10.22.22.1", ssid: String, password: String, channel: Int = 6,
        authMode: String = "WPA2PSK", encryptionType: String = "AES", encryptionMethod: Int = 0,
        addPasswordLengths: Boolean? = null, customDeviceName: String = "", roomName: String = "",
        deviceType: DeviceType? = null, keepDialogOpenOnSuccess: Boolean = false
    ) = provisionApUiController.provisionSetupAp(
        apHost, ssid, password, channel, authMode, encryptionType,
        encryptionMethod, addPasswordLengths, customDeviceName, roomName,
        deviceType, keepDialogOpenOnSuccess
    )

    fun toggleDemoShowroomMode(enabled: Boolean = !isDemoShowroomMode.value) =
        homeAndRulesController.toggleDemoShowroomMode(enabled) {
            _selectedRoomFilter.value = null
            hardwareRulesController.dismissAllDialogs()
            householdSync.dismissAllDialogs()
        }

    fun resetToClassicScreenshotDevices() =
        homeAndRulesController.resetToClassicScreenshotDevices {
            _selectedRoomFilter.value = null
            hardwareRulesController.dismissAllDialogs()
            householdSync.dismissAllDialogs()
        }

    fun clearSoapLogs() = repository.clearLogs()

    fun dismissBanner() {
        statusBannerJob?.cancel()
        statusBannerJob = null
        _statusBannerMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        statusBannerJob?.cancel()
        householdSync.close()
        networkMonitor.stopMonitoring()
        repository.close()
        if (SwitchRepository.isRunningInRobolectric()) {
            SwitchDatabase.closeCurrentInstance()
        }
    }

    internal fun showTransientStatus(message: String) {
        statusBannerJob?.cancel()
        _statusBannerMessage.value = message
        val durationMs = if (isErrorStatusMessage(message)) 5500L else 3200L
        statusBannerJob = viewModelScope.launch {
            delay(durationMs)
            if (_statusBannerMessage.value == message) {
                _statusBannerMessage.value = null
            }
        }
    }

    companion object {
        private val ERROR_STATUS_KEYWORDS = listOf(
            "failed", "could not", "blocked", "error", "unreachable", "cannot", "rejected"
        )

        fun isErrorStatusMessage(message: String?): Boolean {
            val text = message?.trim().orEmpty()
            return text.isNotEmpty() && ERROR_STATUS_KEYWORDS.any { text.contains(it, ignoreCase = true) }
        }

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(SwitchViewModel::class.java)) {
                    val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    @Suppress("UNCHECKED_CAST")
                    return SwitchViewModel(app) as T
                }
                return ViewModelProvider.NewInstanceFactory().create(modelClass, extras)
            }
        }
    }
}
