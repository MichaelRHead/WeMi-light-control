package org.suppressingfire.wemi.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkClient

/**
 * Coordinates Room v5 CRUD operations, manual-IP 9-port hardware enrichment (`saveDevice`),
 * NVRAM friendly-name synchronization (`ChangeFriendlyName`), room-wide batch power/brightness
 * fan-out, and local schedule/motion rule execution on behalf of [SwitchRepository].
 */
internal class DeviceCrudAndRulesCoordinator(
    private val dao: SwitchDao,
    private val networkClient: NetworkClient,
    private val lanSemaphore: Semaphore,
    private val isDemoShowroomMode: () -> Boolean,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String,
    private val setDevicePowerExplicit: suspend (DeviceEntity, Boolean) -> DeviceEntity,
    private val updateDeviceBrightness: suspend (DeviceEntity, Int) -> DeviceEntity
) {

    suspend fun saveOrUpdateHomeNetwork(
        existingKey: String?,
        homeName: String,
        ssidLabel: String,
        subnetPrefix: String
    ): HomeNetworkEntity {
        val cleanHome = homeName.trim().ifEmpty { "My Home Wi-Fi" }
        val cleanSsid = ssidLabel.trim().ifEmpty { "Home-WiFi-5G" }
        val cleanSubnet = subnetPrefix.trim()
            .removeSuffix(".0/24")
            .removeSuffix(".x")
            .ifEmpty { "192.168.1" }
        val key = existingKey ?: "wifi_custom_${cleanSubnet.replace('.', '_')}_${System.currentTimeMillis() % 10000}"
        val currentNetworks = dao.getAllHomeNetworks().first()
        val entity = HomeNetworkEntity(
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

    suspend fun toggleRoomPower(
        roomName: String,
        targetOn: Boolean,
        networkKey: String? = null
    ): Int {
        val demoMode = isDemoShowroomMode()
        val currentDevices = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultClassicDevices() else it
        }
        val roomSwitches = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.roomName.equals(roomName, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType != DeviceType.MOTION_SENSOR
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
        val demoMode = isDemoShowroomMode()
        val currentDevices = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultClassicDevices() else it
        }
        val roomBulbs = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.roomName.equals(roomName, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType == DeviceType.LIGHTBULB_DIMMER
        }
        roomBulbs.forEach { bulb ->
            updateDeviceBrightness(bulb, clamped)
        }
        return roomBulbs.size
    }

    suspend fun triggerMotionEvent(device: DeviceEntity) {
        val now = nowFormatted()
        val updated = device.copy(
            lastMotionTime = now,
            lastCheckedTime = now,
            latencyMs = device.latencyMs.takeIf { it > 0 } ?: 15,
            isReachable = true
        )
        dao.updateDevice(updated)

        val rules = dao.getAllRules().first()
        rules.filter { it.isEnabled && it.parsedRuleType == RuleType.MOTION_TRIGGER }.forEach { rule ->
            val target = dao.getDeviceById(rule.targetDeviceId)
            if (target != null) {
                setDevicePowerExplicit(target, rule.targetStateOn)
            }
        }
    }

    suspend fun setDeviceReachableState(device: DeviceEntity, reachable: Boolean) {
        dao.updateDevice(
            device.copy(
                isReachable = reachable,
                latencyMs = if (reachable) (device.latencyMs.takeIf { it > 0 } ?: 14) else 0,
                lastCheckedTime = nowFormatted()
            )
        )
    }

    suspend fun saveDevice(
        device: DeviceEntity,
        syncFriendlyNameToHardware: Boolean = false
    ): DeviceEntity {
        val hasPlaceholderSerial = LanRuntimeUtils.isPlaceholderSerial(device.serialNumber)

        val liveDesc = if (device.id == 0 || hasPlaceholderSerial) {
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
        } else if (hasPlaceholderSerial && !isDemoShowroomMode()) {
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

    suspend fun moveDeviceOrder(device: DeviceEntity, moveUp: Boolean) {
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

    suspend fun toggleRule(rule: RuleEntity) {
        dao.updateRule(rule.copy(isEnabled = !rule.isEnabled))
    }

    suspend fun runRuleNow(rule: RuleEntity) {
        val devices = dao.getAllDevices().first()
        val target = devices.find { it.id == rule.targetDeviceId }
            ?: devices.find { it.name.equals(rule.targetDeviceName, ignoreCase = true) }
        if (target != null) {
            if (target.parsedType == DeviceType.LIGHTBULB_DIMMER && rule.targetStateOn) {
                updateDeviceBrightness(target, rule.targetBrightness)
            } else {
                setDevicePowerExplicit(target, rule.targetStateOn)
            }
        }
    }

    suspend fun addRule(rule: RuleEntity) {
        dao.insertRule(rule)
    }

    suspend fun deleteRule(id: Int) {
        dao.deleteRuleById(id)
    }
}
