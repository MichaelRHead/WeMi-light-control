package com.example.data

import android.content.Context
import android.os.Build
import com.example.network.GenaEventNotification
import com.example.network.SoapCallResult
import com.example.network.SsdpScanReport
import com.example.network.WeMoGenaSubscriptionManager
import com.example.network.WeMoNetworkClient
import com.example.network.WeMoProvisionResult
import com.example.network.WeMoProvisionerClient
import com.example.network.WeMoScannedAp
import com.example.network.WeMoSetupDeviceDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
 * - Unified optimistic SOAP command pipeline ([executeOptimisticDeviceCommand]) with 2-stage
 *   self-healing reconnect and offline rollback
 * - UPnP GENA push notification handling (`1200ms` echo-suppression window) and 30-second fallback polling
 * - Room v5 device, home-network, and rule CRUD
 *
 * Delegates SSDP discovery & startup probing to [WeMoDiscoveryCoordinator], `10.22.22.1` Setup AP
 * provisioning to [WeMoProvisioningCoordinator], and showcase presets to [WeMoDemoFixtures].
 */
class WeMoRepository(
    private val dao: WeMoDao,
    private val context: Context? = null,
    private val networkClient: WeMoNetworkClient = WeMoNetworkClient(context),
    private val provisionerClient: WeMoProvisionerClient = WeMoProvisionerClient(context),
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

    /**
     * Limits concurrent HTTP/SOAP connections across the LAN to 3 so 2nd-Gen Belkin RTOS
     * switches (WLS040 / WLS0403 / WDS060) never suffer lwIP socket starvation.
     */
    private val lanSemaphore = Semaphore(3)
    private val deviceMutexes = ConcurrentHashMap<String, Mutex>()
    private val lastSetStateTimestampMs = ConcurrentHashMap<Int, Long>()

    private val _soapLogs = MutableStateFlow<List<SoapLogEntry>>(emptyList())
    val soapLogs: StateFlow<List<SoapLogEntry>> = _soapLogs.asStateFlow()

    private val _latestSsdpReport = MutableStateFlow<SsdpScanReport?>(null)
    val latestSsdpReport: StateFlow<SsdpScanReport?> = _latestSsdpReport.asStateFlow()

    private fun appendLog(entry: SoapLogEntry) {
        _soapLogs.value = (listOf(entry) + _soapLogs.value).take(50)
    }

    private fun nowFormatted(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
    }

    private fun mutexFor(device: WeMoDeviceEntity): Mutex {
        val key = device.serialNumber.takeIf { it.isNotBlank() } ?: "${device.ipAddress}:${device.port}"
        return deviceMutexes.getOrPut(key) { Mutex() }
    }

    private val discoveryCoordinator = WeMoDiscoveryCoordinator(
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

    private val provisioningCoordinator = WeMoProvisioningCoordinator(
        dao = dao,
        provisionerClient = provisionerClient,
        detectCurrentLanIp = ::detectCurrentLanIp,
        isDemoShowroomMode = { _isDemoShowroomMode.value },
        appendLog = ::appendLog,
        nowFormatted = ::nowFormatted
    )

    /**
     * Embedded UPnP GENA event manager for <100ms physical wall-switch push notifications.
     */
    val genaManager = WeMoGenaSubscriptionManager(
        localIpProvider = { detectCurrentLanIp() },
        onNotification = { notification ->
            repoScope.launch {
                handleGenaNotification(notification)
            }
        }
    )

    val allDevices: Flow<List<WeMoDeviceEntity>> = combine(
        dao.getAllDevices(),
        initialSeedDone,
        _isDemoShowroomMode
    ) { list, _, demoMode ->
        if (list.isEmpty() && demoMode) defaultClassicDevices() else list
    }

    val allHomeNetworks: Flow<List<WeMoHomeNetworkEntity>> = combine(
        dao.getAllHomeNetworks(),
        initialSeedDone,
        _isDemoShowroomMode
    ) { list, _, demoMode ->
        if (list.isEmpty() && demoMode) defaultHomeNetworks() else list
    }

    val allRules: Flow<List<WeMoRuleEntity>> = combine(
        dao.getAllRules(),
        initialSeedDone,
        _isDemoShowroomMode
    ) { list, _, demoMode ->
        if (list.isEmpty() && demoMode) defaultClassicRules() else list
    }

    /**
     * Initializes the database on app startup:
     * - In Real LAN Mode (default on physical Android phones/TVs), starts clean on the
     *   detected Wi-Fi subnet without seeding fake demo switches, creating a profile for
     *   the active LAN subnet if none exists.
     * - In Demo Showroom Mode, seeds the 3 showcase homes, 14 devices, and 4 rules.
     */
    suspend fun ensureSeeded(): String {
        return if (_isDemoShowroomMode.value) {
            if (dao.getHomeNetworkCount() == 0) {
                dao.insertHomeNetworks(defaultHomeNetworks())
            }
            if (dao.getDeviceCount() == 0) {
                dao.insertDevices(defaultClassicDevices())
            }
            if (dao.getRuleCount() == 0) {
                dao.insertRules(defaultClassicRules())
            }
            initialSeedDone.value = true
            DEFAULT_MAIN_HOME_KEY
        } else {
            val localIp = detectCurrentLanIp()
            val subnetPrefix = localIp.substringBeforeLast('.', "192.168.1")

            // Sanitize any existing home networks or devices whose names/SSIDs were previously
            // polluted by Belkin GetMetaInfo soft-AP SSIDs (e.g. "WeMo.Switch.xxx Home")
            val rawHomes = dao.getAllHomeNetworks().first()
            val existingHomes = rawHomes.mapIndexed { idx, home ->
                val pollutedName = WeMoDiscoveryCoordinator.isWemoDeviceSetupSsid(home.homeName) ||
                    home.homeName.startsWith("Home Wi-Fi (", ignoreCase = true)
                val pollutedSsid = WeMoDiscoveryCoordinator.isWemoDeviceSetupSsid(home.ssidLabel)
                if (pollutedName || pollutedSsid) {
                    val repaired = home.copy(
                        homeName = if (pollutedName) {
                            if (idx == 0) "My Home" else "Home (${home.subnetPrefix}.x)"
                        } else {
                            home.homeName
                        },
                        ssidLabel = if (pollutedSsid) "LAN ${home.subnetPrefix}.0/24" else home.ssidLabel
                    )
                    dao.updateHomeNetwork(repaired)
                    repaired
                } else {
                    home
                }
            }

            val rawDevices = dao.getAllDevices().first()
            rawDevices.forEach { dev ->
                val cleanSsid = if (WeMoDiscoveryCoordinator.isWemoDeviceSetupSsid(dev.wifiSsid)) {
                    "LAN $subnetPrefix.0/24"
                } else {
                    dev.wifiSsid
                }
                val inferredRoom = inferRoomFromName(dev.name)
                val cleanRoom = if (dev.roomName.isBlank() ||
                    dev.roomName.equals("Unassigned", ignoreCase = true) ||
                    (dev.roomName.equals("Living Room", ignoreCase = true) && inferredRoom != "Living Room")
                ) {
                    inferredRoom
                } else {
                    dev.roomName
                }
                if (cleanSsid != dev.wifiSsid || cleanRoom != dev.roomName) {
                    dao.updateDevice(dev.copy(wifiSsid = cleanSsid, roomName = cleanRoom))
                }
            }

            val matchedHome = existingHomes.find { it.subnetPrefix == subnetPrefix }
            val activeHome = when {
                matchedHome != null -> matchedHome
                existingHomes.isNotEmpty() -> existingHomes.first()
                else -> {
                    val key = "wifi_lan_${subnetPrefix.replace('.', '_')}"
                    val newHome = WeMoHomeNetworkEntity(
                        networkKey = key,
                        homeName = "My Home",
                        ssidLabel = "LAN $subnetPrefix.0/24",
                        subnetPrefix = subnetPrefix,
                        gatewayIp = "$subnetPrefix.1",
                        lastConnectedTime = "Connected now",
                        autoReconnectOnArrival = true,
                        sortOrder = 0
                    )
                    dao.insertHomeNetwork(newHome)
                    newHome
                }
            }
            initialSeedDone.value = true
            activeHome.networkKey
        }
    }

    suspend fun setDemoShowroomMode(enabled: Boolean): String {
        _isDemoShowroomMode.value = enabled
        prefs?.edit()?.putBoolean(KEY_DEMO_SHOWROOM_MODE, enabled)?.apply()
        return if (enabled) {
            resetToClassicScreenshotPreset()
            DEFAULT_MAIN_HOME_KEY
        } else {
            dao.deleteAllDevices()
            dao.deleteAllRules()
            dao.deleteAllHomeNetworks()
            ensureSeeded()
        }
    }

    /**
     * Clears all remembered Wi-Fi Homes, discovered switches, room groupings, and rules in
     * Real LAN Mode, and re-initializes a single clean "My Home" profile on the active subnet.
     */
    suspend fun resetLanHomesSwitchesAndRooms(): String {
        _isDemoShowroomMode.value = false
        prefs?.edit()?.putBoolean(KEY_DEMO_SHOWROOM_MODE, false)?.apply()
        dao.deleteAllDevices()
        dao.deleteAllRules()
        dao.deleteAllHomeNetworks()
        return ensureSeeded()
    }

    suspend fun resetToClassicScreenshotPreset() {
        _isDemoShowroomMode.value = true
        prefs?.edit()?.putBoolean(KEY_DEMO_SHOWROOM_MODE, true)?.apply()
        dao.deleteAllDevices()
        dao.deleteAllRules()
        dao.deleteAllHomeNetworks()
        dao.insertHomeNetworks(defaultHomeNetworks())
        dao.insertDevices(defaultClassicDevices())
        dao.insertRules(defaultClassicRules())
    }

    fun detectCurrentLanIp(): String {
        return networkClient.resolveLocalIpv4Address()
    }

    suspend fun detectStartupDeviceStates(networkKey: String): List<WeMoDeviceEntity> {
        return discoveryCoordinator.detectStartupDeviceStates(networkKey)
    }

    suspend fun reconnectAndRediscoverHome(networkKey: String): HomeReconnectSummary {
        return discoveryCoordinator.reconnectAndRediscoverHome(networkKey)
    }

    suspend fun reconnectDevice(device: WeMoDeviceEntity): WeMoDeviceEntity {
        return discoveryCoordinator.reconnectDevice(device)
    }

    suspend fun pingDeviceLatency(device: WeMoDeviceEntity): WeMoDeviceEntity {
        return discoveryCoordinator.reconnectDevice(device)
    }

    suspend fun executeSsdpScan(
        searchTarget: String = "urn:Belkin:service:basicevent:1",
        autoAdoptNew: Boolean = false,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): SsdpScanReport {
        return discoveryCoordinator.executeSsdpScan(
            searchTarget = searchTarget,
            autoAdoptNew = autoAdoptNew,
            activeNetworkKey = activeNetworkKey,
            activeWifiSsid = activeWifiSsid
        )
    }

    suspend fun adoptDiscoveredDevice(
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): WeMoDeviceEntity {
        return discoveryCoordinator.adoptDiscoveredDevice(
            discovered = discovered,
            activeNetworkKey = activeNetworkKey,
            activeWifiSsid = activeWifiSsid
        )
    }

    suspend fun adoptDiscoveredDevices(
        discoveredList: List<SsdpDiscoveredDevice>,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): Int {
        return discoveryCoordinator.adoptDiscoveredDevices(
            discoveredList = discoveredList,
            activeNetworkKey = activeNetworkKey,
            activeWifiSsid = activeWifiSsid
        )
    }

    suspend fun resolveOrCreateHomeForSubnet(
        subnetPrefix: String,
        gatewayIp: String = "$subnetPrefix.1"
    ): WeMoHomeNetworkEntity? {
        return discoveryCoordinator.resolveOrCreateHomeForSubnet(
            subnetPrefix = subnetPrefix,
            gatewayIp = gatewayIp
        )
    }

    suspend fun saveOrUpdateHomeNetwork(
        existingKey: String?,
        homeName: String,
        ssidLabel: String,
        subnetPrefix: String
    ): WeMoHomeNetworkEntity {
        val cleanHome = homeName.trim().ifEmpty { "My Home Wi-Fi" }
        val cleanSsid = ssidLabel.trim().ifEmpty { "Home-WiFi-5G" }
        val cleanSubnet = subnetPrefix.trim()
            .removeSuffix(".0/24")
            .removeSuffix(".x")
            .ifEmpty { "192.168.1" }
        val key = existingKey ?: "wifi_custom_${cleanSubnet.replace('.', '_')}_${System.currentTimeMillis() % 10000}"
        val currentNetworks = dao.getAllHomeNetworks().first()
        val entity = WeMoHomeNetworkEntity(
            networkKey = key,
            homeName = cleanHome,
            ssidLabel = cleanSsid,
            subnetPrefix = cleanSubnet,
            gatewayIp = "$cleanSubnet.1",
            lastConnectedTime = "Connected now",
            autoReconnectOnArrival = true,
            sortOrder = currentNetworks.size
        )
        dao.insertHomeNetwork(entity)
        return entity
    }

    /**
     * Unified optimistic SOAP state-transition pipeline shared by [toggleDevicePower],
     * [setDevicePowerExplicit], and [updateDeviceBrightness]:
     * 1. Acquires the per-device [Mutex] so rapid taps on the same switch execute in order.
     * 2. Writes the optimistic state (`targetOn`, `optimisticBrightness`) to Room and stamps
     *    [lastSetStateTimestampMs] before dispatching the SOAP command.
     * 3. Dispatches `SetBinaryState` inside [lanSemaphore] (`Semaphore(3)`).
     * 4. If unreachable in Real LAN Mode, triggers [WeMoDiscoveryCoordinator.attemptTwoStageReconnect]
     *    (9-port probe + targeted SSDP scan by serialNumber) and retries once.
     * 5. Stamps [lastSetStateTimestampMs] on completion and commits the confirmed state — or reverts
     *    to `priorOn`/`priorBrightness` with `isReachable = false, latencyMs = 0` when offline.
     */
    private suspend fun executeOptimisticDeviceCommand(
        device: WeMoDeviceEntity,
        targetOn: Boolean,
        optimisticBrightness: Int,
        soapBrightnessArg: Int?,
        optimisticStatusLabel: String? = null
    ): WeMoDeviceEntity {
        val priorOn = device.isOn
        val priorBrightness = device.brightness

        return mutexFor(device).withLock {
            val optimistic = device.copy(
                isOn = targetOn,
                brightness = optimisticBrightness,
                lastCheckedTime = optimisticStatusLabel ?: device.lastCheckedTime
            )
            dao.updateDevice(optimistic)
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

            var activeDevice = device
            var result: SoapCallResult = lanSemaphore.withPermit {
                networkClient.setBinaryState(
                    device = activeDevice,
                    targetOn = targetOn,
                    targetBrightness = soapBrightnessArg
                )
            }

            if (!result.reachableOnLan && !_isDemoShowroomMode.value) {
                val healedDevice = discoveryCoordinator.attemptTwoStageReconnect(activeDevice)
                if (healedDevice != null) {
                    activeDevice = healedDevice
                    result = lanSemaphore.withPermit {
                        networkClient.setBinaryState(
                            device = activeDevice,
                            targetOn = targetOn,
                            targetBrightness = soapBrightnessArg
                        )
                    }
                }
            }

            appendLog(result.logEntry)
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

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
                    latencyMs = device.latencyMs.takeIf { it > 0 } ?: 14,
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

    suspend fun toggleDevicePower(device: WeMoDeviceEntity): WeMoDeviceEntity {
        val nextOn = !device.isOn
        val restoreFullDimmer = device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && nextOn && device.brightness == 0
        val nextBrightness = if (restoreFullDimmer) 100 else device.brightness
        val soapBrightnessArg = if (restoreFullDimmer) 100 else null

        return executeOptimisticDeviceCommand(
            device = device,
            targetOn = nextOn,
            optimisticBrightness = nextBrightness,
            soapBrightnessArg = soapBrightnessArg,
            optimisticStatusLabel = "Sending..."
        )
    }

    private suspend fun setDevicePowerExplicit(
        device: WeMoDeviceEntity,
        targetOn: Boolean
    ): WeMoDeviceEntity {
        val restoreFullDimmer = device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && targetOn && device.brightness == 0
        val nextBrightness = if (restoreFullDimmer) 100 else device.brightness
        val soapBrightnessArg = if (restoreFullDimmer) 100 else null

        return executeOptimisticDeviceCommand(
            device = device,
            targetOn = targetOn,
            optimisticBrightness = nextBrightness,
            soapBrightnessArg = soapBrightnessArg
        )
    }

    suspend fun updateDeviceBrightness(device: WeMoDeviceEntity, newBrightness: Int): WeMoDeviceEntity {
        val clamped = newBrightness.coerceIn(1, 100)
        return executeOptimisticDeviceCommand(
            device = device,
            targetOn = true,
            optimisticBrightness = clamped,
            soapBrightnessArg = clamped
        )
    }

    suspend fun toggleRoomPower(
        roomName: String,
        targetOn: Boolean,
        networkKey: String? = null
    ): Int {
        val demoMode = _isDemoShowroomMode.value
        val currentDevices = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) defaultClassicDevices() else it
        }
        val roomSwitches = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.roomName.equals(roomName, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType != WeMoDeviceType.MOTION_SENSOR
        }
        var succeeded = 0
        for (dev in roomSwitches) {
            val updated = setDevicePowerExplicit(dev, targetOn)
            if (updated.isReachable) {
                succeeded++
            }
        }
        return succeeded
    }

    suspend fun updateRoomBrightness(
        roomName: String,
        newBrightness: Int,
        networkKey: String? = null
    ): Int {
        val clamped = newBrightness.coerceIn(1, 100)
        val demoMode = _isDemoShowroomMode.value
        val currentDevices = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) defaultClassicDevices() else it
        }
        val roomBulbs = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.roomName.equals(roomName, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER
        }
        roomBulbs.forEach { bulb ->
            updateDeviceBrightness(bulb, clamped)
        }
        return roomBulbs.size
    }

    suspend fun triggerMotionEvent(device: WeMoDeviceEntity) {
        val now = nowFormatted()
        val updated = device.copy(
            lastMotionTime = now,
            lastCheckedTime = now,
            latencyMs = device.latencyMs.takeIf { it > 0 } ?: 15,
            isReachable = true
        )
        dao.updateDevice(updated)

        val rules = dao.getAllRules().first()
        rules.filter { it.isEnabled && it.ruleType == "MOTION_TRIGGER" }.forEach { rule ->
            val target = dao.getDeviceById(rule.targetDeviceId)
            if (target != null) {
                setDevicePowerExplicit(target, rule.targetStateOn)
            }
        }
    }

    suspend fun setDeviceReachableState(device: WeMoDeviceEntity, reachable: Boolean) {
        dao.updateDevice(
            device.copy(
                isReachable = reachable,
                latencyMs = if (reachable) (device.latencyMs.takeIf { it > 0 } ?: 14) else 0,
                lastCheckedTime = nowFormatted()
            )
        )
    }

    /**
     * Handles incoming UPnP GENA `<e:propertyset>` push events from physical wall switches
     * and 3-way traveler rockers, updating Room state immediately (`<100ms`).
     */
    suspend fun handleGenaNotification(event: GenaEventNotification) {
        appendLog(event.logEntry)
        val devices = dao.getAllDevices().first()
        val matched = devices.find {
            (event.deviceKey.isNotBlank() && it.serialNumber.equals(event.deviceKey, ignoreCase = true)) ||
                event.deviceKey == "dev_${it.id}" ||
                (event.sourceIp.isNotBlank() && it.ipAddress == event.sourceIp)
        } ?: return

        val recentSetMs = lastSetStateTimestampMs[matched.id] ?: 0L
        if ((System.currentTimeMillis() - recentSetMs) < 1200L) {
            return
        }

        val updated = matched.copy(
            isOn = event.binaryStateOn ?: matched.isOn,
            brightness = event.brightness?.coerceIn(1, 100) ?: matched.brightness,
            isReachable = true,
            lastCheckedTime = "GENA Push • ${nowFormatted()}"
        )
        dao.updateDevice(updated)
    }

    suspend fun refreshGenaSubscriptionsForHome(networkKey: String): Int {
        if (_isDemoShowroomMode.value) return 0
        val devices = dao.getAllDevices().first().filter {
            it.networkKey == networkKey && it.isReachable && it.parsedType != WeMoDeviceType.MOTION_SENSOR
        }
        var subscribedCount = 0
        for (dev in devices) {
            val rec = lanSemaphore.withPermit {
                genaManager.subscribeOrRenew(dev, timeoutSeconds = 300)
            }
            if (rec != null) {
                subscribedCount++
            }
        }
        return subscribedCount
    }

    suspend fun pollActiveHomeStates(networkKey: String) {
        if (_isDemoShowroomMode.value) return
        val devices = dao.getAllDevices().first().filter { it.networkKey == networkKey }
        if (devices.isEmpty()) return
        val checkedAt = nowFormatted()
        for (dev in devices) {
            val recentSetMs = lastSetStateTimestampMs[dev.id] ?: 0L
            if ((System.currentTimeMillis() - recentSetMs) < 2000L) continue

            val probe = lanSemaphore.withPermit {
                networkClient.getBinaryState(dev)
            }
            if (probe.reachableOnLan) {
                dao.updateDevice(
                    dev.copy(
                        port = probe.resolvedPort,
                        isReachable = true,
                        isOn = probe.binaryStateOn ?: dev.isOn,
                        brightness = probe.brightness ?: dev.brightness,
                        latencyMs = probe.latencyMs.coerceAtLeast(1),
                        lastCheckedTime = checkedAt
                    )
                )
            } else {
                dao.updateDevice(
                    dev.copy(
                        isReachable = false,
                        latencyMs = 0,
                        lastCheckedTime = "Offline • Timeout"
                    )
                )
            }
        }
    }

    suspend fun toggleDimmerNightMode(device: WeMoDeviceEntity): WeMoDeviceEntity {
        val targetNightMode = !device.nightModeEnabled
        val nextBrightness = if (targetNightMode && device.brightness > 20) 20 else device.brightness

        return mutexFor(device).withLock {
            val result = lanSemaphore.withPermit {
                networkClient.configureNightMode(
                    device = device,
                    enabled = targetNightMode,
                    nightBrightness = 20
                )
            }
            appendLog(result.logEntry)
            val updated = device.copy(
                nightModeEnabled = targetNightMode,
                brightness = nextBrightness,
                lastCheckedTime = nowFormatted()
            )
            dao.updateDevice(updated)
            updated
        }
    }

    suspend fun turnOffDeviceById(deviceId: Int): WeMoDeviceEntity? {
        val dev = dao.getDeviceById(deviceId) ?: return null
        if (!dev.isOn) return dev
        return setDevicePowerExplicit(dev, false)
    }

    fun latestSetupProbeSummary(): String =
        provisioningCoordinator.latestProbeDiagnosticSummary()

    fun latestSetupApScanSummary(): String =
        provisioningCoordinator.latestApScanDiagnosticSummary()

    suspend fun probeSetupApDevice(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152
    ): WeMoSetupDeviceDescriptor? {
        return provisioningCoordinator.probeSetupApDevice(apHost = apHost, apPort = apPort)
    }

    suspend fun scanSetupApNetworks(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152
    ): List<WeMoScannedAp> {
        return provisioningCoordinator.scanSetupApNetworks(apHost = apHost, apPort = apPort)
    }

    suspend fun provisionSetupApDevice(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152,
        ssid: String,
        plainPassword: String,
        channel: Int = 6,
        authMode: String = "WPA2PSK",
        encryptionType: String = "AES",
        encryptionMethod: Int = 0,
        addPasswordLengths: Boolean? = null,
        customDeviceName: String? = null,
        roomName: String? = null,
        deviceType: WeMoDeviceType? = null,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        maxStatusPolls: Int = 18,
        statusPollDelayMs: Long = 1000L,
        onProgress: ((String) -> Unit)? = null
    ): WeMoProvisionResult {
        return provisioningCoordinator.provisionSetupApDevice(
            apHost = apHost,
            apPort = apPort,
            ssid = ssid,
            plainPassword = plainPassword,
            channel = channel,
            authMode = authMode,
            encryptionType = encryptionType,
            encryptionMethod = encryptionMethod,
            addPasswordLengths = addPasswordLengths,
            customDeviceName = customDeviceName,
            roomName = roomName,
            deviceType = deviceType,
            activeNetworkKey = activeNetworkKey,
            maxStatusPolls = maxStatusPolls,
            statusPollDelayMs = statusPollDelayMs,
            onProgress = onProgress
        )
    }

    suspend fun saveDevice(
        device: WeMoDeviceEntity,
        syncFriendlyNameToHardware: Boolean = false
    ): WeMoDeviceEntity {
        val isPlaceholderSerial = device.serialNumber.isBlank() ||
            device.serialNumber == "221517K01018NEW" ||
            device.serialNumber == "221517K01018AB"

        val liveDesc = if (device.id == 0 || isPlaceholderSerial) {
            lanSemaphore.withPermit {
                networkClient.probeAndFetchDeviceDescriptor(
                    ipAddress = device.ipAddress,
                    preferredPort = device.port
                )
            }
        } else {
            null
        }

        val enriched = if (liveDesc != null) {
            val resolvedName = device.name.trim().ifBlank { liveDesc.name }
            val resolvedRoom = if (device.name.isBlank() &&
                (device.roomName == "Living Room" || device.roomName == "Unassigned")
            ) {
                liveDesc.roomName
            } else {
                device.roomName.ifBlank { liveDesc.roomName }
            }
            val resolvedType = if (device.name.isBlank()) {
                liveDesc.deviceType.name
            } else {
                device.deviceType
            }
            device.copy(
                name = resolvedName,
                roomName = resolvedRoom,
                deviceType = resolvedType,
                port = liveDesc.port,
                isReachable = true,
                isOn = liveDesc.isOn,
                brightness = liveDesc.brightness,
                latencyMs = liveDesc.responseTimeMs.toInt().coerceAtLeast(1),
                lastCheckedTime = nowFormatted(),
                serialNumber = liveDesc.serialNumber.ifBlank { device.serialNumber },
                firmwareVersion = liveDesc.firmwareVersion.ifBlank { device.firmwareVersion },
                hardwareModel = liveDesc.hardwareSku.ifBlank { device.hardwareModel },
                macAddress = liveDesc.macAddress.ifBlank { device.macAddress },
                udn = liveDesc.usn.ifBlank { device.udn },
                wifiSsid = liveDesc.wifiSsid.ifBlank { device.wifiSsid },
                signalStrengthPercent = liveDesc.signalStrengthPercent
            )
        } else if (isPlaceholderSerial && !_isDemoShowroomMode.value) {
            device.copy(serialNumber = "")
        } else {
            device
        }

        val saved = if (enriched.id == 0) {
            val count = dao.getDeviceCount()
            val toInsert = enriched.copy(sortOrder = count)
            val newId = dao.insertDevice(toInsert).toInt()
            toInsert.copy(id = newId)
        } else {
            dao.updateDevice(enriched)
            enriched
        }

        if (syncFriendlyNameToHardware) {
            val renameResult = lanSemaphore.withPermit {
                networkClient.changeFriendlyName(saved, saved.name)
            }
            appendLog(renameResult.logEntry)
        }
        return saved
    }

    suspend fun deleteDevice(id: Int) {
        dao.deleteDeviceById(id)
    }

    suspend fun moveDeviceOrder(device: WeMoDeviceEntity, moveUp: Boolean) {
        val list = dao.getAllDevices().first()
        val index = list.indexOfFirst { it.id == device.id }
        if (index == -1) return
        val swapIndex = if (moveUp) index - 1 else index + 1
        if (swapIndex !in list.indices) return
        val current = list[index]
        val neighbor = list[swapIndex]
        dao.updateDevice(current.copy(sortOrder = swapIndex))
        dao.updateDevice(neighbor.copy(sortOrder = index))
    }

    suspend fun toggleRule(rule: WeMoRuleEntity) {
        dao.updateRule(rule.copy(isEnabled = !rule.isEnabled))
    }

    suspend fun runRuleNow(rule: WeMoRuleEntity) {
        val devices = dao.getAllDevices().first()
        val target = devices.find { it.id == rule.targetDeviceId }
            ?: devices.find { it.name.equals(rule.targetDeviceName, ignoreCase = true) }
        if (target != null) {
            if (target.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && rule.targetStateOn) {
                updateDeviceBrightness(target, rule.targetBrightness)
            } else {
                setDevicePowerExplicit(target, rule.targetStateOn)
            }
        }
    }

    suspend fun addRule(rule: WeMoRuleEntity) {
        dao.insertRule(rule)
    }

    suspend fun deleteRule(id: Int) {
        dao.deleteRuleById(id)
    }

    fun clearLogs() {
        _soapLogs.value = emptyList()
    }

    companion object {
        private const val PREFS_NAME = "wemi_controller_prefs"
        private const val KEY_DEMO_SHOWROOM_MODE = "demo_showroom_mode"

        fun isRunningInRobolectric(): Boolean {
            return Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
                runCatching { Class.forName("org.robolectric.Robolectric"); true }.getOrDefault(false)
        }

        val DEFAULT_ROOMS: List<String> = WeMoDemoFixtures.DEFAULT_ROOMS

        fun defaultHomeNetworks(): List<WeMoHomeNetworkEntity> =
            WeMoDemoFixtures.defaultHomeNetworks()

        fun defaultClassicDevices(): List<WeMoDeviceEntity> =
            WeMoDemoFixtures.defaultClassicDevices()

        fun defaultClassicRules(): List<WeMoRuleEntity> =
            WeMoDemoFixtures.defaultClassicRules()
    }
}
