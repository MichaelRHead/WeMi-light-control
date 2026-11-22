package org.suppressingfire.wemi.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareLocationInfo
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleTimePoint
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HardwareRulesClient

/**
 * Coordinates lazy, concurrency-bounded (`Semaphore(3)` + per-device `Mutex`) Tier-2 hardware
 * rules capability verification and read-only `rules.db` extraction across switches in the active
 * Wi-Fi Home.
 *
 * Caching verified capabilities against each switch's `firmwareVersion` ensures repeated scans
 * never waste TCP sockets re-probing RTOS devices (`WDS060`, `WSP080`, `WSP090`) that omit
 * `FetchRules` from `/rulesservice.xml`.
 */
internal class HardwareRulesCoordinator(
    private val dao: SwitchDao,
    private val hardwareRulesClient: HardwareRulesClient,
    private val lanSemaphore: Semaphore,
    private val deviceMutexes: ConcurrentHashMap<String, Mutex>,
    private val isDemoShowroomMode: () -> Boolean,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String
) {
    private val _hardwareRulesByDeviceId = MutableStateFlow<Map<Int, DeviceHardwareRulesSnapshot>>(emptyMap())
    val hardwareRulesByDeviceId: StateFlow<Map<Int, DeviceHardwareRulesSnapshot>> =
        _hardwareRulesByDeviceId.asStateFlow()

    private val _inspectingDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val inspectingDeviceIds: StateFlow<Set<Int>> = _inspectingDeviceIds.asStateFlow()

    private val _isScanningAll = MutableStateFlow(false)
    val isScanningAll: StateFlow<Boolean> = _isScanningAll.asStateFlow()

    private fun mutexFor(device: DeviceEntity): Mutex =
        deviceMutexes.getOrPut(device.concurrencyKey()) { Mutex() }

    fun clearRuntimeState() {
        _hardwareRulesByDeviceId.value = emptyMap()
        _inspectingDeviceIds.value = emptySet()
        _isScanningAll.value = false
    }

    fun evictDevice(deviceId: Int) {
        _hardwareRulesByDeviceId.update { if (deviceId in it) it - deviceId else it }
        _inspectingDeviceIds.update { if (deviceId in it) it - deviceId else it }
    }

    /**
     * Reads existing on-device rules from [device] in strict read-only mode, updating Room's
     * cached capability columns when Tier-2 verification returns an authoritative verdict.
     */
    suspend fun inspectDeviceHardwareRules(
        device: DeviceEntity,
        forceRecheck: Boolean = false
    ): DeviceHardwareRulesSnapshot {
        val demoMode = isDemoShowroomMode()
        val isSyntheticDemoEndpoint =
            demoMode && device.ipAddress != "127.0.0.1" && device.ipAddress != "localhost"
        if (isSyntheticDemoEndpoint) {
            val allDevices = dao.getAllDevices().first()
            val demoSnap = buildDemoSnapshot(device, allDevices)
            persistCapabilityVerdict(device.id, demoSnap.capability, demoSnap.longPressCapable)
            _hardwareRulesByDeviceId.update { it + (device.id to demoSnap) }
            return demoSnap
        }

        if (!forceRecheck &&
            device.isHardwareRulesCapabilityFresh &&
            device.parsedHardwareRulesCapability == HardwareRulesCapability.VERIFIED_UNSUPPORTED
        ) {
            val cached = _hardwareRulesByDeviceId.value[device.id] ?: DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_UNSUPPORTED,
                longPressCapable = false,
                dbVersion = 0,
                diagnosticMessage = "Cached firmware verdict (${device.firmwareVersion}): no FetchRules/StoreRules support",
                inspectedAt = nowFormatted()
            )
            _hardwareRulesByDeviceId.update { it + (device.id to cached) }
            return cached
        }

        _inspectingDeviceIds.update { it + device.id }
        return try {
            val lock = mutexFor(device)
            lock.withLock {
                val fresh = dao.getDeviceById(device.id) ?: device
                val (snapshot, logs) = lanSemaphore.withPermit {
                    hardwareRulesClient.fetchDeviceRulesSnapshot(fresh)
                }
                logs.forEach(appendLog)
                if (snapshot.capability.isVerified) {
                    persistCapabilityVerdictLocked(fresh.id, snapshot.capability, snapshot.longPressCapable)
                }
                _hardwareRulesByDeviceId.update { it + (fresh.id to snapshot) }
                snapshot
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            _inspectingDeviceIds.update { it - device.id }
        }
    }

    /**
     * Reads on-device rules across all reachable switches in [networkKey] bounded by `Semaphore(3)`.
     * Skips already-verified unsupported switches for the same firmware unless [forceRecheck] is true.
     */
    suspend fun inspectAllHomeHardwareRules(
        networkKey: String,
        forceRecheck: Boolean = false
    ): List<DeviceHardwareRulesSnapshot> {
        _isScanningAll.value = true
        return try {
            val homeDevices = dao.getAllDevices().first()
                .filter { it.networkKey == networkKey && it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR }
            coroutineScope {
                homeDevices.map { dev ->
                    async { inspectDeviceHardwareRules(dev, forceRecheck = forceRecheck) }
                }.awaitAll()
            }
        } finally {
            _isScanningAll.value = false
        }
    }

    private suspend fun persistCapabilityVerdict(
        deviceId: Int,
        capability: HardwareRulesCapability,
        longPressCapable: Boolean
    ) {
        val current = dao.getDeviceById(deviceId) ?: return
        mutexFor(current).withLock {
            persistCapabilityVerdictLocked(deviceId, capability, longPressCapable)
        }
    }

    private suspend fun persistCapabilityVerdictLocked(
        deviceId: Int,
        capability: HardwareRulesCapability,
        longPressCapable: Boolean
    ) {
        val fresh = dao.getDeviceById(deviceId) ?: return
        val nextVerifiedFw = if (capability.isVerified) fresh.firmwareVersion.trim() else fresh.hardwareRulesVerifiedFirmware
        if (fresh.hardwareRulesCapability != capability.name ||
            fresh.hardwareRulesLongPressCapable != longPressCapable ||
            fresh.hardwareRulesVerifiedFirmware != nextVerifiedFw
        ) {
            dao.updateDevice(
                fresh.copy(
                    hardwareRulesCapability = capability.name,
                    hardwareRulesLongPressCapable = longPressCapable,
                    hardwareRulesVerifiedFirmware = nextVerifiedFw
                )
            )
        }
    }

    private fun buildDemoSnapshot(
        device: DeviceEntity,
        allDevices: List<DeviceEntity>
    ): DeviceHardwareRulesSnapshot {
        val isRtosDimmer = device.hardwareModel.contains("WDS060", ignoreCase = true) ||
            device.firmwareVersion.contains("-RTOS-", ignoreCase = true)
        if (isRtosDimmer) {
            return DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_UNSUPPORTED,
                longPressCapable = false,
                dbVersion = 0,
                diagnosticMessage = "RTOS firmware does not expose FetchRules (Dimmer Night Mode still available)",
                inspectedAt = nowFormatted()
            )
        }
        val selfUdn = device.udn.ifBlank { "uuid:Lightswitch-1_0-${device.serialNumber}" }
        val peerTargets = allDevices
            .filter { it.networkKey == device.networkKey && it.id != device.id && it.parsedType != DeviceType.MOTION_SENSOR }
            .take(2)
            .map { it.udn.ifBlank { "uuid:Lightswitch-1_0-${it.serialNumber}" } }
        val rules = buildList {
            if (device.parsedType == DeviceType.SWITCH_PLUG || device.hardwareModel.contains("F7C059", ignoreCase = true)) {
                add(
                    HardwareRule(
                        ruleId = device.id * 10 + 1,
                        name = "Goodnight Long Press",
                        type = HardwareRuleType.LONG_PRESS,
                        rawFirmwareType = "Long Press",
                        isEnabled = true,
                        ruleOrder = 1,
                        ownerDeviceUdn = selfUdn,
                        daysOfWeek = listOf(-1),
                        startTime = HardwareRuleTimePoint(60),
                        endTime = HardwareRuleTimePoint(86400),
                        ruleDurationSeconds = 86340,
                        startAction = 0.0,
                        endAction = -1.0,
                        countdownSeconds = 0,
                        sensorDurationSeconds = 0,
                        targetDeviceUdns = peerTargets
                    )
                )
            }
            if (device.autoOffMinutes > 0 || device.id % 2 == 1) {
                add(
                    HardwareRule(
                        ruleId = device.id * 10 + 2,
                        name = "${device.name} Auto-Off",
                        type = HardwareRuleType.COUNTDOWN_TIMER,
                        rawFirmwareType = "Countdown Rule",
                        isEnabled = true,
                        ruleOrder = 2,
                        ownerDeviceUdn = selfUdn,
                        daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                        startTime = HardwareRuleTimePoint(0),
                        endTime = HardwareRuleTimePoint(86340),
                        ruleDurationSeconds = 86340,
                        startAction = 0.0,
                        endAction = -1.0,
                        countdownSeconds = (device.autoOffMinutes.takeIf { it > 0 } ?: 30) * 60,
                        sensorDurationSeconds = 0
                    )
                )
            }
            if (device.name.contains("Porch", ignoreCase = true) || device.name.contains("Living", ignoreCase = true)) {
                add(
                    HardwareRule(
                        ruleId = device.id * 10 + 3,
                        name = "Evening Sunset Schedule",
                        type = HardwareRuleType.SCHEDULE_INTERVAL,
                        rawFirmwareType = "Time Interval",
                        isEnabled = true,
                        ruleOrder = 3,
                        ownerDeviceUdn = selfUdn,
                        daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                        startTime = HardwareRuleTimePoint(rawSeconds = 64802, offsetSeconds = -900),
                        endTime = HardwareRuleTimePoint(rawSeconds = 82800),
                        ruleDurationSeconds = 18000,
                        startAction = 1.0,
                        endAction = 0.0,
                        countdownSeconds = 0,
                        sensorDurationSeconds = 0
                    )
                )
            }
        }
        return DeviceHardwareRulesSnapshot(
            deviceId = device.id,
            deviceName = device.name,
            deviceUdn = selfUdn,
            capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
            longPressCapable = true,
            dbVersion = 4,
            rules = rules,
            locationInfo = HardwareLocationInfo("Mountain View", "CA", "US", "37.3861", "-122.0839"),
            existingTables = setOf("RULES", "RULEDEVICES", "TARGETDEVICES", "LOCATIONINFO"),
            integrityOk = true,
            diagnosticMessage = "Demo showcase snapshot (${rules.size} on-device rule(s))",
            inspectedAt = nowFormatted()
        )
    }
}
