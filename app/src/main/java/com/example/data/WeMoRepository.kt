package com.example.data

import com.example.network.WeMoNetworkClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeMoRepository(
    private val dao: WeMoDao,
    private val networkClient: WeMoNetworkClient = WeMoNetworkClient()
) {
    private val initialSeedDone = MutableStateFlow(false)

    val allDevices: Flow<List<WeMoDeviceEntity>> = kotlinx.coroutines.flow.combine(
        dao.getAllDevices(),
        initialSeedDone
    ) { list, seeded ->
        if (list.isEmpty() && !seeded) defaultClassicDevices() else list
    }

    val allRules: Flow<List<WeMoRuleEntity>> = kotlinx.coroutines.flow.combine(
        dao.getAllRules(),
        initialSeedDone
    ) { list, seeded ->
        if (list.isEmpty() && !seeded) defaultClassicRules() else list
    }

    private val _soapLogs = MutableStateFlow<List<SoapLogEntry>>(emptyList())
    val soapLogs: StateFlow<List<SoapLogEntry>> = _soapLogs.asStateFlow()

    private fun appendLog(entry: SoapLogEntry) {
        _soapLogs.value = (listOf(entry) + _soapLogs.value).take(40)
    }

    /**
     * Seeds the exact 8 devices from the user's classic WeMo screenshot if the database is empty.
     */
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
            brightness = if (device.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && nextOn && device.brightness == 0) 100 else device.brightness
        )
        dao.updateDevice(updated)
        return updated
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
                port = result.resolvedPort
            )
        )
    }

    suspend fun triggerMotionEvent(device: WeMoDeviceEntity) {
        val nowFormatted = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
        val updated = device.copy(
            lastMotionTime = nowFormatted,
            isReachable = true
        )
        dao.updateDevice(updated)

        // Also execute any enabled MOTION_TRIGGER rules
        val rules = dao.getAllRules().first()
        rules.filter { it.isEnabled && it.ruleType == "MOTION_TRIGGER" }.forEach { rule ->
            val target = dao.getDeviceById(rule.targetDeviceId)
            if (target != null) {
                dao.updateDevice(
                    target.copy(
                        isOn = rule.targetStateOn,
                        brightness = rule.targetBrightness,
                        isReachable = true
                    )
                )
            }
        }
    }

    suspend fun reconnectDevice(device: WeMoDeviceEntity) {
        val probe = networkClient.getBinaryState(device)
        appendLog(probe.logEntry)
        dao.updateDevice(
            device.copy(
                isReachable = true,
                port = probe.resolvedPort,
                isOn = probe.binaryStateOn ?: true
            )
        )
    }

    suspend fun setDeviceReachableState(device: WeMoDeviceEntity, reachable: Boolean) {
        dao.updateDevice(device.copy(isReachable = reachable))
    }

    suspend fun refreshAndDiscoverAll(): Int {
        val (discoveredLan, discoveryLog) = networkClient.discoverDevicesOnLan()
        appendLog(discoveryLog)

        val currentDevices = dao.getAllDevices().first()
        var newlyAdded = 0
        for (lanDev in discoveredLan) {
            val existing = currentDevices.find {
                it.ipAddress == lanDev.ipAddress || it.name.equals(lanDev.name, ignoreCase = true)
            }
            if (existing == null) {
                dao.insertDevice(
                    WeMoDeviceEntity(
                        name = lanDev.name,
                        deviceType = lanDev.deviceType.name,
                        ipAddress = lanDev.ipAddress,
                        port = lanDev.port,
                        isOn = true,
                        isReachable = true,
                        brightness = 100,
                        serialNumber = lanDev.serialNumber,
                        sortOrder = currentDevices.size + newlyAdded
                    )
                )
                newlyAdded++
            } else {
                val stateRes = networkClient.getBinaryState(existing)
                appendLog(stateRes.logEntry)
                if (stateRes.reachableOnLan) {
                    dao.updateDevice(
                        existing.copy(
                            isOn = stateRes.binaryStateOn ?: existing.isOn,
                            brightness = stateRes.brightness ?: existing.brightness,
                            port = stateRes.resolvedPort,
                            isReachable = true
                        )
                    )
                }
            }
        }

        // Also probe the first active switch so the user sees a real GetBinaryState SOAP check in the log
        currentDevices.firstOrNull()?.let { firstDevice ->
            val res = networkClient.getBinaryState(firstDevice)
            appendLog(res.logEntry)
            if (res.reachableOnLan) {
                dao.updateDevice(
                    firstDevice.copy(
                        isOn = res.binaryStateOn ?: firstDevice.isOn,
                        port = res.resolvedPort,
                        isReachable = true
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
        fun defaultClassicDevices(): List<WeMoDeviceEntity> = listOf(
            WeMoDeviceEntity(
                id = 1,
                name = "Christmas Tree Living",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.1.101",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                serialNumber = "221517K01018A1",
                sortOrder = 0
            ),
            WeMoDeviceEntity(
                id = 2,
                name = "Christmas Candle",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.1.102",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                serialNumber = "221517K01018A2",
                sortOrder = 1
            ),
            WeMoDeviceEntity(
                id = 3,
                name = "Bedroom Light Motion",
                deviceType = WeMoDeviceType.MOTION_SENSOR.name,
                ipAddress = "192.168.1.103",
                port = 49153,
                isOn = true,
                isReachable = true,
                lastMotionTime = "4:15:15 PM",
                serialNumber = "221432M02004B9",
                sortOrder = 2
            ),
            WeMoDeviceEntity(
                id = 4,
                name = "Christmas Tree Kitchen",
                deviceType = WeMoDeviceType.SWITCH_PLUG.name,
                ipAddress = "192.168.1.104",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100,
                serialNumber = "221517K01018A4",
                sortOrder = 3
            ),
            WeMoDeviceEntity(
                id = 5,
                name = "Hall Lightbulb",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.105",
                port = 49153,
                isOn = false,
                isReachable = false, // Matches "Not Detected" in screenshot
                brightness = 100,
                serialNumber = "94103EA2B27801",
                sortOrder = 4
            ),
            WeMoDeviceEntity(
                id = 6,
                name = "Lightbulb Landing",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.106",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 21, // Matches "21%" in screenshot
                serialNumber = "94103EA2B27802",
                sortOrder = 5
            ),
            WeMoDeviceEntity(
                id = 7,
                name = "Lightbulb Ensuite",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.107",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100, // Matches "100%" in screenshot
                serialNumber = "94103EA2B27803",
                sortOrder = 6
            ),
            WeMoDeviceEntity(
                id = 8,
                name = "Lightbulb Bedroom",
                deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.108",
                port = 49153,
                isOn = true,
                isReachable = true,
                brightness = 100, // Matches "100%" in screenshot
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
