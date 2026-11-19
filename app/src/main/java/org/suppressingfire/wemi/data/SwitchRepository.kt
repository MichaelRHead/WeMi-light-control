package org.suppressingfire.wemi.data

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.suppressingfire.wemi.network.GenaEventNotification
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.ProvisionResult
import org.suppressingfire.wemi.network.ProvisionerClient
import org.suppressingfire.wemi.network.ScannedAp
import org.suppressingfire.wemi.network.SetupDeviceDescriptor
import org.suppressingfire.wemi.network.SoapCallResult
import org.suppressingfire.wemi.network.SsdpScanReport
import org.suppressingfire.wemi.network.XmlAndSkuParser

data class HomeReconnectSummary(
    val networkKey: String,
    val homeName: String,
    val ssidLabel: String,
    val reconnectedCount: Int,
    val totalRememberedCount: Int,
    val averageLatencyMs: Int
)

/**
 * Primary data and domain repository for WeMi Light Control.
 *
 * Responsibilities kept cohesive here:
 * - RTOS-safe LAN concurrency limiting (`Semaphore(3)`) and per-device `Mutex` serialization
 *   using canonical [DeviceEntity.concurrencyKey]
 * - Unified optimistic SOAP command pipeline ([executeOptimisticDeviceCommand]) with 2-stage
 *   self-healing reconnect and offline rollback
 *
 * Delegates SSDP discovery & startup probing to [DiscoveryCoordinator], UPnP GENA push handling &
 * concurrent health polling to [GenaAndHealthPollingCoordinator], `10.22.22.1` Setup AP
 * provisioning to [ProvisioningCoordinator], Room/rule CRUD & manual-IP enrichment to
 * [DeviceCrudAndRulesCoordinator], and showcase presets to [DemoFixtures].
 */
