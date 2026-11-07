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
        dao.insertDevices(defaultClassicDevices())
        dao.insertRules(defaultClassicRules())
    }

    private fun nowFormatted(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
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
    suspend fun toggleRoomPower(roomName: String, targetOn: Boolean): Int {
        val currentDevices = dao.getAllDevices().first().ifEmpty { defaultClassicDevices() }
        val roomSwitches = currentDevices.filter {
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
    suspend fun updateRoomBrightness(roomName: String, newBrightness: Int): Int {
        val clamped = newBrightness.coerceIn(1, 100)
        val currentDevices = dao.getAllDevices().first().ifEmpty { defaultClassicDevices() }
        val roomBulbs = currentDevices.filter {
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
        autoAdoptNew: Boolean = false
    ): SsdpScanReport {
        val currentDevices = dao.getAllDevices().first().ifEmpty { defaultClassicDevices() }
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
            adoptDiscoveredDevices(report.discoveredDevices)
        }
        return report
    }

    suspend fun adoptDiscoveredDevice(discovered: SsdpDiscoveredDevice): WeMoDeviceEntity {
        val currentDevices = dao.getAllDevices().first()
        val existing = currentDevices.find {
            it.ipAddress == discovered.ipAddress || it.name.equals(discovered.name, ignoreCase = true)
        }
        val checkedAt = nowFormatted()
        return if (existing == null) {
            val newEntity = WeMoDeviceEntity(
                name = discovered.name,
                roomName = discovered.roomName.ifBlank { inferRoomFromName(discovered.name) },
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

    suspend fun adoptDiscoveredDevices(discoveredList: List<SsdpDiscoveredDevice>): Int {
        val currentDevices = dao.getAllDevices().first()
        var newlyAdded = 0
        val checkedAt = nowFormatted()
        for (item in discoveredList) {
            val existing = currentDevices.find {
                it.ipAddress == item.ipAddress || it.name.equals(item.name, ignoreCase = true)
            }
            if (existing == null) {
                dao.insertDevice(
                    WeMoDeviceEntity(
                        name = item.name,
                        roomName = item.roomName.ifBlank { inferRoomFromName(item.name) },
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

        fun defaultClassicDevices(): List<WeMoDeviceEntity> = listOf(
            WeMoDeviceEntity(
                id = 1,
                name = "Christmas Tree Living",
                roomName = "Living Room",
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
