package com.example.data

import com.example.network.SsdpScanReport
import com.example.network.WeMoNetworkClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Coordinates Belkin WeMo startup state probing, 2-stage self-healing reconnects,
 * SSDP discovery scans, device adoption, and zero-permission subnet/SSID home synchronization.
 *
 * Shares the repository's single [lanSemaphore] (`Semaphore(3)`), [deviceMutexes], and
 * [lastSetStateTimestampMs] map so concurrent startup probes, background polls, and manual
 * toggles never exceed 3 simultaneous TCP connections against 2nd-Gen RTOS switches.
 */
internal class WeMoDiscoveryCoordinator(
    private val dao: WeMoDao,
    private val networkClient: WeMoNetworkClient,
    private val lanSemaphore: Semaphore,
    private val deviceMutexes: ConcurrentHashMap<String, Mutex>,
    private val lastSetStateTimestampMs: ConcurrentHashMap<Int, Long>,
    private val isDemoShowroomMode: () -> Boolean,
    private val onSsdpReport: (SsdpScanReport) -> Unit,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String
) {
    private fun mutexFor(device: WeMoDeviceEntity): Mutex {
        val key = device.serialNumber.takeIf { it.isNotBlank() } ?: "${device.ipAddress}:${device.port}"
        return deviceMutexes.getOrPut(key) { Mutex() }
    }

    /**
     * Two-stage self-healing reconnect for a device whose primary SOAP call failed:
     * - Stage 1: Re-probe all 9 Belkin UPnP ports on the device's current IP address (verifying serialNumber/UDN).
     * - Stage 2: If Stage 1 returns null (device moved to a new DHCP IP), run a fast SSDP scan and match by serialNumber.
     * Returns the healed [WeMoDeviceEntity] (with updated ipAddress/port) if found on the LAN, or null if offline.
     */
    suspend fun attemptTwoStageReconnect(device: WeMoDeviceEntity): WeMoDeviceEntity? {
        val newPort = networkClient.probeWemoPort(
            ipAddress = device.ipAddress,
            preferredPort = device.port,
            expectedSerialOrUdn = device.serialNumber
        )
        if (newPort != null) {
            return device.copy(port = newPort, isReachable = true)
        }

        val scanReport = networkClient.performSsdpDiscoveryScan(
            searchTarget = "urn:Belkin:service:basicevent:1",
            knownLocalDevices = emptyList(),
            includeDemoFallbacks = false
        )
        onSsdpReport(scanReport)
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
     * Immediately probes all remembered switches on [networkKey] in parallel (bounded by [lanSemaphore])
     * via SOAP GetBinaryState on app startup or home network switch so that each device's live
     * ON, OFF (Accessible Standby), or Disconnected state is reflected in the UI right away.
     */
    suspend fun detectStartupDeviceStates(networkKey: String): List<WeMoDeviceEntity> = coroutineScope {
        val demoMode = isDemoShowroomMode()
        val allDevs = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) WeMoDemoFixtures.defaultClassicDevices() else it
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
     * and enriching hardware metadata (`GetMetaInfo` + `GetSignalStrength`).
     */
    suspend fun reconnectDevice(device: WeMoDeviceEntity): WeMoDeviceEntity {
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
                        wifiSsid = meta?.accessPointSsid?.takeIf { it.isNotBlank() && !isWemoDeviceSetupSsid(it) }
                            ?: activeDevice.wifiSsid.takeUnless { isWemoDeviceSetupSsid(it) }
                            ?: "Local LAN",
                        signalStrengthPercent = rssi ?: activeDevice.signalStrengthPercent
                    )
                }
                isDemoShowroomMode() -> {
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

    /**
     * Reconnects and rediscovers all remembered switches belonging to [networkKey] when
     * the user connects or returns to that Wi-Fi network ("Home").
     */
    suspend fun reconnectAndRediscoverHome(networkKey: String): HomeReconnectSummary {
        detectStartupDeviceStates(networkKey)

        val demoMode = isDemoShowroomMode()
        val networks = dao.getAllHomeNetworks().first().let {
            if (it.isEmpty() && demoMode) WeMoDemoFixtures.defaultHomeNetworks() else it
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
            if (it.isEmpty() && demoMode) WeMoDemoFixtures.defaultClassicDevices() else it
        }
        val rememberedForHome = allDevs.filter { it.networkKey == networkKey }
        val checkedAt = nowFormatted()

        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = "urn:Belkin:service:basicevent:1",
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

    suspend fun executeSsdpScan(
        searchTarget: String = "urn:Belkin:service:basicevent:1",
        autoAdoptNew: Boolean = false,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): SsdpScanReport {
        val demoMode = isDemoShowroomMode()
        val allDevs = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) WeMoDemoFixtures.defaultClassicDevices() else it
        }
        val currentDevicesForHome = allDevs.filter { it.networkKey == activeNetworkKey }
        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = searchTarget,
            knownLocalDevices = currentDevicesForHome,
            includeDemoFallbacks = demoMode
        )
        onSsdpReport(report)
        appendLog(report.logEntry)

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
                val resolvedName = existing.name.ifBlank { match.name }
                val inferredRoom = inferRoomFromName(resolvedName)
                val resolvedRoom = if (!demoMode && (
                        existing.roomName.isBlank() ||
                            existing.roomName.equals("Unassigned", ignoreCase = true) ||
                            (existing.roomName.equals("Living Room", ignoreCase = true) && inferredRoom != "Living Room")
                        )
                ) {
                    inferredRoom
                } else {
                    existing.roomName
                }
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
        val existing = currentDevices.find {
            discovered.serialNumber.isNotBlank() &&
                it.serialNumber.equals(discovered.serialNumber, ignoreCase = true)
        } ?: currentDevices.find {
            it.networkKey == activeNetworkKey &&
                (it.ipAddress == discovered.ipAddress || it.name.equals(discovered.name, ignoreCase = true))
        }

        val rawDeviceSsid = discovered.wifiSsid.trim()
        val validRouterSsid = rawDeviceSsid.takeIf { it.isNotEmpty() && !isWemoDeviceSetupSsid(it) }
        val resolvedSsid = validRouterSsid ?: activeWifiSsid.takeIf { !isWemoDeviceSetupSsid(it) } ?: "Local LAN"
        if (validRouterSsid != null) {
            syncHomeSsidFromDeviceSsid(activeNetworkKey, validRouterSsid)
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
            val resolvedName = discovered.name.ifBlank { existing.name }
            val inferredRoom = discovered.roomName.ifBlank { inferRoomFromName(resolvedName) }
            val resolvedRoom = if (!isDemoShowroomMode() && (
                    existing.roomName.isBlank() ||
                        existing.roomName.equals("Unassigned", ignoreCase = true) ||
                        (existing.roomName.equals("Living Room", ignoreCase = true) && inferredRoom != "Living Room")
                    )
            ) {
                inferredRoom
            } else {
                existing.roomName
            }
            val updated = existing.copy(
                name = resolvedName,
                roomName = resolvedRoom,
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
     * Zero-Permission Subnet matching:
     * finds or creates a [WeMoHomeNetworkEntity] for [subnetPrefix] when on a real LAN.
     */
    suspend fun resolveOrCreateHomeForSubnet(
        subnetPrefix: String,
        gatewayIp: String = "$subnetPrefix.1"
    ): WeMoHomeNetworkEntity? {
        if (isDemoShowroomMode() || subnetPrefix == "10.22.22") return null
        val cleanPrefix = subnetPrefix.trim().ifEmpty { "192.168.1" }
        val existingHomes = dao.getAllHomeNetworks().first()
        val matched = existingHomes.find { it.subnetPrefix == cleanPrefix }
        if (matched != null) return matched

        val key = "wifi_lan_${cleanPrefix.replace('.', '_')}"
        val defaultName = if (existingHomes.isEmpty()) "My Home" else "Home ($cleanPrefix.x)"
        val newHome = WeMoHomeNetworkEntity(
            networkKey = key,
            homeName = defaultName,
            ssidLabel = "LAN $cleanPrefix.0/24",
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
     * Updates the active Wi-Fi Home's ssidLabel only when a real router SSID is reported
     * (never a Belkin WeMo soft-AP SSID like `WeMo.Switch.xxx` / `WeMo.Light.xxx`), and never
     * overwrites the Home Name with a WeMo device SSID.
     */
    private suspend fun syncHomeSsidFromDeviceSsid(networkKey: String, reportedSsid: String) {
        val cleanSsid = reportedSsid.trim()
        if (cleanSsid.isEmpty() || isWemoDeviceSetupSsid(cleanSsid)) return
        val homes = dao.getAllHomeNetworks().first()
        val home = homes.find { it.networkKey == networkKey } ?: return
        val isGenericSsid = home.ssidLabel.startsWith("Local LAN", ignoreCase = true) ||
            home.ssidLabel.startsWith("LAN ", ignoreCase = true) ||
            home.ssidLabel.startsWith("Local Wi-Fi", ignoreCase = true)
        if (isGenericSsid && home.ssidLabel != cleanSsid) {
            dao.updateHomeNetwork(
                home.copy(
                    ssidLabel = cleanSsid
                )
            )
        }
    }

    companion object {
        /**
         * Returns true if [ssid] is a Belkin WeMo hardware soft-AP or device identifier
         * (e.g. `WeMo.Switch.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`, `WeMo.Dimmer.xxx`),
         * which `metainfo1#GetMetaInfo` often returns in field 4 instead of the router SSID.
         */
        fun isWemoDeviceSetupSsid(ssid: String): Boolean =
            com.example.network.WeMoXmlAndSkuParser.isWemoDeviceSetupSsid(ssid)
    }
}