class SwitchRepository(
    private val dao: SwitchDao,
    private val context: Context? = null,
    private val networkClient: NetworkClient = NetworkClient(context),
    private val provisionerClient: ProvisionerClient = ProvisionerClient(context),
    initialDemoModeOverride: Boolean? = null
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val defaultDemoMode = initialDemoModeOverride
        ?: if (isRunningInRobolectric()) {
            true
        } else {
            prefs?.getBoolean(KEY_DEMO_SHOWROOM_MODE, false) ?: false
        }

    private val _isDemoShowroomMode = MutableStateFlow(defaultDemoMode)
    val isDemoShowroomMode: StateFlow<Boolean> = _isDemoShowroomMode.asStateFlow()

    private val initialSeedDone = MutableStateFlow(false)
    private val seedAndModeMutex = Mutex()

    /**
     * Limits concurrent HTTP/SOAP connections across the LAN to 3 so 2nd-Gen RTOS switches
     * (`WLS040` / `WLS0403` / `WDS060`) never suffer lwIP socket starvation.
     */
    private val lanSemaphore = Semaphore(3)
    private val deviceMutexes = ConcurrentHashMap<String, Mutex>()
    private val lastSetStateTimestampMs = ConcurrentHashMap<Int, Long>()

    private val _soapLogs = MutableStateFlow<List<SoapLogEntry>>(emptyList())
    val soapLogs: StateFlow<List<SoapLogEntry>> = _soapLogs.asStateFlow()

    private val _latestSsdpReport = MutableStateFlow<SsdpScanReport?>(null)
    val latestSsdpReport: StateFlow<SsdpScanReport?> = _latestSsdpReport.asStateFlow()

    private fun appendLog(entry: SoapLogEntry) {
        _soapLogs.update { (listOf(entry) + it).take(50) }
    }

    private fun nowFormatted(): String =
        SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    private fun mutexFor(device: DeviceEntity): Mutex =
        deviceMutexes.getOrPut(device.concurrencyKey()) { Mutex() }

    private val discoveryCoordinator = DiscoveryCoordinator(
        dao = dao,
        networkClient = networkClient,
        lanSemaphore = lanSemaphore,
        deviceMutexes = deviceMutexes,
        lastSetStateTimestampMs = lastSetStateTimestampMs,
        isDemoShowroomMode = { _isDemoShowroomMode.value },
        onSsdpReport = { _latestSsdpReport.value = it },
        appendLog = ::appendLog,
        nowFormatted = ::nowFormatted
    )

    private val provisioningCoordinator = ProvisioningCoordinator(
        dao = dao,
        provisionerClient = provisionerClient,
        detectCurrentLanIp = ::detectCurrentLanIp,
        isDemoShowroomMode = { _isDemoShowroomMode.value },
        appendLog = ::appendLog,
        nowFormatted = ::nowFormatted
    )

    private val crudCoordinator = DeviceCrudAndRulesCoordinator(
        dao = dao, networkClient = networkClient, lanSemaphore = lanSemaphore,
        deviceMutexes = deviceMutexes, isDemoShowroomMode = { _isDemoShowroomMode.value },
        appendLog = ::appendLog, nowFormatted = ::nowFormatted,
        setDevicePowerExplicit = ::setDevicePowerExplicit, updateDeviceBrightness = ::updateDeviceBrightness
    )

    /**
     * Embedded UPnP GENA event manager for `<100ms` physical wall-switch push notifications.
     */
    val genaManager: GenaSubscriptionManager = GenaSubscriptionManager(
        localIpProvider = { detectCurrentLanIp() },
        wifiSocketFactory = NetworkMonitor.createWifiBoundSocketFactory(context),
        onNotification = { notification ->
            repoScope.launch { handleGenaNotification(notification) }
        }
    )

    private val genaPollingCoordinator: GenaAndHealthPollingCoordinator = GenaAndHealthPollingCoordinator(
        dao = dao,
        networkClient = networkClient,
        genaManager = genaManager,
        lanSemaphore = lanSemaphore,
        deviceMutexes = deviceMutexes,
        lastSetStateTimestampMs = lastSetStateTimestampMs,
        isDemoShowroomMode = { _isDemoShowroomMode.value },
        attemptTwoStageReconnect = discoveryCoordinator::attemptTwoStageReconnect,
        appendLog = ::appendLog,
        nowFormatted = ::nowFormatted
    )

    val allDevices: Flow<List<DeviceEntity>> =
        combine(dao.getAllDevices(), initialSeedDone, _isDemoShowroomMode) { list, _, demoMode ->
            if (list.isEmpty() && demoMode) defaultClassicDevices() else list
        }

    val allHomeNetworks: Flow<List<HomeNetworkEntity>> =
        combine(dao.getAllHomeNetworks(), initialSeedDone, _isDemoShowroomMode) { list, _, demoMode ->
            if (list.isEmpty() && demoMode) defaultHomeNetworks() else list
        }

    val allRules: Flow<List<RuleEntity>> =
        combine(dao.getAllRules(), initialSeedDone, _isDemoShowroomMode) { list, _, demoMode ->
            if (list.isEmpty() && demoMode) defaultClassicRules() else list
        }

    suspend fun ensureSeeded(): String = seedAndModeMutex.withLock {
        if (_isDemoShowroomMode.value) {
            if (dao.getHomeNetworkCount() == 0) dao.insertHomeNetworks(defaultHomeNetworks())
            if (dao.getDeviceCount() == 0) dao.insertDevices(defaultClassicDevices())
            if (dao.getRuleCount() == 0) dao.insertRules(defaultClassicRules())
            initialSeedDone.value = true
            DEFAULT_MAIN_HOME_KEY
        } else {
            val activeKey = discoveryCoordinator.sanitizeAndResolveInitialLanHome(detectCurrentLanIp())
            initialSeedDone.value = true
            activeKey
        }
    }

    private fun resetRuntimeDeviceState() {
        genaManager.stopServer()
        lastSetStateTimestampMs.clear()
        deviceMutexes.clear()
        _latestSsdpReport.value = null
    }

    suspend fun setDemoShowroomMode(
        enabled: Boolean,
        onActiveKeyResolved: ((String) -> Unit)? = null
    ): String = seedAndModeMutex.withLock {
        prefs?.edit { putBoolean(KEY_DEMO_SHOWROOM_MODE, enabled) }
        if (enabled) {
            resetToClassicScreenshotPresetLocked(onActiveKeyResolved)
            DEFAULT_MAIN_HOME_KEY
        } else {
            resetRuntimeDeviceState()
            dao.deleteAllDevices()
            dao.deleteAllRules()
            dao.deleteAllHomeNetworks()
            val activeKey = discoveryCoordinator.sanitizeAndResolveInitialLanHome(detectCurrentLanIp())
            initialSeedDone.value = true
            onActiveKeyResolved?.invoke(activeKey)
            _isDemoShowroomMode.value = false
            activeKey
        }
    }

    suspend fun resetLanHomesSwitchesAndRooms(
        onActiveKeyResolved: ((String) -> Unit)? = null
    ): String = setDemoShowroomMode(false, onActiveKeyResolved)

    suspend fun resetToClassicScreenshotPreset() = seedAndModeMutex.withLock {
        resetToClassicScreenshotPresetLocked()
    }

    private suspend fun resetToClassicScreenshotPresetLocked(
        onActiveKeyResolved: ((String) -> Unit)? = null
    ) {
        resetRuntimeDeviceState()
        prefs?.edit { putBoolean(KEY_DEMO_SHOWROOM_MODE, true) }
        dao.deleteAllDevices()
        dao.deleteAllRules()
        dao.deleteAllHomeNetworks()
        dao.insertHomeNetworks(defaultHomeNetworks())
        dao.insertDevices(defaultClassicDevices())
        dao.insertRules(defaultClassicRules())
        onActiveKeyResolved?.invoke(DEFAULT_MAIN_HOME_KEY)
        _isDemoShowroomMode.value = true
    }

    fun detectCurrentLanIp(): String = networkClient.resolveLocalIpv4Address()

    suspend fun detectStartupDeviceStates(networkKey: String): List<DeviceEntity> =
        discoveryCoordinator.detectStartupDeviceStates(networkKey)

    suspend fun reconnectAndRediscoverHome(networkKey: String): HomeReconnectSummary =
        discoveryCoordinator.reconnectAndRediscoverHome(networkKey)

    suspend fun reconnectDevice(device: DeviceEntity): DeviceEntity =
        discoveryCoordinator.reconnectDevice(device)

    suspend fun pingDeviceLatency(device: DeviceEntity): DeviceEntity =
        discoveryCoordinator.reconnectDevice(device)

    suspend fun executeSsdpScan(
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT,
        autoAdoptNew: Boolean = false,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): SsdpScanReport = discoveryCoordinator.executeSsdpScan(searchTarget, autoAdoptNew, activeNetworkKey, activeWifiSsid)

    suspend fun adoptDiscoveredDevice(
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): DeviceEntity = discoveryCoordinator.adoptDiscoveredDevice(discovered, activeNetworkKey, activeWifiSsid)

    suspend fun adoptDiscoveredDevices(
        discoveredList: List<SsdpDiscoveredDevice>,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): Int = discoveryCoordinator.adoptDiscoveredDevices(discoveredList, activeNetworkKey, activeWifiSsid)

    suspend fun resolveOrCreateHomeForSubnet(subnetPrefix: String, gatewayIp: String = "$subnetPrefix.1"): HomeNetworkEntity? =
        discoveryCoordinator.resolveOrCreateHomeForSubnet(subnetPrefix, gatewayIp)

    suspend fun saveOrUpdateHomeNetwork(existingKey: String?, homeName: String, ssidLabel: String, subnetPrefix: String): HomeNetworkEntity =
        crudCoordinator.saveOrUpdateHomeNetwork(existingKey, homeName, ssidLabel, subnetPrefix)

    /**
     * Unified optimistic SOAP state-transition pipeline shared by [toggleDevicePower],
     * [setDevicePowerExplicit], and [updateDeviceBrightness]:
     * 1. Acquires the per-device [Mutex] ([DeviceEntity.concurrencyKey]) and reads the latest
     *    Room row (`baseDevice`) so rapid back-to-back toggles never invert against a stale snapshot.
     * 2. Writes optimistic state to Room and stamps [lastSetStateTimestampMs].
     * 3. Dispatches `SetBinaryState` inside [lanSemaphore] (`Semaphore(3)`), healing port/IP hops on failure.
     * 4. Commits confirmed state or reverts to `priorOn`/`priorBrightness` when offline.
     */
    private suspend fun awaitDemoSeedIfPending() {
        if (_isDemoShowroomMode.value && !initialSeedDone.value) ensureSeeded()
    }

    private suspend fun executeOptimisticDeviceCommand(
        device: DeviceEntity,
        computeTargetOn: (DeviceEntity) -> Boolean,
        computeOptimisticBrightness: (DeviceEntity, Boolean) -> Int,
        computeSoapBrightnessArg: (DeviceEntity, Boolean) -> Int?,
        optimisticStatusLabel: String? = null
    ): DeviceEntity {
        awaitDemoSeedIfPending()
        return mutexFor(device).withLock {
        val baseDevice = if (device.id != 0) dao.getDeviceById(device.id) ?: device else device
        val priorOn = baseDevice.isOn
        val priorBrightness = baseDevice.brightness
        val targetOn = computeTargetOn(baseDevice)
        val optimisticBrightness = computeOptimisticBrightness(baseDevice, targetOn)
        val soapBrightnessArg = computeSoapBrightnessArg(baseDevice, targetOn)

        dao.updateDevice(
            baseDevice.copy(
                isOn = targetOn,
                brightness = optimisticBrightness,
                lastCheckedTime = optimisticStatusLabel ?: baseDevice.lastCheckedTime
            )
        )
        lastSetStateTimestampMs[baseDevice.id] = System.currentTimeMillis()

        var activeDevice = baseDevice
        var result: SoapCallResult = lanSemaphore.withPermit {
            networkClient.setBinaryState(activeDevice, targetOn, soapBrightnessArg)
        }

        if (!result.reachableOnLan && !_isDemoShowroomMode.value) {
            discoveryCoordinator.attemptTwoStageReconnect(activeDevice)?.let { healedDevice ->
                activeDevice = healedDevice
                result = lanSemaphore.withPermit {
                    networkClient.setBinaryState(activeDevice, targetOn, soapBrightnessArg)
                }
            }
        }

        appendLog(result.logEntry)
        if (result.reachableOnLan || _isDemoShowroomMode.value) {
            lastSetStateTimestampMs[baseDevice.id] = System.currentTimeMillis()
        } else {
            lastSetStateTimestampMs.remove(baseDevice.id)
        }

        val finalEntity = when {
            result.reachableOnLan -> activeDevice.copy(
                isOn = result.binaryStateOn ?: targetOn,
                isReachable = true,
                port = result.resolvedPort,
                latencyMs = result.latencyMs.coerceAtLeast(1),
                lastCheckedTime = nowFormatted(),
                brightness = result.brightness ?: optimisticBrightness
            )
            _isDemoShowroomMode.value -> activeDevice.copy(
                isOn = targetOn,
                isReachable = true,
                port = result.resolvedPort,
                latencyMs = baseDevice.latencyMs.takeIf { it > 0 } ?: 14,
                lastCheckedTime = nowFormatted(),
                brightness = optimisticBrightness
            )
            else -> activeDevice.copy(
                isOn = priorOn,
                brightness = priorBrightness,
                isReachable = false,
                latencyMs = 0,
                lastCheckedTime = "Offline • Timeout"
            )
        }
        dao.updateDevice(finalEntity)
        finalEntity
        }
    }

    private fun shouldRestoreFullDimmer(base: DeviceEntity, nextOn: Boolean): Boolean =
        base.parsedType == DeviceType.LIGHTBULB_DIMMER && nextOn && base.brightness == 0

    suspend fun toggleDevicePower(device: DeviceEntity): DeviceEntity = executeOptimisticDeviceCommand(
        device = device,
        computeTargetOn = { !it.isOn },
        computeOptimisticBrightness = { base, nextOn -> if (shouldRestoreFullDimmer(base, nextOn)) 100 else base.brightness },
        computeSoapBrightnessArg = { base, nextOn -> if (shouldRestoreFullDimmer(base, nextOn)) 100 else null },
        optimisticStatusLabel = "Sending..."
    )

    suspend fun setDevicePowerExplicit(device: DeviceEntity, targetOn: Boolean): DeviceEntity = executeOptimisticDeviceCommand(
        device = device,
        computeTargetOn = { targetOn },
        computeOptimisticBrightness = { base, nextOn -> if (shouldRestoreFullDimmer(base, nextOn)) 100 else base.brightness },
        computeSoapBrightnessArg = { base, nextOn -> if (shouldRestoreFullDimmer(base, nextOn)) 100 else null }
    )

    suspend fun updateDeviceBrightness(device: DeviceEntity, newBrightness: Int): DeviceEntity {
        val clamped = newBrightness.coerceIn(1, 100)
        return executeOptimisticDeviceCommand(device, { true }, { _, _ -> clamped }, { _, _ -> clamped })
    }

    suspend fun stepDeviceBrightness(device: DeviceEntity, deltaPercent: Int): DeviceEntity =
        executeOptimisticDeviceCommand(
            device = device,
            computeTargetOn = { true },
            computeOptimisticBrightness = { base, _ -> (base.brightness + deltaPercent).coerceIn(10, 100) },
            computeSoapBrightnessArg = { base, _ -> (base.brightness + deltaPercent).coerceIn(10, 100) }
        )

    suspend fun toggleRoomPower(roomName: String, targetOn: Boolean, networkKey: String? = null): Int =
        awaitDemoSeedIfPending().let { crudCoordinator.toggleRoomPower(roomName, targetOn, networkKey) }

    suspend fun updateRoomBrightness(roomName: String, newBrightness: Int, networkKey: String? = null): Int =
        awaitDemoSeedIfPending().let { crudCoordinator.updateRoomBrightness(roomName, newBrightness, networkKey) }

    suspend fun triggerMotionEvent(device: DeviceEntity) =
        awaitDemoSeedIfPending().let { crudCoordinator.triggerMotionEvent(device) }

    suspend fun setDeviceReachableState(device: DeviceEntity, reachable: Boolean) =
        awaitDemoSeedIfPending().let { crudCoordinator.setDeviceReachableState(device, reachable) }

    suspend fun handleGenaNotification(event: GenaEventNotification): Unit = genaPollingCoordinator.handleGenaNotification(event)

    suspend fun refreshGenaSubscriptionsForHome(networkKey: String): Int = genaPollingCoordinator.refreshGenaSubscriptionsForHome(networkKey)

    suspend fun pollActiveHomeStates(networkKey: String): Unit = genaPollingCoordinator.pollActiveHomeStates(networkKey)

    suspend fun toggleDimmerNightMode(device: DeviceEntity): DeviceEntity =
        awaitDemoSeedIfPending().let { genaPollingCoordinator.toggleDimmerNightMode(device) }

    suspend fun turnOffDeviceById(deviceId: Int): DeviceEntity? {
        awaitDemoSeedIfPending()
        val dev = dao.getDeviceById(deviceId) ?: return null
        return if (!dev.isOn) dev else setDevicePowerExplicit(dev, false)
    }

    fun latestSetupProbeSummary(): String = provisioningCoordinator.latestProbeDiagnosticSummary()

    fun latestSetupApScanSummary(): String = provisioningCoordinator.latestApScanDiagnosticSummary()

    suspend fun probeSetupApDevice(apHost: String = "10.22.22.1", apPort: Int = 49152): SetupDeviceDescriptor? =
        provisioningCoordinator.probeSetupApDevice(apHost = apHost, apPort = apPort)

    suspend fun scanSetupApNetworks(apHost: String = "10.22.22.1", apPort: Int = 49152): List<ScannedAp> =
        provisioningCoordinator.scanSetupApNetworks(apHost = apHost, apPort = apPort)

    suspend fun provisionSetupApDevice(
        apHost: String = "10.22.22.1", apPort: Int = 49152, ssid: String, plainPassword: String,
        channel: Int = 6, authMode: String = "WPA2PSK", encryptionType: String = "AES",
        encryptionMethod: Int = 0, addPasswordLengths: Boolean? = null, customDeviceName: String? = null,
        roomName: String? = null, deviceType: DeviceType? = null, activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        maxStatusPolls: Int = 18, statusPollDelayMs: Long = 1000L, onProgress: ((String) -> Unit)? = null
    ): ProvisionResult = provisioningCoordinator.provisionSetupApDevice(
        apHost, apPort, ssid, plainPassword, channel, authMode, encryptionType,
        encryptionMethod, addPasswordLengths, customDeviceName, roomName, deviceType,
        activeNetworkKey, maxStatusPolls, statusPollDelayMs, onProgress
    )

    suspend fun saveDevice(device: DeviceEntity, syncFriendlyNameToHardware: Boolean = false): DeviceEntity =
        awaitDemoSeedIfPending().let { crudCoordinator.saveDevice(device, syncFriendlyNameToHardware) }

    suspend fun deleteDevice(id: Int) {
        awaitDemoSeedIfPending()
        val existing = dao.getDeviceById(id)
        crudCoordinator.deleteDevice(id)
        lastSetStateTimestampMs.remove(id)
        if (existing != null) {
            deviceMutexes.remove(existing.concurrencyKey())
            if (!_isDemoShowroomMode.value) genaManager.unsubscribeDevice(existing)
        }
    }

    suspend fun moveDeviceOrder(device: DeviceEntity, moveUp: Boolean, withinRoomOnly: Boolean = false) =
        awaitDemoSeedIfPending().let { crudCoordinator.moveDeviceOrder(device, moveUp, withinRoomOnly) }

    suspend fun toggleRule(rule: RuleEntity) = awaitDemoSeedIfPending().let { crudCoordinator.toggleRule(rule) }

    suspend fun runRuleNow(rule: RuleEntity) = awaitDemoSeedIfPending().let { crudCoordinator.runRuleNow(rule) }

    suspend fun addRule(rule: RuleEntity) = awaitDemoSeedIfPending().let { crudCoordinator.addRule(rule) }

    suspend fun deleteRule(id: Int) = awaitDemoSeedIfPending().let { crudCoordinator.deleteRule(id) }

    fun clearLogs() {
        _soapLogs.value = emptyList()
    }

    fun close() {
        genaManager.close()
        repoScope.cancel()
    }

    companion object {
        private const val PREFS_NAME = "wemi_controller_prefs"
        private const val KEY_DEMO_SHOWROOM_MODE = "demo_showroom_mode"

        fun isRunningInRobolectric(): Boolean = LanRuntimeUtils.isRunningInRobolectric()

        val DEFAULT_ROOMS: List<String> = DemoFixtures.DEFAULT_ROOMS

        fun defaultHomeNetworks(): List<HomeNetworkEntity> = DemoFixtures.defaultHomeNetworks()

        fun defaultClassicDevices(): List<DeviceEntity> = DemoFixtures.defaultClassicDevices()

        fun defaultClassicRules(): List<RuleEntity> = DemoFixtures.defaultClassicRules()
    }
}
