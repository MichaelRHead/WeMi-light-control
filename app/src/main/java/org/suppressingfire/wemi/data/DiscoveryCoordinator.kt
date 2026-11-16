package org.suppressingfire.wemi.data

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.SsdpScanReport
import org.suppressingfire.wemi.network.XmlAndSkuParser

/**
 * Coordinates startup state probing, 2-stage self-healing reconnects, SSDP discovery scans,
 * device adoption, and zero-permission subnet/SSID home synchronization.
 *
 * Shares the repository's single [lanSemaphore] (`Semaphore(3)`), [deviceMutexes], and
 * [lastSetStateTimestampMs] map so concurrent startup probes, background polls, and manual
 * toggles never exceed 3 simultaneous TCP connections against 2nd-Gen RTOS switches.
 */
internal class DiscoveryCoordinator(
    private val dao: SwitchDao,
    private val networkClient: NetworkClient,
    private val lanSemaphore: Semaphore,
    private val deviceMutexes: ConcurrentHashMap<String, Mutex>,
    private val lastSetStateTimestampMs: ConcurrentHashMap<Int, Long>,
    private val isDemoShowroomMode: () -> Boolean,
    private val onSsdpReport: (SsdpScanReport) -> Unit,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String
) {
    private fun mutexFor(device: DeviceEntity): Mutex =
        deviceMutexes.getOrPut(device.concurrencyKey()) { Mutex() }

    private val homeSanitizer = HomeProfileSanitizer(
        dao = dao,
        isDemoShowroomMode = isDemoShowroomMode,
        nowFormatted = nowFormatted,
        findMatchingExistingDevice = ::findMatchingExistingDevice
    )

    /**
     * 3-tier hardware identity matcher between a persisted [existing] device and a newly
     * discovered [discovered] SSDP/unicast device (delegating to [DeviceEntity.matchesDiscovered]).
     */
    internal fun matchesDiscoveredDevice(
        existing: DeviceEntity,
        discovered: SsdpDiscoveredDevice,
        requireSamePortWhenBlankSerial: Boolean = true
    ): Boolean = existing.matchesDiscovered(
        discovered = discovered,
        requireSamePortWhenBlankSerial = requireSamePortWhenBlankSerial
    )

    internal fun findMatchingExistingDevice(
        existingDevices: List<DeviceEntity>,
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String
    ): DeviceEntity? {
        if (!LanRuntimeUtils.isPlaceholderSerial(discovered.serialNumber)) {
            existingDevices.find {
                !LanRuntimeUtils.isPlaceholderSerial(it.serialNumber) &&
                    it.serialNumber.equals(discovered.serialNumber, ignoreCase = true)
            }?.let { return it }
        }
        return existingDevices.find {
            it.networkKey == activeNetworkKey &&
                matchesDiscoveredDevice(
                    existing = it,
                    discovered = discovered,
                    requireSamePortWhenBlankSerial = true
                )
        }
    }

    /**
     * Two-stage self-healing reconnect for a device whose primary SOAP call failed:
     * - Stage 1: Re-probe all 9 UPnP ports on the device's current IP address (verifying serialNumber/UDN).
     * - Stage 2: If Stage 1 returns null (device moved to a new DHCP IP), run a fast SSDP scan and match by serialNumber.
     */
    suspend fun attemptTwoStageReconnect(device: DeviceEntity): DeviceEntity? {
        val newPort = networkClient.probeWemoPort(
            ipAddress = device.ipAddress,
            preferredPort = device.port,
            expectedSerialOrUdn = device.serialNumber
        )
        if (newPort != null) {
            return device.copy(port = newPort, isReachable = true)
        }

        val scanReport = networkClient.performSsdpDiscoveryScan(
            searchTarget = XmlAndSkuParser.URN_BASIC_EVENT,
            knownLocalDevices = emptyList(),
            includeDemoFallbacks = false,
            allowUnicastSubnetFallback = false
        )
        onSsdpReport(scanReport)
        val match = scanReport.discoveredDevices.find { discovered ->
            matchesDiscoveredDevice(
                existing = device,
                discovered = discovered,
                requireSamePortWhenBlankSerial = false
            )
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
     * Immediately probes all remembered switches on [networkKey] in parallel (bounded by [lanSemaphore])
     * via SOAP `GetBinaryState` on app startup or home network switch.
     */
    suspend fun detectStartupDeviceStates(networkKey: String): List<DeviceEntity> = coroutineScope {
        val demoMode = isDemoShowroomMode()
        val allDevs = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultClassicDevices() else it
        }
        val homeDevices = allDevs.filter { it.networkKey == networkKey }
        if (homeDevices.isEmpty()) return@coroutineScope emptyList()

        val checkedAt = nowFormatted()
        homeDevices.map { device ->
            async {
                mutexFor(device).withLock {
                    val probe = lanSemaphore.withPermit {
                        networkClient.getBinaryState(device)
                    }
                    appendLog(probe.logEntry)
                    val updated = when {
                        probe.reachableOnLan -> device.copy(
                            isReachable = true,
                            port = probe.resolvedPort,
                            isOn = probe.binaryStateOn ?: device.isOn,
                            brightness = probe.brightness ?: device.brightness,
                            latencyMs = probe.latencyMs.coerceAtLeast(1),
                            lastCheckedTime = checkedAt
                        )
                        demoMode -> device
                        else -> device.copy(
                            isReachable = false,
                            latencyMs = 0,
                            lastCheckedTime = "Offline • Timeout"
                        )
                    }
                    dao.updateDevice(updated)
                    updated
                }
            }
        }.awaitAll()
    }

    /**
     * Reconnects a single device by probing `GetBinaryState` (with 2-stage self-healing fallback)
     * and enriching hardware metadata (`GetMetaInfo` + `GetSignalStrength`) inside [lanSemaphore].
     */
    suspend fun reconnectDevice(device: DeviceEntity): DeviceEntity {
        return mutexFor(device).withLock {
            var activeDevice = device
            var probe = lanSemaphore.withPermit {
                networkClient.getBinaryState(activeDevice)
            }
            if (!probe.reachableOnLan && !isDemoShowroomMode()) {
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
                    val (meta, rssi) = lanSemaphore.withPermit {
                        val m = networkClient.getMetaInfo(activeDevice.ipAddress, probe.resolvedPort)
                        val r = networkClient.getSignalStrength(activeDevice.ipAddress, probe.resolvedPort)
                        m to r
                    }
                    activeDevice.copy(
                        isReachable = true,
                        port = probe.resolvedPort,
                        latencyMs = probe.latencyMs.coerceAtLeast(1),
                        lastCheckedTime = nowFormatted(),
                        isOn = probe.binaryStateOn ?: activeDevice.isOn,
                        brightness = probe.brightness ?: activeDevice.brightness,
                        firmwareVersion = meta?.firmwareVersion?.takeIf { it.isNotBlank() } ?: activeDevice.firmwareVersion,
                        macAddress = meta?.macAddress?.takeIf { it.isNotBlank() } ?: activeDevice.macAddress,
                        wifiSsid = meta?.accessPointSsid?.takeIf { it.isNotBlank() && !isWemoDeviceSetupSsid(it) }
                            ?: activeDevice.wifiSsid.takeUnless { isWemoDeviceSetupSsid(it) }
                            ?: "Local LAN",
                        signalStrengthPercent = rssi ?: activeDevice.signalStrengthPercent
                    )
                }
                isDemoShowroomMode() -> activeDevice.copy(
                    isReachable = true,
                    port = probe.resolvedPort,
                    latencyMs = device.latencyMs.takeIf { it > 0 } ?: 11,
                    lastCheckedTime = nowFormatted(),
                    isOn = probe.binaryStateOn ?: activeDevice.isOn
                )
                else -> activeDevice.copy(
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
     * Reconnects and rediscovers all remembered switches belonging to [networkKey]:
     * runs SSDP discovery first (healing DHCP IP/port hops) and then probes all remembered
     * switches in a single parallel pass bounded by [lanSemaphore] (`Semaphore(3)`) and
     * serialized per device via [mutexFor].
     */
    suspend fun reconnectAndRediscoverHome(networkKey: String): HomeReconnectSummary {
        val demoMode = isDemoShowroomMode()
        val networks = dao.getAllHomeNetworks().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultHomeNetworks() else it
        }
        val homeNetwork = networks.find { it.networkKey == networkKey }
            ?: networks.firstOrNull()
            ?: HomeNetworkEntity(
                networkKey = networkKey,
                homeName = "Home Wi-Fi",
                ssidLabel = "Local Wi-Fi",
                subnetPrefix = "192.168.1"
            )

        val allDevs = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultClassicDevices() else it
        }
        val rememberedForHome = allDevs.filter { it.networkKey == networkKey }
        val checkedAt = nowFormatted()

        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = XmlAndSkuParser.URN_BASIC_EVENT,
            knownLocalDevices = rememberedForHome,
            includeDemoFallbacks = demoMode
        )
        onSsdpReport(report)
        appendLog(report.logEntry)

        if (rememberedForHome.isEmpty() && report.discoveredDevices.isNotEmpty()) {
            val adoptedCount = adoptDiscoveredDevices(
                discoveredList = report.discoveredDevices,
                activeNetworkKey = networkKey,
                activeWifiSsid = homeNetwork.ssidLabel
            )
            dao.updateHomeNetwork(homeNetwork.copy(lastConnectedTime = "Reconnected at $checkedAt"))
            val avgRtt = report.discoveredDevices.map { it.responseTimeMs }.average()
                .takeIf { !it.isNaN() }?.roundToInt() ?: 0
            return HomeReconnectSummary(
                networkKey = homeNetwork.networkKey,
                homeName = homeNetwork.homeName,
                ssidLabel = homeNetwork.ssidLabel,
                reconnectedCount = adoptedCount,
                totalRememberedCount = adoptedCount,
                averageLatencyMs = avgRtt
            )
        }

        val updatedLatencies = coroutineScope {
            rememberedForHome.mapIndexed { idx, device ->
                async {
                    mutexFor(device).withLock {
                        val ssdpMatch = report.discoveredDevices.find { discovered ->
                            matchesDiscoveredDevice(
                                existing = device,
                                discovered = discovered,
                                requireSamePortWhenBlankSerial = false
                            )
                        }
                        val candidateDevice = if (ssdpMatch != null) {
                            device.copy(ipAddress = ssdpMatch.ipAddress, port = ssdpMatch.port)
                        } else {
                            device
                        }

                        val probe = lanSemaphore.withPermit {
                            networkClient.getBinaryState(candidateDevice)
                        }
                        appendLog(probe.logEntry)

                        val isOnline = probe.reachableOnLan ||
                            (ssdpMatch != null && ssdpMatch.isLiveUdpResponse) ||
                            demoMode
                        if (isOnline) {
                            val resolvedLatency = when {
                                probe.reachableOnLan && probe.latencyMs > 0 -> probe.latencyMs
                                ssdpMatch != null && ssdpMatch.responseTimeMs > 0 -> ssdpMatch.responseTimeMs.toInt()
                                demoMode -> 11 + idx * 3
                                else -> 12
                            }
                            val recentSetMs = lastSetStateTimestampMs[device.id] ?: 0L
                            val allowStateOverwrite = (System.currentTimeMillis() - recentSetMs) > 1500L
                            val resolvedRoom = resolveDeviceRoomAssignment(
                                existingRoomName = device.roomName,
                                deviceName = device.name,
                                discoveredRoomName = ssdpMatch?.roomName.orEmpty(),
                                isDemoMode = demoMode
                            )
                            dao.updateDevice(
                                candidateDevice.copy(
                                    roomName = resolvedRoom,
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
                            resolvedLatency
                        } else {
                            dao.updateDevice(
                                device.copy(
                                    isReachable = false,
                                    latencyMs = 0,
                                    lastCheckedTime = "Offline • Timeout"
                                )
                            )
                            null
                        }
                    }
                }
            }.awaitAll()
        }.filterNotNull()

        dao.updateHomeNetwork(homeNetwork.copy(lastConnectedTime = "Reconnected at $checkedAt"))
        val avgRtt = if (updatedLatencies.isNotEmpty()) updatedLatencies.average().roundToInt() else 0

        return HomeReconnectSummary(
            networkKey = homeNetwork.networkKey,
            homeName = homeNetwork.homeName,
            ssidLabel = homeNetwork.ssidLabel,
            reconnectedCount = updatedLatencies.size,
            totalRememberedCount = rememberedForHome.size,
            averageLatencyMs = avgRtt
        )
    }

    suspend fun executeSsdpScan(
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT,
        autoAdoptNew: Boolean = false,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): SsdpScanReport {
        val demoMode = isDemoShowroomMode()
        val allDevs = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultClassicDevices() else it
        }
        val currentDevicesForHome = allDevs.filter { it.networkKey == activeNetworkKey }
        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = searchTarget,
            knownLocalDevices = currentDevicesForHome,
            includeDemoFallbacks = demoMode
        )
        appendLog(report.logEntry)

        val checkedAt = nowFormatted()
        currentDevicesForHome.forEach { existing ->
            val match = report.discoveredDevices.find { discovered ->
                matchesDiscoveredDevice(existing, discovered, requireSamePortWhenBlankSerial = true)
            }
            if (match != null) {
                val recentSetMs = lastSetStateTimestampMs[existing.id] ?: 0L
                val allowStateOverwrite = (System.currentTimeMillis() - recentSetMs) > 1500L
                val resolvedName = existing.name.ifBlank { match.name }
                val resolvedRoom = resolveDeviceRoomAssignment(
                    existingRoomName = existing.roomName,
                    deviceName = resolvedName,
                    discoveredRoomName = match.roomName,
                    isDemoMode = demoMode
                )
                dao.updateDevice(
                    existing.copy(
                        roomName = resolvedRoom,
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

        if (autoAdoptNew || currentDevicesForHome.isEmpty()) {
            adoptDiscoveredDevices(report.discoveredDevices, activeNetworkKey, activeWifiSsid)
        }
        onSsdpReport(report)
        return report
    }

    suspend fun adoptDiscoveredDevice(
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): DeviceEntity = homeSanitizer.adoptDiscoveredDevice(discovered, activeNetworkKey, activeWifiSsid)

    suspend fun adoptDiscoveredDevices(
        discoveredList: List<SsdpDiscoveredDevice>,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): Int = homeSanitizer.adoptDiscoveredDevices(discoveredList, activeNetworkKey, activeWifiSsid)

    suspend fun sanitizeAndResolveInitialLanHome(localIp: String): String =
        homeSanitizer.sanitizeAndResolveInitialLanHome(localIp)

    suspend fun resolveOrCreateHomeForSubnet(
        subnetPrefix: String,
        gatewayIp: String = "$subnetPrefix.1"
    ): HomeNetworkEntity? = homeSanitizer.resolveOrCreateHomeForSubnet(subnetPrefix, gatewayIp)

    companion object {
        fun isWemoDeviceSetupSsid(ssid: String): Boolean =
            XmlAndSkuParser.isWemoDeviceSetupSsid(ssid)
    }
}
