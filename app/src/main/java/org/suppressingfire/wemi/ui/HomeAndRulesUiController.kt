package org.suppressingfire.wemi.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SwitchRepository

/**
 * Cohesive ViewModel-scoped UI controller for remembered Wi-Fi Home profiles, Demo Showroom
 * vs. Real LAN mode transitions, and 1-press Android TV scenes ([TvScene]).
 *
 * Extracted from [SwitchViewModel] (mirroring [ProvisionApUiController] and
 * [HardwareRulesUiController]) so [SwitchViewModel] focuses on reactive Room flows, SSDP
 * discovery, and per-device/per-room switch actions.
 */
internal class HomeAndRulesUiController(
    private val scope: CoroutineScope,
    private val repository: SwitchRepository,
    private val onClearRoomFilterAndSelection: () -> Unit,
    private val onShowTransientStatus: (String) -> Unit
) {
    private val _activeNetworkKey = MutableStateFlow(DEFAULT_MAIN_HOME_KEY)
    val activeNetworkKey: StateFlow<String> = _activeNetworkKey.asStateFlow()

    private val _isReconnectingHome = MutableStateFlow(false)
    val isReconnectingHome: StateFlow<Boolean> = _isReconnectingHome.asStateFlow()

    private val _showAddHomeDialog = MutableStateFlow(false)
    val showAddHomeDialog: StateFlow<Boolean> = _showAddHomeDialog.asStateFlow()

    private val reconnectLock = Any()

    @Volatile
    private var reconnectJob: Job? = null

    private fun cancelActiveReconnect() {
        val prior = synchronized(reconnectLock) {
            val existing = reconnectJob
            reconnectJob = null
            _isReconnectingHome.value = false
            existing
        }
        prior?.cancel()
    }

    fun setActiveNetworkKey(networkKey: String) {
        _activeNetworkKey.value = networkKey
    }

    fun selectHomeNetwork(networkKey: String, onResetRoomFilter: () -> Unit) {
        _activeNetworkKey.value = networkKey
        onResetRoomFilter()
        reconnectCurrentHomeNetwork()
    }

    fun reconnectCurrentHomeNetwork() {
        val targetKey = _activeNetworkKey.value
        val superseded: Job?
        val job: Job
        synchronized(reconnectLock) {
            superseded = reconnectJob
            _isReconnectingHome.value = true
            job = scope.launch(start = CoroutineStart.LAZY) {
                val self = coroutineContext[Job]
                try {
                    val summary = repository.reconnectAndRediscoverHome(targetKey)
                    if (!repository.isDemoShowroomMode.value) {
                        repository.refreshGenaSubscriptionsForHome(targetKey)
                    }
                    onShowTransientStatus(
                        "Welcome back to ${summary.homeName} (${summary.ssidLabel}): Reconnected ${summary.reconnectedCount}/${summary.totalRememberedCount} remembered switches (Avg ${summary.averageLatencyMs}ms)"
                    )
                } finally {
                    synchronized(reconnectLock) {
                        if (reconnectJob === self) {
                            reconnectJob = null
                            _isReconnectingHome.value = false
                        }
                    }
                }
            }
            reconnectJob = job
        }
        superseded?.cancel()
        job.start()
    }

    fun openAddHomeDialog() {
        _showAddHomeDialog.value = true
    }

    fun closeAddHomeDialog() {
        _showAddHomeDialog.value = false
    }

    fun addWifiHomeNetwork(
        homeName: String,
        ssidLabel: String,
        subnetPrefix: String,
        onResetRoomFilter: () -> Unit
    ) {
        _showAddHomeDialog.value = false
        scope.launch {
            val created = repository.saveOrUpdateHomeNetwork(
                existingKey = null,
                homeName = homeName,
                ssidLabel = ssidLabel,
                subnetPrefix = subnetPrefix
            )
            _activeNetworkKey.value = created.networkKey
            onResetRoomFilter()
            onShowTransientStatus(
                "Remembered Wi-Fi Home '${created.homeName}' (${created.ssidLabel} • ${created.subnetPrefix}.0/24)"
            )
        }
    }

    fun resetLanHomesSwitchesAndRooms(
        rediscoverAfterReset: Boolean,
        onTriggerNetworkRefresh: () -> Unit
    ) {
        cancelActiveReconnect()
        onClearRoomFilterAndSelection()
        scope.launch {
            val freshKey = repository.resetLanHomesSwitchesAndRooms { resolvedKey ->
                _activeNetworkKey.value = resolvedKey
            }
            _activeNetworkKey.value = freshKey
            if (rediscoverAfterReset) {
                onShowTransientStatus("Reset Wi-Fi Homes, switches & rooms • Scanning local LAN...")
                onTriggerNetworkRefresh()
            } else {
                onShowTransientStatus("Reset all Wi-Fi Homes, discovered switches, and rooms")
            }
        }
    }

    fun toggleDemoShowroomMode(
        enabled: Boolean,
        onResetRoomFilter: () -> Unit
    ) {
        cancelActiveReconnect()
        scope.launch {
            val nextKey = repository.setDemoShowroomMode(enabled) { resolvedKey ->
                _activeNetworkKey.value = resolvedKey
            }
            _activeNetworkKey.value = nextKey
            onResetRoomFilter()
            if (enabled) {
                repository.genaManager.stopServer()
                onShowTransientStatus("Demo Showroom Mode Enabled: Loaded 14 showcase devices across 3 Wi-Fi Homes")
            } else {
                reconnectCurrentHomeNetwork()
                onShowTransientStatus("Real LAN Mode Active: Scanning local Wi-Fi for Belkin's discontinued smart switch platform")
            }
        }
    }

    fun resetToClassicScreenshotDevices(onResetRoomFilter: () -> Unit) {
        cancelActiveReconnect()
        _activeNetworkKey.value = DEFAULT_MAIN_HOME_KEY
        onResetRoomFilter()
        scope.launch {
            repository.resetToClassicScreenshotPreset()
            onShowTransientStatus("Restored remembered Wi-Fi Homes and classic devices")
        }
    }

    fun applyTvScene(
        sceneKey: String,
        currentDevices: () -> List<DeviceEntity>,
        onTurnAllSwitches: (Boolean) -> Unit
    ) {
        val scene = TvScene.fromWireKey(sceneKey) ?: return
        scope.launch {
            val activeKey = _activeNetworkKey.value
            val currentHomeDevices = currentDevices().filter { it.networkKey == activeKey }
            when (scene) {
                TvScene.MOVIE_MODE -> {
                    val bulbs = currentHomeDevices.filter {
                        it.isReachable && it.parsedType == DeviceType.LIGHTBULB_DIMMER
                    }
                    coroutineScope {
                        bulbs.map { bulb ->
                            async { repository.updateDeviceBrightness(bulb, 20) }
                        }.awaitAll()
                    }
                    onShowTransientStatus("TV Movie Mode: Dimmed ${bulbs.size} smart bulb(s) to 20%")
                }
                TvScene.HOLIDAY_ON -> {
                    val holidaySwitches = currentHomeDevices.filter {
                        it.isReachable &&
                            org.suppressingfire.wemi.data.isHolidayLightDevice(it) &&
                            !it.isOn
                    }
                    coroutineScope {
                        holidaySwitches.map { sw ->
                            async { repository.setDevicePowerExplicit(sw, true) }
                        }.awaitAll()
                    }
                    onShowTransientStatus(
                        if (holidaySwitches.isNotEmpty()) {
                            "Holiday Scene: Turned ON ${holidaySwitches.size} holiday light switch(es)"
                        } else {
                            "Holiday Scene: Holiday lights are already ON"
                        }
                    )
                }
                TvScene.BEDTIME_OFF -> {
                    onTurnAllSwitches(false)
                    onShowTransientStatus("Bedtime Scene: Turned OFF all switches across all rooms")
                }
            }
        }
    }
}
