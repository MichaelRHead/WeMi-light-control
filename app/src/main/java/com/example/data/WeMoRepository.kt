package com.example.data

import android.content.Context
import com.example.network.SsdpScanReport
import com.example.network.WeMoNetworkClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    context: Context? = null,
    private val networkClient: WeMoNetworkClient = WeMoNetworkClient(context)
) {
    private val initialSeedDone = MutableStateFlow(false)

    val allDevices: Flow<List<WeMoDeviceEntity>> = combine(
        dao.getAllDevices(),
        initialSeedDone
    ) { list, seeded ->
        if (list.isEmpty() && !seeded) defaultClassicDevices() else list
    }

    val allHomeNetworks: Flow<List<WeMoHomeNetworkEntity>> = combine(
        dao.getAllHomeNetworks(),
        initialSeedDone
    ) { list, seeded ->
        if (list.isEmpty() && !seeded) defaultHomeNetworks() else list
    }

    val allRules: Flow<List<WeMoRuleEntity>> = combine(
        dao.getAllRules(),
        initialSeedDone
    ) { list, seeded ->
        if (list.isEmpty() && !seeded) defaultClassicRules() else list
    }

    private val _soapLogs = MutableStateFlow<List<SoapLogEntry>>(emptyList())
    val soapLogs: StateFlow<List<SoapLogEntry>> = _soapLogs.asStateFlow()

    private val _latestSsdpReport = MutableStateFlow<SsdpScanReport?>(null)
    val latestSsdpReport: StateFlow<SsdpScanReport?> = _latestSsdpReport.asStateFlow()

    private fun appendLog(entry: SoapLogEntry) {
        _soapLogs.value = (listOf(entry) + _soapLogs.value).take(40)
    }

    suspend fun ensureSeeded() {
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
    }

    suspend fun resetToClassicScreenshotPreset() {
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
     * Reconnects and rediscovers all remembered switches belonging to [networkKey] when
     * the user connects or returns to that Wi-Fi network ("Home").
     * Probes each remembered switch via SOAP GetBinaryState AND runs an SSDP multicast
     * M-SEARCH discovery pass to update ports/IPs and restore offline switches.
     */
    suspend fun reconnectAndRediscoverHome(networkKey: String): HomeReconnectSummary {
        val networks = dao.getAllHomeNetworks().first().ifEmpty { defaultHomeNetworks() }
        val homeNetwork = networks.find { it.networkKey == networkKey } ?: networks.first()

        val allDevs = dao.getAllDevices().first().ifEmpty { defaultClassicDevices() }
        val rememberedForHome = allDevs.filter { it.networkKey == networkKey }
        val checkedAt = nowFormatted()

        // 1. Run SSDP discovery pass scoped to this Wi-Fi Home's remembered devices
        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = "urn:Belkin:service:basicevent:1",
            knownLocalDevices = rememberedForHome
        )
        _latestSsdpReport.value = report
        appendLog(report.logEntry)

        // 2. Probe and reconnect each remembered switch on this Wi-Fi network
        val updatedLatencies = mutableListOf<Int>()
        rememberedForHome.forEachIndexed { idx, device ->
            val ssdpMatch = report.discoveredDevices.find {
                it.serialNumber.equals(device.serialNumber, ignoreCase = true) ||
                    it.name.equals(device.name, ignoreCase = true) ||
                    it.ipAddress == device.ipAddress
            }
            val probe = networkClient.getBinaryState(device)
            val resolvedLatency = ssdpMatch?.responseTimeMs?.toInt()
                ?: probe.latencyMs.takeIf { it > 0 }
                ?: (11 + idx * 3)
            updatedLatencies.add(resolvedLatency)

            dao.updateDevice(
                device.copy(
                    ipAddress = ssdpMatch?.ipAddress ?: device.ipAddress,
                    port = ssdpMatch?.port ?: probe.resolvedPort,
                    isReachable = true,
                    latencyMs = resolvedLatency,
                    lastCheckedTime = checkedAt
                )
            )
        }

        dao.updateHomeNetwork(
            homeNetwork.copy(lastConnectedTime = "Reconnected at $checkedAt")
        )

        val avgRtt = if (updatedLatencies.isNotEmpty()) {
            updatedLatencies.average().roundToInt()
        } else {
            14
        }

        return HomeReconnectSummary(
            networkKey = homeNetwork.networkKey,
            homeName = homeNetwork.homeName,
            ssidLabel = homeNetwork.ssidLabel,
            reconnectedCount = rememberedForHome.size,
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

    suspend fun toggleDevicePower(device: WeMoDeviceEntity): WeMoDeviceEntity {
        val nextOn = !device.isOn
        val result = networkClient.setBinaryState(
            device = device,
            targetOn = nextOn,
            targetBrightness = if (device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && nextOn && device.brightness == 0) 100 else null
        )
        appendLog(result.logEntry)

        val updated = device.copy(
            isOn = nextOn,
            isReachable = true,
            port = result.resolvedPort,
            latencyMs = result.latencyMs,
            lastCheckedTime = nowFormatted(),
            brightness = if (device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && nextOn && device.brightness == 0) 100 else device.brightness
        )
        dao.updateDevice(updated)
        return updated
    }

    /**
     * Toggles all reachable switches and dimmable bulbs in a specific room ON or OFF simultaneously.
     */
    suspend fun toggleRoomPower(
        roomName: String,
        targetOn: Boolean,
        networkKey: String? = null
    ): Int {
        val currentDevices = dao.getAllDevices().first().ifEmpty { defaultClassicDevices() }
        val roomSwitches = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.roomName.equals(roomName, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType != WeMoDeviceType.MOTION_SENSOR
        }
        val checkedAt = nowFormatted()
        roomSwitches.forEach { dev ->
            val result = networkClient.setBinaryState(
                device = dev,
                targetOn = targetOn,
                targetBrightness = if (dev.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && targetOn && dev.brightness == 0) 100 else null
            )
            appendLog(result.logEntry)
            dao.updateDevice(
                dev.copy(
                    isOn = targetOn,
                    port = result.resolvedPort,
                    latencyMs = result.latencyMs,
                    lastCheckedTime = checkedAt,
                    brightness = if (dev.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && targetOn && dev.brightness == 0) 100 else dev.brightness
                )
            )
        }
        return roomSwitches.size
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
        val currentDevices = dao.getAllDevices().first().ifEmpty { defaultClassicDevices() }
        val roomBulbs = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.roomName.equals(roomName, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER
        }
        val checkedAt = nowFormatted()
        roomBulbs.forEach { bulb ->
            val result = networkClient.setBinaryState(
                device = bulb,
                targetOn = true,
                targetBrightness = clamped
            )
            appendLog(result.logEntry)
            dao.updateDevice(
                bulb.copy(
                    isOn = true,
                    brightness = clamped,
                    port = result.resolvedPort,
                    latencyMs = result.latencyMs,
                    lastCheckedTime = checkedAt
                )
            )
        }
        return roomBulbs.size
    }

    suspend fun updateDeviceBrightness(device: WeMoDeviceEntity, newBrightness: Int) {
        val clamped = newBrightness.coerceIn(1, 100)
        val result = networkClient.setBinaryState(
            device = device,
            targetOn = true,
            targetBrightness = clamped
        )
        appendLog(result.logEntry)
        dao.updateDevice(
            device.copy(
                isOn = true,
                isReachable = true,
                brightness = clamped,
                port = result.resolvedPort,
                latencyMs = result.latencyMs,
                lastCheckedTime = nowFormatted()
            )
        )
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
                dao.updateDevice(
                    target.copy(
                        isOn = rule.targetStateOn,
                        brightness = rule.targetBrightness,
                        lastCheckedTime = now,
                        isReachable = true
                    )
                )
            }
        }
    }

    suspend fun reconnectDevice(device: WeMoDeviceEntity): WeMoDeviceEntity {
        val probe = networkClient.getBinaryState(device)
        appendLog(probe.logEntry)
        val updated = device.copy(
            isReachable = true,
            port = probe.resolvedPort,
            latencyMs = probe.latencyMs.coerceAtLeast(11),
            lastCheckedTime = nowFormatted(),
            isOn = probe.binaryStateOn ?: true
        )
        dao.updateDevice(updated)
        return updated
    }

    suspend fun pingDeviceLatency(device: WeMoDeviceEntity): WeMoDeviceEntity {
        val probe = networkClient.getBinaryState(device)
        appendLog(probe.logEntry)
        val updated = device.copy(
            isReachable = true,
            port = probe.resolvedPort,
            latencyMs = probe.latencyMs.coerceAtLeast(9),
            lastCheckedTime = nowFormatted()
        )
        dao.updateDevice(updated)
        return updated
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
        val allDevs = dao.getAllDevices().first().ifEmpty { defaultClassicDevices() }
        val currentDevices = allDevs.filter { it.networkKey == activeNetworkKey }.ifEmpty { allDevs }
        val report = networkClient.performSsdpDiscoveryScan(
            searchTarget = searchTarget,
            knownLocalDevices = currentDevices
        )
        _latestSsdpReport.value = report
        appendLog(report.logEntry)

        // Update latency & lastCheckedTime for all known devices found in the SSDP scan
        val checkedAt = nowFormatted()
        currentDevices.forEach { existing ->
            val match = report.discoveredDevices.find {
                it.ipAddress == existing.ipAddress || it.name.equals(existing.name, ignoreCase = true)
            }
            if (match != null && existing.isReachable) {
                dao.updateDevice(
                    existing.copy(
                        latencyMs = match.responseTimeMs.toInt().coerceAtLeast(8),
                        lastCheckedTime = checkedAt
                    )
                )
            }
        }

        if (autoAdoptNew) {
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
            it.networkKey == activeNetworkKey &&
                (it.ipAddress == discovered.ipAddress || it.name.equals(discovered.name, ignoreCase = true))
        }
        val checkedAt = nowFormatted()
        return if (existing == null) {
            val newEntity = WeMoDeviceEntity(
                name = discovered.name,
                roomName = discovered.roomName.ifBlank { inferRoomFromName(discovered.name) },
                networkKey = activeNetworkKey,
                wifiSsid = activeWifiSsid,
                deviceType = discovered.deviceType.name,
                ipAddress = discovered.ipAddress,
                port = discovered.port,
                isOn = discovered.isOn,
                isReachable = true,
                brightness = discovered.brightness,
                latencyMs = discovered.responseTimeMs.toInt().coerceAtLeast(9),
                lastCheckedTime = checkedAt,
                serialNumber = discovered.serialNumber,
                firmwareVersion = discovered.firmwareVersion,
                sortOrder = currentDevices.size
            )
            val newId = dao.insertDevice(newEntity).toInt()
            newEntity.copy(id = newId)
        } else {
            val updated = existing.copy(
                ipAddress = discovered.ipAddress,
                port = discovered.port,
                isReachable = true,
                latencyMs = discovered.responseTimeMs.toInt().coerceAtLeast(9),
                lastCheckedTime = checkedAt,
                serialNumber = discovered.serialNumber,
                firmwareVersion = discovered.firmwareVersion
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
        val currentDevices = dao.getAllDevices().first()
        var newlyAdded = 0
        val checkedAt = nowFormatted()
        for (item in discoveredList) {
            val existing = currentDevices.find {
                it.networkKey == activeNetworkKey &&
                    (it.ipAddress == item.ipAddress || it.name.equals(item.name, ignoreCase = true))
            }
            if (existing == null) {
                dao.insertDevice(
                    WeMoDeviceEntity(
                        name = item.name,
                        roomName = item.roomName.ifBlank { inferRoomFromName(item.name) },
                        networkKey = activeNetworkKey,
                        wifiSsid = activeWifiSsid,
                        deviceType = item.deviceType.name,
                        ipAddress = item.ipAddress,
                        port = item.port,
                        isOn = item.isOn,
                        isReachable = true,
                        brightness = item.brightness,
                        latencyMs = item.responseTimeMs.toInt().coerceAtLeast(9),
                        lastCheckedTime = checkedAt,
                        serialNumber = item.serialNumber,
                        firmwareVersion = item.firmwareVersion,
                        sortOrder = currentDevices.size + newlyAdded
                    )
                )
                newlyAdded++
            } else if (!existing.isReachable) {
                dao.updateDevice(
                    existing.copy(
                        isReachable = true,
                        port = item.port,
                        latencyMs = item.responseTimeMs.toInt().coerceAtLeast(9),
                        lastCheckedTime = checkedAt
                    )
                )
            }
        }
        return newlyAdded
    }

    suspend fun saveDevice(device: WeMoDeviceEntity) {
        if (device.id == 0) {
            val count = dao.getDeviceCount()
            dao.insertDevice(device.copy(sortOrder = count))
        } else {
            dao.updateDevice(device)
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
            val res = networkClient.setBinaryState(
                device = target,
                targetOn = rule.targetStateOn,
                targetBrightness = if (target.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER) rule.targetBrightness else null
            )
            appendLog(res.logEntry)
            dao.updateDevice(
                target.copy(
                    isOn = rule.targetStateOn,
                    brightness = if (target.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER) rule.targetBrightness else target.brightness,
                    latencyMs = res.latencyMs,
                    lastCheckedTime = nowFormatted(),
                    isReachable = true
                )
            )
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
        val DEFAULT_ROOMS = listOf(
            "Living Room",
            "Bedroom",
            "Kitchen",
            "Hallway & Landing",
            "Ensuite",
            "Outdoor",
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
