package org.suppressingfire.wemi.data

import java.util.concurrent.ConcurrentHashMap
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

/**
 * Coordinates Room v5 CRUD operations, manual-IP 9-port hardware enrichment (`saveDevice`),
 * NVRAM friendly-name synchronization (`ChangeFriendlyName`), room-wide batch power/brightness
 * fan-out, and local schedule/motion rule execution on behalf of [SwitchRepository].
 */
internal class DeviceCrudAndRulesCoordinator(
    private val dao: SwitchDao,
    private val networkClient: NetworkClient,
    private val lanSemaphore: Semaphore,
    private val deviceMutexes: ConcurrentHashMap<String, Mutex>,
    private val isDemoShowroomMode: () -> Boolean,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String,
    private val setDevicePowerExplicit: suspend (DeviceEntity, Boolean) -> DeviceEntity,
    private val updateDeviceBrightness: suspend (DeviceEntity, Int) -> DeviceEntity
) {
    private fun mutexFor(device: DeviceEntity): Mutex =
        deviceMutexes.getOrPut(device.concurrencyKey()) { Mutex() }

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
        val existingHome = existingKey?.let { targetKey ->
            currentNetworks.find { it.networkKey == targetKey }
        }
        val nextSortOrder = existingHome?.sortOrder
            ?: ((currentNetworks.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        val entity = HomeNetworkEntity(
            networkKey = key,
            homeName = cleanHome,
            ssidLabel = cleanSsid,
            subnetPrefix = cleanSubnet,
            gatewayIp = "$cleanSubnet.1",
            lastConnectedTime = "Connected now",
            autoReconnectOnArrival = existingHome?.autoReconnectOnArrival ?: true,
            sortOrder = nextSortOrder
        )
        dao.insertHomeNetwork(entity)
        return entity
    }

    suspend fun toggleRoomPower(
        roomName: String,
        targetOn: Boolean,
        networkKey: String? = null
    ): Int = coroutineScope {
        val normalizedRoom = normalizeRoomName(roomName)
        val demoMode = isDemoShowroomMode()
        val currentDevices = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultClassicDevices() else it
        }
        val roomSwitches = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.normalizedRoomName.equals(normalizedRoom, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType != DeviceType.MOTION_SENSOR
        }
        roomSwitches.map { dev ->
            async {
                setDevicePowerExplicit(dev, targetOn).isReachable
            }
        }.awaitAll().count { it }
    }

    suspend fun updateRoomBrightness(
        roomName: String,
        newBrightness: Int,
        networkKey: String? = null
    ): Int = coroutineScope {
        val normalizedRoom = normalizeRoomName(roomName)
        val clamped = newBrightness.coerceIn(1, 100)
        val demoMode = isDemoShowroomMode()
        val currentDevices = dao.getAllDevices().first().let {
            if (it.isEmpty() && demoMode) DemoFixtures.defaultClassicDevices() else it
        }
        val roomBulbs = currentDevices.filter {
            (networkKey == null || it.networkKey == networkKey) &&
                it.normalizedRoomName.equals(normalizedRoom, ignoreCase = true) &&
                it.isReachable &&
                it.parsedType == DeviceType.LIGHTBULB_DIMMER
        }
        roomBulbs.map { bulb ->
            async {
                updateDeviceBrightness(bulb, clamped)
            }
        }.awaitAll()
        roomBulbs.size
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
        val demoMode = isDemoShowroomMode()
        val isSyntheticDemoEndpoint =
            demoMode && device.ipAddress != "127.0.0.1" && device.ipAddress != "localhost"
        val hasPlaceholderSerial = LanRuntimeUtils.isPlaceholderSerial(device.serialNumber)

        val liveDesc = if (!isSyntheticDemoEndpoint && (device.id == 0 || hasPlaceholderSerial)) {
            lanSemaphore.withPermit {
                networkClient.probeAndFetchDeviceDescriptor(
                    ipAddress = device.ipAddress,
                    preferredPort = device.port
                )
            }
        } else {
            null
        }

        val existingMatch = if (device.id == 0 && liveDesc != null) {
            val cleanLiveUdn = LanRuntimeUtils.canonicalHardwareUdn(liveDesc.usn)
            val existingInHome = dao.getAllDevices().first().filter { it.networkKey == device.networkKey }
            existingInHome.find { existing ->
                (!LanRuntimeUtils.isPlaceholderSerial(liveDesc.serialNumber) &&
                    !LanRuntimeUtils.isPlaceholderSerial(existing.serialNumber) &&
                    existing.serialNumber.equals(liveDesc.serialNumber, ignoreCase = true)) ||
                    ((LanRuntimeUtils.isPlaceholderSerial(liveDesc.serialNumber) ||
                        LanRuntimeUtils.isPlaceholderSerial(existing.serialNumber)) &&
                        cleanLiveUdn.isNotEmpty() &&
                        cleanLiveUdn.equals(LanRuntimeUtils.canonicalHardwareUdn(existing.udn), ignoreCase = true))
            }
        } else {
            null
        }

        suspend fun buildAndPersist(liveRow: DeviceEntity?): DeviceEntity {
            val enriched = if (liveDesc != null) {
                val baseEntity = existingMatch ?: liveRow ?: device
                val resolvedName = device.name.trim().ifBlank {
                    liveDesc.name.ifBlank { baseEntity.name.ifBlank { "WeMi Light Switch" } }
                }
                val resolvedRoom = if (device.name.isBlank() &&
                    (device.roomName.isBlank() || device.roomName == "Living Room" || device.roomName == "Unassigned")
                ) {
                    if (existingMatch != null) {
                        resolveDeviceRoomAssignment(
                            existingRoomName = existingMatch.roomName,
                            deviceName = resolvedName,
                            discoveredRoomName = liveDesc.roomName,
                            isDemoMode = demoMode
                        )
                    } else {
                        liveDesc.roomName.ifBlank { inferRoomFromName(resolvedName) }
                    }
                } else {
                    device.roomName.trim().ifBlank { liveDesc.roomName.ifBlank { inferRoomFromName(resolvedName) } }
                }
                val resolvedType = if (device.name.isBlank()) {
                    liveDesc.deviceType.name
                } else {
                    device.deviceType
                }
                val cleanLiveSsid = liveDesc.wifiSsid.trim().takeIf {
                    it.isNotEmpty() && !org.suppressingfire.wemi.network.XmlAndSkuParser.isWemoDeviceSetupSsid(it)
                }
                baseEntity.copy(
                    name = resolvedName,
                    roomName = resolvedRoom,
                    networkKey = device.networkKey,
                    deviceType = resolvedType,
                    ipAddress = device.ipAddress,
                    port = liveDesc.port,
                    isReachable = true,
                    isOn = liveDesc.isOn,
                    brightness = liveDesc.brightness,
                    latencyMs = liveDesc.responseTimeMs.toInt().coerceAtLeast(1),
                    lastCheckedTime = nowFormatted(),
                    serialNumber = liveDesc.serialNumber
                        .takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) }
                        ?: baseEntity.serialNumber.takeUnless { !demoMode && LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty(),
                    firmwareVersion = liveDesc.firmwareVersion.ifBlank { baseEntity.firmwareVersion },
                    hardwareModel = liveDesc.hardwareSku.ifBlank { baseEntity.hardwareModel },
                    macAddress = liveDesc.macAddress.ifBlank { baseEntity.macAddress },
                    udn = LanRuntimeUtils.canonicalHardwareUdn(liveDesc.usn)
                        .ifBlank { LanRuntimeUtils.canonicalHardwareUdn(baseEntity.udn) },
                    wifiSsid = cleanLiveSsid ?: device.wifiSsid.ifBlank { baseEntity.wifiSsid },
                    signalStrengthPercent = liveDesc.signalStrengthPercent,
                    autoOffMinutes = if (device.id == 0 && device.autoOffMinutes == 0) {
                        baseEntity.autoOffMinutes
                    } else {
                        device.autoOffMinutes
                    }
                )
            } else {
                val fallbackName = device.name.trim().ifBlank { liveRow?.name?.ifBlank { null } ?: "WeMi Light Switch" }
                val fallbackRoom = device.roomName.trim().let { rawRoom ->
                    if (rawRoom.isBlank() || (device.name.isBlank() && rawRoom == "Living Room")) {
                        inferRoomFromName(fallbackName)
                    } else {
                        rawRoom
                    }
                }
                val baseEntity = liveRow ?: device
                val preservedSerial = baseEntity.serialNumber
                    .takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) }
                    ?: if (hasPlaceholderSerial && !demoMode) "" else device.serialNumber
                baseEntity.copy(
                    name = fallbackName,
                    roomName = fallbackRoom,
                    networkKey = device.networkKey,
                    wifiSsid = device.wifiSsid.ifBlank { baseEntity.wifiSsid },
                    deviceType = device.deviceType,
                    ipAddress = device.ipAddress,
                    port = device.port,
                    isOn = device.isOn,
                    isReachable = device.isReachable,
                    brightness = device.brightness,
                    serialNumber = preservedSerial,
                    autoOffMinutes = device.autoOffMinutes
                )
            }

            return if (enriched.id == 0) {
                val nextSortOrder = (
                    dao.getAllDevices().first()
                        .filter { it.networkKey == enriched.networkKey }
                        .maxOfOrNull { it.sortOrder }
                        ?: -1
                    ) + 1
                val toInsert = enriched.copy(sortOrder = nextSortOrder)
                val newId = dao.insertDevice(toInsert).toInt()
                toInsert.copy(id = newId)
            } else {
                dao.updateDevice(enriched)
                enriched
            }
        }

        val lockTarget = existingMatch ?: device.takeIf { it.id != 0 }
        var saved = if (lockTarget != null) {
            mutexFor(lockTarget).withLock {
                val liveRow = dao.getDeviceById(lockTarget.id)
                buildAndPersist(liveRow)
            }
        } else {
            buildAndPersist(null)
        }

        if (syncFriendlyNameToHardware && device.name.isNotBlank() && !isSyntheticDemoEndpoint) {
            val renameResult = lanSemaphore.withPermit {
                networkClient.changeFriendlyName(saved, saved.name)
            }
            appendLog(renameResult.logEntry)
            if (renameResult.reachableOnLan && renameResult.resolvedPort != saved.port) {
                saved = mutexFor(saved).withLock {
                    val latest = dao.getDeviceById(saved.id) ?: saved
                    val healed = latest.copy(
                        port = renameResult.resolvedPort,
                        isReachable = true,
                        latencyMs = renameResult.latencyMs.coerceAtLeast(1),
                        lastCheckedTime = nowFormatted()
                    )
                    dao.updateDevice(healed)
                    healed
                }
            }
        }
        return saved
    }

    suspend fun deleteDevice(id: Int) {
        dao.deleteDeviceById(id)
    }

    /**
     * Moves [device] up or down within its Wi-Fi Home (`networkKey`), and when [withinRoomOnly]
     * is `true` (Grouped-by-Room or single-room filter view), swaps it with the adjacent device
     * in the same normalized `roomName` so grouped rows move immediately without scrambling room
     * header order or crossing Wi-Fi Home boundaries.
     */
    suspend fun moveDeviceOrder(
        device: DeviceEntity,
        moveUp: Boolean,
        withinRoomOnly: Boolean = false
    ) {
        val targetRoom = device.normalizedRoomName
        val homeDevices = dao.getAllDevices().first()
            .filter { it.networkKey == device.networkKey }
            .toMutableList()
        val scopedIndices = homeDevices.indices.filter { idx ->
            !withinRoomOnly || homeDevices[idx].normalizedRoomName
                .equals(targetRoom, ignoreCase = true)
        }
        val posInScope = scopedIndices.indexOfFirst { idx -> homeDevices[idx].id == device.id }
        if (posInScope == -1) return
        val targetScopePos = if (moveUp) posInScope - 1 else posInScope + 1
        if (targetScopePos !in scopedIndices.indices) return

        val fromIdx = scopedIndices[posInScope]
        val toIdx = scopedIndices[targetScopePos]
        val temp = homeDevices[fromIdx]
        homeDevices[fromIdx] = homeDevices[toIdx]
        homeDevices[toIdx] = temp

        homeDevices.forEachIndexed { normalizedOrder, entity ->
            if (entity.sortOrder != normalizedOrder) {
                dao.updateDevice(entity.copy(sortOrder = normalizedOrder))
            }
        }
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
