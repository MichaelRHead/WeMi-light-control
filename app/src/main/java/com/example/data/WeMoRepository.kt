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
import kotlin.math.roundToInt

data class HomeReconnectSummary(
    val networkKey: String,
    val homeName: String,
    val ssidLabel: String,
    val reconnectedCount: Int,
    val totalRememberedCount: Int,
    val averageLatencyMs: Int
)

class WeMoRepository(
    private val dao: WeMoDao,
    private val context: Context? = null,
    private val networkClient: WeMoNetworkClient = WeMoNetworkClient(context),
    private val provisionerClient: WeMoProvisionerClient = WeMoProvisionerClient(),
    initialDemoModeOverride: Boolean? = null
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val defaultDemoMode = initialDemoModeOverride
        ?: prefs?.getBoolean(KEY_DEMO_SHOWROOM_MODE, isRunningInRobolectric())
        ?: isRunningInRobolectric()

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

    private fun mutexFor(device: WeMoDeviceEntity): Mutex {
        val key = device.serialNumber.takeIf { it.isNotBlank() } ?: "${device.ipAddress}:${device.port}"
        return deviceMutexes.getOrPut(key) { Mutex() }
    }

    val allDevices: Flow<List<WeMoDeviceEntity>> = combine(
        dao.getAllDevices(),
        initialSeedDone,
        _isDemoShowroomMode
    ) { list, seeded, demoMode ->
        if (list.isEmpty() && !seeded && demoMode) defaultClassicDevices() else list
    }

    val allHomeNetworks: Flow<List<WeMoHomeNetworkEntity>> = combine(
        dao.getAllHomeNetworks(),
        initialSeedDone,
        _isDemoShowroomMode
    ) { list, seeded, demoMode ->
        if (list.isEmpty() && !seeded && demoMode) defaultHomeNetworks() else list
    }

    val allRules: Flow<List<WeMoRuleEntity>> = combine(
        dao.getAllRules(),
        initialSeedDone,
        _isDemoShowroomMode
    ) { list, seeded, demoMode ->
        if (list.isEmpty() && !seeded && demoMode) defaultClassicRules() else list
    }

    private val _soapLogs = MutableStateFlow<List<SoapLogEntry>>(emptyList())
    val soapLogs: StateFlow<List<SoapLogEntry>> = _soapLogs.asStateFlow()

    private val _latestSsdpReport = MutableStateFlow<SsdpScanReport?>(null)
    val latestSsdpReport: StateFlow<SsdpScanReport?> = _latestSsdpReport.asStateFlow()

    private fun appendLog(entry: SoapLogEntry) {
        _soapLogs.value = (listOf(entry) + _soapLogs.value).take(50)
    }

    /**
     * Initializes the database on app startup:
     * - In Real LAN Mode (default on physical Android phones/TVs), starts clean on the
     *   detected Wi-Fi subnet without seeding fake demo switches, creating a profile for
     *   the active LAN subnet if none exists.
     * - In Demo Showroom Mode, seeds the 3 showcase homes, 13 devices, and 4 rules.
     *
     * Returns the initial active networkKey to select.
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
            val existingHomes = dao.getAllHomeNetworks().first()
            val localIp = detectCurrentLanIp()
            val subnetPrefix = localIp.substringBeforeLast('.', "192.168.1")
            val matchedHome = existingHomes.find { it.subnetPrefix == subnetPrefix }
            val activeHome = when {
                matchedHome != null -> matchedHome
                existingHomes.isNotEmpty() -> existingHomes.first()
                else -> {
                    val key = "wifi_lan_${subnetPrefix.replace('.', '_')}"
                    val newHome = WeMoHomeNetworkEntity(
                        networkKey = key,
                        homeName = "Home Wi-Fi ($subnetPrefix.x)",
                        ssidLabel = "Local LAN ($subnetPrefix.0/24)",
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

    /**
     * Toggles Demo Showroom Mode ON or OFF and persists the preference.
     * - Enabling Demo Showroom Mode populates the 3 showcase Wi-Fi homes, 13 sample switches, and 4 rules.
     * - Disabling Demo Showroom Mode removes demo data and initializes a clean profile for the real Wi-Fi LAN.
     */
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

    private fun nowFormatted(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
    }

    fun detectCurrentLanIp(): String {
        return networkClient.resolveLocalIpv4Address()
    }

    /**
     * Two-stage self-healing reconnect for a device whose primary SOAP call failed:
     * - Stage 1: Re-probe all 9 Belkin UPnP ports on the device's current IP address (verifying serialNumber/UDN).
     * - Stage 2: If Stage 1 returns null (device moved to a new DHCP IP), run a fast SSDP scan and match by serialNumber.
     * Returns the healed WeMoDeviceEntity (with updated ipAddress/port) if found on the LAN, or null if offline.
     */
    private suspend fun attemptTwoStageReconnect(device: WeMoDeviceEntity): WeMoDeviceEntity? {
        // Stage 1: Probe all 9 candidate UPnP ports on the known IP address
        val newPort = networkClient.probeWemoPort(
            ipAddress = device.ipAddress,
            preferredPort = device.port,
            expectedSerialOrUdn = device.serialNumber
        )
        if (newPort != null) {
            return device.copy(port = newPort, isReachable = true)
        }

        // Stage 2: Targeted SSDP M-SEARCH scan matching by serialNumber / UDN
        val scanReport = networkClient.performSsdpDiscoveryScan(
            searchTarget = "urn:Belkin:service:basicevent:1",
            knownLocalDevices = emptyList(),
            includeDemoFallbacks = false
        )
        _latestSsdpReport.value = scanReport
        val match = scanReport.discoveredDevices.find { discovered ->
            (device.serialNumber.isNotBlank() && discovered.serialNumber.equals(device.serialNumber, ignoreCase = true)) ||
                (device.udn.isNotBlank() && discovered.usn.contains(device.udn, ignoreCase = true)) ||
                discovered.name.equals(device.name, ignoreCase = true)
        }
        return if (match != null) {
            device.copy(
                ipAddress = match.ipAddress,
                port = match.port,
                isReachable = true,
                firmwareVersion = match.firmwareVersion.ifBlank { device.firmwareVersion },
                hardwareModel = match.hardwareSku.ifBlank { device.hardwareModel },
                macAddress = match.macAddress.ifBlank { device.macAddress }
            )
        } else {
            null
        }
    }

    /**
     * Reconnects and rediscovers all remembered switches belonging to [networkKey] when
     * the user connects or returns to that Wi-Fi network ("Home").
     * Probes each remembered switch via SOAP GetBinaryState AND runs an SSDP multicast
     * M-SEARCH discovery pass to update ports/IPs and restore offline switches.
     */
    suspend fun reconnectAndRediscoverHome(networkKey: String): HomeReconnectSummary {
        val demoMode = _isDemoShowroomMode.value
        val networks = dao.getAllHomeNetworks().first().let {
            if (it.isEmpty() && demoMode) defaultHomeNetworks() else it
        }
        val homeNetwork = networks.find { it.networkKey == networkKey }
            ?: networks.firstOrNull()
            ?: WeMoHomeNetworkEntity(
                networkKey = networkKey,
                homeName = "Home Wi-Fi",
                ssidLabel = "Local Wi-Fi",
                subnetPrefix = "192.168.1"
            )

        val allDevs = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) defaultClassicDevices() else it
        }
        val rememberedForHome = allDevs.filter { it.networkKey == networkKey }
        val checkedAt = nowFormatted()

        // 1. Run SSDP discovery pass scoped to this Wi-Fi Home's remembered devices
        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = "urn:Belkin:service:basicevent:1",
            knownLocalDevices = rememberedForHome,
            includeDemoFallbacks = demoMode
        )
        _latestSsdpReport.value = report
        appendLog(report.logEntry)

        // If this Wi-Fi Home currently has 0 saved devices and SSDP found real switches, auto-adopt them!
        if (rememberedForHome.isEmpty() && report.discoveredDevices.isNotEmpty()) {
            val adoptedCount = adoptDiscoveredDevices(
                discoveredList = report.discoveredDevices,
                activeNetworkKey = networkKey,
                activeWifiSsid = homeNetwork.ssidLabel
            )
            val avgRtt = report.discoveredDevices.map { it.responseTimeMs }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
            return HomeReconnectSummary(
                networkKey = homeNetwork.networkKey,
                homeName = homeNetwork.homeName,
                ssidLabel = homeNetwork.ssidLabel,
                reconnectedCount = adoptedCount,
                totalRememberedCount = adoptedCount,
                averageLatencyMs = avgRtt
            )
        }

        // 2. Probe and reconnect each remembered switch on this Wi-Fi network
        val updatedLatencies = mutableListOf<Int>()
        var reconnectedCount = 0

        rememberedForHome.forEachIndexed { idx, device ->
            val ssdpMatch = report.discoveredDevices.find {
                (device.serialNumber.isNotBlank() && it.serialNumber.equals(device.serialNumber, ignoreCase = true)) ||
                    it.name.equals(device.name, ignoreCase = true) ||
                    it.ipAddress == device.ipAddress
            }
            val candidateDevice = if (ssdpMatch != null) {
                device.copy(ipAddress = ssdpMatch.ipAddress, port = ssdpMatch.port)
            } else {
                device
            }

            val probe = lanSemaphore.withPermit {
                networkClient.getBinaryState(candidateDevice)
            }

            val isOnline = probe.reachableOnLan || (ssdpMatch != null && ssdpMatch.isLiveUdpResponse) || demoMode
            if (isOnline) {
                reconnectedCount++
                val resolvedLatency = when {
                    probe.reachableOnLan && probe.latencyMs > 0 -> probe.latencyMs
                    ssdpMatch != null && ssdpMatch.responseTimeMs > 0 -> ssdpMatch.responseTimeMs.toInt()
                    demoMode -> 11 + idx * 3
                    else -> 12
                }
                updatedLatencies.add(resolvedLatency)

                val recentSetMs = lastSetStateTimestampMs[device.id] ?: 0L
                val allowStateOverwrite = (System.currentTimeMillis() - recentSetMs) > 1500L

                dao.updateDevice(
                    candidateDevice.copy(
                        port = if (probe.reachableOnLan) probe.resolvedPort else candidateDevice.port,
                        isOn = if (allowStateOverwrite && probe.binaryStateOn != null) probe.binaryStateOn else device.isOn,
                        brightness = if (allowStateOverwrite && probe.brightness != null) probe.brightness else device.brightness,
                        isReachable = true,
                        latencyMs = resolvedLatency,
                        lastCheckedTime = checkedAt,
                        firmwareVersion = ssdpMatch?.firmwareVersion?.ifBlank { device.firmwareVersion } ?: device.firmwareVersion,
                        hardwareModel = ssdpMatch?.hardwareSku?.ifBlank { device.hardwareModel } ?: device.hardwareModel
                    )
                )
            } else {
                dao.updateDevice(
                    device.copy(
                        isReachable = false,
                        latencyMs = 0,
                        lastCheckedTime = "Offline • Timeout"
                    )
                )
            }
        }

        dao.updateHomeNetwork(
            homeNetwork.copy(lastConnectedTime = "Reconnected at $checkedAt")
        )

        val avgRtt = if (updatedLatencies.isNotEmpty()) {
            updatedLatencies.average().roundToInt()
        } else {
            0
        }

        return HomeReconnectSummary(
            networkKey = homeNetwork.networkKey,
            homeName = homeNetwork.homeName,
            ssidLabel = homeNetwork.ssidLabel,
            reconnectedCount = reconnectedCount,
            totalRememberedCount = rememberedForHome.size,
            averageLatencyMs = avgRtt
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
     * Toggles a WeMo device's binary power state with:
     * 1. Optimistic UI update in Room so the switch button responds immediately.
     * 2. Per-device Mutex + LAN Semaphore(3) SOAP SetBinaryState dispatch.
     * 3. Automatic 2-stage reconnect (9-port probe + targeted SSDP scan by serialNumber) if the initial call fails.
     * 4. Revert to prior state and mark OFFLINE (isReachable = false, latencyMs = 0) if genuinely unreachable in Real LAN Mode.
     */
    suspend fun toggleDevicePower(device: WeMoDeviceEntity): WeMoDeviceEntity {
        val priorOn = device.isOn
        val priorBrightness = device.brightness
        val nextOn = !priorOn
        val nextBrightness = if (device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && nextOn && priorBrightness == 0) {
            100
        } else {
            priorBrightness
        }

        return mutexFor(device).withLock {
            // 1. Optimistic UI state write
            val optimistic = device.copy(
                isOn = nextOn,
                brightness = nextBrightness,
                lastCheckedTime = "Sending..."
            )
            dao.updateDevice(optimistic)
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

            // 2. Primary SOAP SetBinaryState call
            val targetBrightnessArg = if (device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && nextOn && priorBrightness == 0) 100 else null
            var activeDevice = device
            var result: SoapCallResult = lanSemaphore.withPermit {
                networkClient.setBinaryState(
                    device = activeDevice,
                    targetOn = nextOn,
                    targetBrightness = targetBrightnessArg
                )
            }

            // 3. Two-stage reconnect if unreachable in Real LAN Mode
            if (!result.reachableOnLan && !_isDemoShowroomMode.value) {
                val healedDevice = attemptTwoStageReconnect(activeDevice)
                if (healedDevice != null) {
                    activeDevice = healedDevice
                    result = lanSemaphore.withPermit {
                        networkClient.setBinaryState(
                            device = activeDevice,
                            targetOn = nextOn,
                            targetBrightness = targetBrightnessArg
                        )
                    }
                }
            }

            appendLog(result.logEntry)
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

            // 4. Commit confirmed state OR revert to prior state & mark OFFLINE
            val finalEntity = when {
                result.reachableOnLan -> {
                    activeDevice.copy(
                        isOn = result.binaryStateOn ?: nextOn,
                        isReachable = true,
                        port = result.resolvedPort,
                        latencyMs = result.latencyMs.coerceAtLeast(1),
                        lastCheckedTime = nowFormatted(),
                        brightness = result.brightness ?: nextBrightness
                    )
                }
                _isDemoShowroomMode.value -> {
                    activeDevice.copy(
                        isOn = nextOn,
                        isReachable = true,
                        port = result.resolvedPort,
                        latencyMs = device.latencyMs.takeIf { it > 0 } ?: 14,
                        lastCheckedTime = nowFormatted(),
                        brightness = nextBrightness
                    )
                }
                else -> {
                    // Revert optimistic state and mark device genuinely OFFLINE
                    activeDevice.copy(
                        isOn = priorOn,
                        brightness = priorBrightness,
                        isReachable = false,
                        latencyMs = 0,
                        lastCheckedTime = "Offline • Timeout"
                    )
                }
            }
            dao.updateDevice(finalEntity)
            finalEntity
        }
    }

    /**
     * Toggles all reachable switches and dimmable bulbs in a specific room ON or OFF simultaneously.
     */
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

    private suspend fun setDevicePowerExplicit(
        device: WeMoDeviceEntity,
        targetOn: Boolean
    ): WeMoDeviceEntity {
        val priorOn = device.isOn
        val priorBrightness = device.brightness
        val nextBrightness = if (device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && targetOn && priorBrightness == 0) 100 else priorBrightness
        val targetBrightnessArg = if (device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && targetOn && priorBrightness == 0) 100 else null

        return mutexFor(device).withLock {
            dao.updateDevice(device.copy(isOn = targetOn, brightness = nextBrightness))
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

            var activeDevice = device
            var result = lanSemaphore.withPermit {
                networkClient.setBinaryState(activeDevice, targetOn, targetBrightnessArg)
            }
            if (!result.reachableOnLan && !_isDemoShowroomMode.value) {
                val healed = attemptTwoStageReconnect(activeDevice)
                if (healed != null) {
                    activeDevice = healed
                    result = lanSemaphore.withPermit {
                        networkClient.setBinaryState(activeDevice, targetOn, targetBrightnessArg)
                    }
                }
            }
            appendLog(result.logEntry)
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

            val updated = when {
                result.reachableOnLan -> activeDevice.copy(
                    isOn = result.binaryStateOn ?: targetOn,
                    brightness = result.brightness ?: nextBrightness,
                    isReachable = true,
                    port = result.resolvedPort,
                    latencyMs = result.latencyMs.coerceAtLeast(1),
                    lastCheckedTime = nowFormatted()
                )
                _isDemoShowroomMode.value -> activeDevice.copy(
                    isOn = targetOn,
                    brightness = nextBrightness,
                    isReachable = true,
                    latencyMs = device.latencyMs.takeIf { it > 0 } ?: 14,
                    lastCheckedTime = nowFormatted()
                )
                else -> activeDevice.copy(
                    isOn = priorOn,
                    brightness = priorBrightness,
                    isReachable = false,
                    latencyMs = 0,
                    lastCheckedTime = "Offline • Timeout"
                )
            }
            dao.updateDevice(updated)
            updated
        }
    }

    /**
     * Sets the brightness level for all reachable dimmable bulbs in a specific room at once.
     */
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

    suspend fun updateDeviceBrightness(device: WeMoDeviceEntity, newBrightness: Int): WeMoDeviceEntity {
        val clamped = newBrightness.coerceIn(1, 100)
        val priorOn = device.isOn
        val priorBrightness = device.brightness

        return mutexFor(device).withLock {
            // 1. Optimistic UI update
            dao.updateDevice(device.copy(isOn = true, brightness = clamped))
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

            // 2. SOAP SetBinaryState(1, brightness)
            var activeDevice = device
            var result = lanSemaphore.withPermit {
                networkClient.setBinaryState(
                    device = activeDevice,
                    targetOn = true,
                    targetBrightness = clamped
                )
            }

            // 3. Two-stage reconnect if unreachable in Real LAN Mode
            if (!result.reachableOnLan && !_isDemoShowroomMode.value) {
                val healed = attemptTwoStageReconnect(activeDevice)
                if (healed != null) {
                    activeDevice = healed
                    result = lanSemaphore.withPermit {
                        networkClient.setBinaryState(
                            device = activeDevice,
                            targetOn = true,
                            targetBrightness = clamped
                        )
                    }
                }
            }
            appendLog(result.logEntry)
            lastSetStateTimestampMs[device.id] = System.currentTimeMillis()

            // 4. Commit or revert to prior state & mark OFFLINE
            val finalEntity = when {
                result.reachableOnLan -> activeDevice.copy(
                    isOn = true,
                    isReachable = true,
                    brightness = result.brightness ?: clamped,
                    port = result.resolvedPort,
                    latencyMs = result.latencyMs.coerceAtLeast(1),
                    lastCheckedTime = nowFormatted()
                )
                _isDemoShowroomMode.value -> activeDevice.copy(
                    isOn = true,
                    isReachable = true,
                    brightness = clamped,
                    port = result.resolvedPort,
                    latencyMs = device.latencyMs.takeIf { it > 0 } ?: 14,
                    lastCheckedTime = nowFormatted()
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

    suspend fun reconnectDevice(device: WeMoDeviceEntity): WeMoDeviceEntity {
        return mutexFor(device).withLock {
            var activeDevice = device
            var probe = lanSemaphore.withPermit {
                networkClient.getBinaryState(activeDevice)
            }
            if (!probe.reachableOnLan && !_isDemoShowroomMode.value) {
                val healed = attemptTwoStageReconnect(activeDevice)
                if (healed != null) {
                    activeDevice = healed
                    probe = lanSemaphore.withPermit {
                        networkClient.getBinaryState(activeDevice)
                    }
                }
            }
            appendLog(probe.logEntry)

            val updated = when {
                probe.reachableOnLan -> {
                    val meta = networkClient.getMetaInfo(activeDevice.ipAddress, probe.resolvedPort)
                    val rssi = networkClient.getSignalStrength(activeDevice.ipAddress, probe.resolvedPort)
                    activeDevice.copy(
                        isReachable = true,
                        port = probe.resolvedPort,
                        latencyMs = probe.latencyMs.coerceAtLeast(1),
                        lastCheckedTime = nowFormatted(),
                        isOn = probe.binaryStateOn ?: activeDevice.isOn,
                        brightness = probe.brightness ?: activeDevice.brightness,
                        firmwareVersion = meta?.firmwareVersion?.takeIf { it.isNotBlank() } ?: activeDevice.firmwareVersion,
                        macAddress = meta?.macAddress?.takeIf { it.isNotBlank() } ?: activeDevice.macAddress,
                        wifiSsid = meta?.accessPointSsid?.takeIf { it.isNotBlank() } ?: activeDevice.wifiSsid,
                        signalStrengthPercent = rssi ?: activeDevice.signalStrengthPercent
                    )
                }
                _isDemoShowroomMode.value -> {
                    activeDevice.copy(
                        isReachable = true,
                        port = probe.resolvedPort,
                        latencyMs = device.latencyMs.takeIf { it > 0 } ?: 11,
                        lastCheckedTime = nowFormatted(),
                        isOn = probe.binaryStateOn ?: activeDevice.isOn
                    )
                }
                else -> {
                    activeDevice.copy(
                        isReachable = false,
                        latencyMs = 0,
                        lastCheckedTime = "Offline • Timeout"
                    )
                }
            }
            dao.updateDevice(updated)
            updated
        }
    }

    suspend fun pingDeviceLatency(device: WeMoDeviceEntity): WeMoDeviceEntity {
        return reconnectDevice(device)
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

    suspend fun executeSsdpScan(
        searchTarget: String = "urn:Belkin:service:basicevent:1",
        autoAdoptNew: Boolean = false,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): SsdpScanReport {
        val demoMode = _isDemoShowroomMode.value
        val allDevs = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) defaultClassicDevices() else it
        }
        val currentDevicesForHome = allDevs.filter { it.networkKey == activeNetworkKey }
        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = searchTarget,
            knownLocalDevices = currentDevicesForHome,
            includeDemoFallbacks = demoMode
        )
        _latestSsdpReport.value = report
        appendLog(report.logEntry)

        // Update latency, port, IP, and live state for all known devices found in the SSDP scan
        val checkedAt = nowFormatted()
        currentDevicesForHome.forEach { existing ->
            val match = report.discoveredDevices.find {
                (existing.serialNumber.isNotBlank() && it.serialNumber.equals(existing.serialNumber, ignoreCase = true)) ||
                    it.ipAddress == existing.ipAddress ||
                    it.name.equals(existing.name, ignoreCase = true)
            }
            if (match != null) {
                val recentSetMs = lastSetStateTimestampMs[existing.id] ?: 0L
                val allowStateOverwrite = (System.currentTimeMillis() - recentSetMs) > 1500L
                dao.updateDevice(
                    existing.copy(
                        ipAddress = match.ipAddress,
                        port = match.port,
                        isReachable = true,
                        isOn = if (allowStateOverwrite) match.isOn else existing.isOn,
                        brightness = if (allowStateOverwrite) match.brightness else existing.brightness,
                        latencyMs = match.responseTimeMs.toInt().coerceAtLeast(1),
                        lastCheckedTime = checkedAt,
                        serialNumber = match.serialNumber.ifBlank { existing.serialNumber },
                        firmwareVersion = match.firmwareVersion.ifBlank { existing.firmwareVersion },
                        hardwareModel = match.hardwareSku.ifBlank { existing.hardwareModel },
                        macAddress = match.macAddress.ifBlank { existing.macAddress },
                        wifiSsid = match.wifiSsid.ifBlank { existing.wifiSsid }
                    )
                )
            }
        }

        // Confirmed Q2: Auto-adopt all discovered Belkin WeMo devices when a Wi-Fi home has 0 devices
        // OR when autoAdoptNew is requested (e.g., pull-to-refresh).
        if (autoAdoptNew || currentDevicesForHome.isEmpty()) {
            adoptDiscoveredDevices(
                discoveredList = report.discoveredDevices,
                activeNetworkKey = activeNetworkKey,
                activeWifiSsid = activeWifiSsid
            )
        }
        return report
    }

    suspend fun adoptDiscoveredDevice(
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): WeMoDeviceEntity {
        val currentDevices = dao.getAllDevices().first()
        // Match by serialNumber first (across DHCP IP shifts), then fallback to IP or friendlyName on active home
        val existing = currentDevices.find {
            discovered.serialNumber.isNotBlank() &&
                it.serialNumber.equals(discovered.serialNumber, ignoreCase = true)
        } ?: currentDevices.find {
            it.networkKey == activeNetworkKey &&
                (it.ipAddress == discovered.ipAddress || it.name.equals(discovered.name, ignoreCase = true))
        }

        val resolvedSsid = discovered.wifiSsid.ifBlank { activeWifiSsid }
        if (discovered.wifiSsid.isNotBlank()) {
            syncHomeSsidFromDeviceSsid(activeNetworkKey, discovered.wifiSsid)
        }
        val checkedAt = nowFormatted()
        return if (existing == null) {
            val newEntity = WeMoDeviceEntity(
                name = discovered.name,
                roomName = discovered.roomName.ifBlank { inferRoomFromName(discovered.name) },
                networkKey = activeNetworkKey,
                wifiSsid = resolvedSsid,
                deviceType = discovered.deviceType.name,
                ipAddress = discovered.ipAddress,
                port = discovered.port,
                isOn = discovered.isOn,
                isReachable = true,
                brightness = discovered.brightness,
                latencyMs = discovered.responseTimeMs.toInt().coerceAtLeast(1),
                lastCheckedTime = checkedAt,
                serialNumber = discovered.serialNumber,
                firmwareVersion = discovered.firmwareVersion,
                hardwareModel = discovered.hardwareSku,
                macAddress = discovered.macAddress,
                udn = discovered.usn,
                signalStrengthPercent = discovered.signalStrengthPercent,
                sortOrder = currentDevices.size
            )
            val newId = dao.insertDevice(newEntity).toInt()
            newEntity.copy(id = newId)
        } else {
            val updated = existing.copy(
                name = discovered.name.ifBlank { existing.name },
                networkKey = activeNetworkKey,
                wifiSsid = resolvedSsid,
                ipAddress = discovered.ipAddress,
                port = discovered.port,
                isOn = discovered.isOn,
                brightness = discovered.brightness,
                isReachable = true,
                latencyMs = discovered.responseTimeMs.toInt().coerceAtLeast(1),
                lastCheckedTime = checkedAt,
                serialNumber = discovered.serialNumber.ifBlank { existing.serialNumber },
                firmwareVersion = discovered.firmwareVersion.ifBlank { existing.firmwareVersion },
                hardwareModel = discovered.hardwareSku.ifBlank { existing.hardwareModel },
                macAddress = discovered.macAddress.ifBlank { existing.macAddress },
                udn = discovered.usn.ifBlank { existing.udn },
                signalStrengthPercent = discovered.signalStrengthPercent
            )
            dao.updateDevice(updated)
            updated
        }
    }

    suspend fun adoptDiscoveredDevices(
        discoveredList: List<SsdpDiscoveredDevice>,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): Int {
        var newlyAdded = 0
        for (item in discoveredList) {
            val currentDevices = dao.getAllDevices().first()
            val existedBefore = currentDevices.any {
                (item.serialNumber.isNotBlank() && it.serialNumber.equals(item.serialNumber, ignoreCase = true)) ||
                    (it.networkKey == activeNetworkKey && (it.ipAddress == item.ipAddress || it.name.equals(item.name, ignoreCase = true)))
            }
            adoptDiscoveredDevice(
                discovered = item,
                activeNetworkKey = activeNetworkKey,
                activeWifiSsid = activeWifiSsid
            )
            if (!existedBefore) {
                newlyAdded++
            }
        }
        return newlyAdded
    }

    /**
     * Phase 3: Handles incoming UPnP GENA <e:propertyset> push events from physical wall switches
     * and 3-way traveler rockers, updating Room state immediately (<100ms).
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
            // Ignore echo from an in-flight SOAP SetBinaryState initiated by the user
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

    /**
     * Phase 3: Subscribes or renews UPnP GENA event subscriptions for all reachable devices on [networkKey].
     */
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

    /**
     * Phase 3: 30-second fallback GetBinaryState poll for the active Wi-Fi home in Real LAN Mode.
     * Ensures switches that drop GENA subscriptions or reboot are kept in sync.
     */
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

    /**
     * Phase 4 (Confirmed A2): Zero-Permission Subnet + WeMo GetMetaInfo SSID matching.
     * Finds or creates a WeMoHomeNetworkEntity for [subnetPrefix] when on a real LAN.
     */
    suspend fun resolveOrCreateHomeForSubnet(
        subnetPrefix: String,
        gatewayIp: String = "$subnetPrefix.1"
    ): WeMoHomeNetworkEntity? {
        if (_isDemoShowroomMode.value || subnetPrefix == "10.22.22") return null
        val cleanPrefix = subnetPrefix.trim().ifEmpty { "192.168.1" }
        val existingHomes = dao.getAllHomeNetworks().first()
        val matched = existingHomes.find { it.subnetPrefix == cleanPrefix }
        if (matched != null) return matched

        val key = "wifi_lan_${cleanPrefix.replace('.', '_')}"
        val newHome = WeMoHomeNetworkEntity(
            networkKey = key,
            homeName = "Home Wi-Fi ($cleanPrefix.x)",
            ssidLabel = "Local LAN ($cleanPrefix.0/24)",
            subnetPrefix = cleanPrefix,
            gatewayIp = gatewayIp,
            lastConnectedTime = "Connected now",
            autoReconnectOnArrival = true,
            sortOrder = existingHomes.size
        )
        dao.insertHomeNetwork(newHome)
        return newHome
    }

    /**
     * Phase 4 (Confirmed A2): Updates the active Wi-Fi Home's ssidLabel from a Belkin WeMo switch's
     * metainfo1#GetMetaInfo (AccessPointSSID) response so the real 2.4 GHz SSID is shown without
     * requiring Android Location permission.
     */
    private suspend fun syncHomeSsidFromDeviceSsid(networkKey: String, reportedSsid: String) {
        val cleanSsid = reportedSsid.trim()
        if (cleanSsid.isEmpty()) return
        val homes = dao.getAllHomeNetworks().first()
        val home = homes.find { it.networkKey == networkKey } ?: return
        val isGenericSsid = home.ssidLabel.startsWith("Local LAN", ignoreCase = true) ||
            home.ssidLabel.startsWith("Local Wi-Fi", ignoreCase = true)
        if (isGenericSsid && home.ssidLabel != cleanSsid) {
            val updatedName = if (home.homeName.startsWith("Home Wi-Fi (")) "$cleanSsid Home" else home.homeName
            dao.updateHomeNetwork(
                home.copy(
                    homeName = updatedName,
                    ssidLabel = cleanSsid
                )
            )
        }
    }

    /**
     * Phase 5 (Confirmed A3): Toggles hardware Night Mode (20% brightness soft cap) on an
     * In-Wall Smart Dimmer (F7C059 / WDS060) via urn:Belkin:service:basicevent:1#ConfigureNightMode.
     */
    suspend fun toggleDimmerNightMode(device: WeMoDeviceEntity): WeMoDeviceEntity {
        val targetNightMode = !device.nightModeEnabled
        val nextBrightness = if (targetNightMode && device.brightness > 20) 20 else device.brightness

        return mutexFor(device).withLock {
            val result = lanSemaphore.withPermit {
                networkClient.configureNightMode(
                    device = device,
                    nightModeEnabled = targetNightMode,
                    nightModeBrightness = 20
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

    /**
     * Phase 5: Turns OFF a device by ID when its Auto-Off timer expires.
     */
    suspend fun turnOffDeviceById(deviceId: Int): WeMoDeviceEntity? {
        val dev = dao.getDeviceById(deviceId) ?: return null
        if (!dev.isOn) return dev
        return setDevicePowerExplicit(dev, false)
    }

    /**
     * Phase 6 (Confirmed A4): Scans nearby 2.4 GHz Wi-Fi APs from a factory-reset Belkin WeMo
     * Setup AP at 10.22.22.1 via urn:Belkin:service:WiFiSetup:1#GetApList.
     */
    suspend fun scanSetupApNetworks(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152
    ): List<WeMoScannedAp> {
        return provisionerClient.fetchAvailableApList(apHost = apHost, apPort = apPort)
    }

    /**
     * Phase 6 (Confirmed A4): Provisions a factory-reset Belkin WeMo switch at 10.22.22.1 onto
     * the home 2.4 GHz Wi-Fi network via AES-128-CBC (Method 1 OpenWrt or Method 2 RTOS) and
     * double-send ConnectHomeNetwork + CloseSetup.
     */
    suspend fun provisionSetupApDevice(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152,
        ssid: String,
        plainPassword: String,
        channel: Int = 6,
        authMode: String = "WPA2PSK",
        encryptionType: String = "AES",
        encryptionMethod: Int = 1
    ): WeMoProvisionResult {
        val result = provisionerClient.provisionDeviceToHomeWifi(
            apHost = apHost,
            apPort = apPort,
            ssid = ssid,
            plainPassword = plainPassword,
            channel = channel,
            authMode = authMode,
            encryptionType = encryptionType,
            encryptionMethod = encryptionMethod
        )
        appendLog(result.logEntry)
        return result
    }

    /**
     * Saves a newly added or edited device to Room, and optionally pushes a friendlyName
     * change to the physical Belkin WeMo hardware via urn:Belkin:service:basicevent:1#ChangeFriendlyName.
     */
    suspend fun saveDevice(
        device: WeMoDeviceEntity,
        syncFriendlyNameToHardware: Boolean = false
    ) {
        if (device.id == 0) {
            val count = dao.getDeviceCount()
            dao.insertDevice(device.copy(sortOrder = count))
        } else {
            dao.updateDevice(device)
        }
        if (syncFriendlyNameToHardware) {
            val renameResult = lanSemaphore.withPermit {
                networkClient.changeFriendlyName(device, device.name)
            }
            appendLog(renameResult.logEntry)
        }
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

        val DEFAULT_ROOMS = listOf(
            "Living Room",
            "Bedroom",
            "Kitchen",
            "Dining Room",
            "Hallway & Landing",
            "Ensuite",
            "Outdoor",
            "Garage",
            "Office"
        )

        fun defaultHomeNetworks(): List<WeMoHomeNetworkEntity> = listOf(
            WeMoHomeNetworkEntity(
                networkKey = DEFAULT_MAIN_HOME_KEY,
                homeName = "Main Home",
                ssidLabel = "WeMi-Home-5G",
                subnetPrefix = "192.168.1",
                gatewayIp = "192.168.1.1",
                lastConnectedTime = "Connected now",
                autoReconnectOnArrival = true,
                sortOrder = 0
            ),
            WeMoHomeNetworkEntity(
                networkKey = DEFAULT_CABIN_HOME_KEY,
                homeName = "Lake Cabin",
                ssidLabel = "PineLake-Cabin-WiFi",
                subnetPrefix = "192.168.50",
                gatewayIp = "192.168.50.1",
                lastConnectedTime = "Remembered • 3 switches",
                autoReconnectOnArrival = true,
                sortOrder = 1
            ),
            WeMoHomeNetworkEntity(
                networkKey = DEFAULT_OFFICE_HOME_KEY,
                homeName = "Studio Office",
                ssidLabel = "Studio-Guest-LAN",
                subnetPrefix = "10.0.10",
                gatewayIp = "10.0.10.1",
                lastConnectedTime = "Remembered • 2 switches",
                autoReconnectOnArrival = true,
                sortOrder = 2
            )
        )

        fun defaultClassicDevices(): List<WeMoDeviceEntity> = listOf(
            // --- Main Home Wi-Fi (WeMi-Home-5G • 192.168.1.x) ---
            WeMoDeviceEntity(
                id = 1,
                name = "Christmas Tree Living",
                roomName = "Living Room",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.1.101",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                latencyMs = 11,
                lastCheckedTime = "Just now",
                serialNumber = "221517K01018A1",
                sortOrder = 0
            ),
            WeMoDeviceEntity(
                id = 2,
                name = "Christmas Candle",
                roomName = "Living Room",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.1.102",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                latencyMs = 14,
                lastCheckedTime = "Just now",
                serialNumber = "221517K01018A2",
                sortOrder = 1
            ),
            WeMoDeviceEntity(
                id = 3,
                name = "Bedroom Light Motion",
                roomName = "Bedroom",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.MOTION_SENSOR.name,
                ipAddress = "192.168.1.103",
                port = 49153,
                isOn = true,
                isReachable = true,
                latencyMs = 18,
                lastCheckedTime = "Just now",
                lastMotionTime = "4:15:15 PM",
                serialNumber = "221432M02004B9",
                sortOrder = 2
            ),
            WeMoDeviceEntity(
                id = 4,
                name = "Christmas Tree Kitchen",
                roomName = "Kitchen",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.1.104",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                latencyMs = 16,
                lastCheckedTime = "Just now",
                serialNumber = "221517K01018A4",
                sortOrder = 3
            ),
            WeMoDeviceEntity(
                id = 5,
                name = "Hall Lightbulb",
                roomName = "Hallway & Landing",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.105",
                port = 49153,
                isOn = false,
                isReachable = false,
                brightness = 100,
                latencyMs = 0,
                lastCheckedTime = "Timeout",
                serialNumber = "94103EA2B27801",
                sortOrder = 4
            ),
            WeMoDeviceEntity(
                id = 6,
                name = "Lightbulb Landing",
                roomName = "Hallway & Landing",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.106",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 21,
                latencyMs = 32,
                lastCheckedTime = "Just now",
                serialNumber = "94103EA2B27802",
                sortOrder = 5
            ),
            WeMoDeviceEntity(
                id = 7,
                name = "Lightbulb Ensuite",
                roomName = "Ensuite",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.107",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                latencyMs = 15,
                lastCheckedTime = "Just now",
                serialNumber = "94103EA2B27803",
                sortOrder = 6
            ),
            WeMoDeviceEntity(
                id = 8,
                name = "Lightbulb Bedroom",
                roomName = "Bedroom",
                networkKey = DEFAULT_MAIN_HOME_KEY,
                wifiSsid = "WeMi-Home-5G",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.108",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                latencyMs = 12,
                lastCheckedTime = "Just now",
                serialNumber = "94103EA2B27804",
                sortOrder = 7
            ),

            // --- Remembered Devices on Lake Cabin Wi-Fi (PineLake-Cabin-WiFi • 192.168.50.x) ---
            WeMoDeviceEntity(
                id = 9,
                name = "Cabin Porch Floodlight",
                roomName = "Outdoor",
                networkKey = DEFAULT_CABIN_HOME_KEY,
                wifiSsid = "PineLake-Cabin-WiFi",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.50.101",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                latencyMs = 19,
                lastCheckedTime = "Remembered",
                serialNumber = "221517K01050C1",
                sortOrder = 8
            ),
            WeMoDeviceEntity(
                id = 10,
                name = "Dock String Lights",
                roomName = "Outdoor",
                networkKey = DEFAULT_CABIN_HOME_KEY,
                wifiSsid = "PineLake-Cabin-WiFi",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.50.102",
                port = 49153,
                isOn = false,
                isReachable = false,
                brightness = 100,
                latencyMs = 0,
                lastCheckedTime = "Tap Reconnect",
                serialNumber = "221517K01050C2",
                sortOrder = 9
            ),
            WeMoDeviceEntity(
                id = 11,
                name = "Loft Dimmer Bulb",
                roomName = "Bedroom",
                networkKey = DEFAULT_CABIN_HOME_KEY,
                wifiSsid = "PineLake-Cabin-WiFi",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.50.103",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 65,
                latencyMs = 23,
                lastCheckedTime = "Remembered",
                serialNumber = "94103EA2B250C3",
                sortOrder = 10
            ),

            // --- Remembered Devices on Studio Office Wi-Fi (Studio-Guest-LAN • 10.0.10.x) ---
            WeMoDeviceEntity(
                id = 12,
                name = "Desk Key Light",
                roomName = "Office",
                networkKey = DEFAULT_OFFICE_HOME_KEY,
                wifiSsid = "Studio-Guest-LAN",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "10.0.10.42",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 85,
                latencyMs = 10,
                lastCheckedTime = "Remembered",
                serialNumber = "94103EA2B210F1",
                sortOrder = 11
            ),
            WeMoDeviceEntity(
                id = 13,
                name = "Studio Signage Plug",
                roomName = "Office",
                networkKey = DEFAULT_OFFICE_HOME_KEY,
                wifiSsid = "Studio-Guest-LAN",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "10.0.10.43",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                latencyMs = 14,
                lastCheckedTime = "Remembered",
                serialNumber = "221517K01010F2",
                sortOrder = 12
            )
        )

        fun defaultClassicRules(): List<WeMoRuleEntity> = listOf(
            WeMoRuleEntity(
                id = 1,
                name = "Evening Holiday Lights",
                ruleType = "SCHEDULE",
                targetDeviceId = 1,
                targetDeviceName = "Christmas Tree Living",
                triggerSummary = "Daily at Sunset (5:15 PM)",
                actionSummary = "Turn ON Christmas Tree Living",
                targetStateOn = true,
                targetBrightness = 100,
                isEnabled = true
            ),
            WeMoRuleEntity(
                id = 2,
                name = "Bedroom Motion Nightlight",
                ruleType = "MOTION_TRIGGER",
                targetDeviceId = 8,
                targetDeviceName = "Lightbulb Bedroom",
                triggerSummary = "When Bedroom Light Motion senses movement",
                actionSummary = "Turn ON Lightbulb Bedroom (35%)",
                targetStateOn = true,
                targetBrightness = 35,
                isEnabled = true
            ),
            WeMoRuleEntity(
                id = 3,
                name = "Landing Night Dimmer",
                ruleType = "SCHEDULE",
                targetDeviceId = 6,
                targetDeviceName = "Lightbulb Landing",
                triggerSummary = "Every night at 10:00 PM",
                actionSummary = "Dim Lightbulb Landing to 21%",
                targetStateOn = true,
                targetBrightness = 21,
                isEnabled = true
            ),
            WeMoRuleEntity(
                id = 4,
                name = "Midnight Candle Auto-Off",
                ruleType = "AUTO_OFF",
                targetDeviceId = 2,
                targetDeviceName = "Christmas Candle",
                triggerSummary = "Daily at 11:59 PM",
                actionSummary = "Turn OFF Christmas Candle",
                targetStateOn = false,
                targetBrightness = 0,
                isEnabled = true
            )
        )
    }
}
