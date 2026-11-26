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
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HardwareRulesClient
import org.suppressingfire.wemi.network.HardwareRulesDbMutator
import org.suppressingfire.wemi.network.HardwareRulesWriteResult
import org.suppressingfire.wemi.network.LanRuntimeUtils

/**
 * Coordinates lazy, concurrency-bounded (`Semaphore(3)` + per-device `Mutex`) Tier-2 hardware
 * rules capability verification, read-only inspection, and armored Step 2 writes (create/edit,
 * enable/disable toggle, delete, `timesync1#TimeSync` clock sync, `LOCATIONINFO` solar updates,
 * and raw-ZIP backup restore).
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
    private val demoStore = DemoHardwareRulesStore(nowFormatted)

    private val _hardwareRulesByDeviceId = MutableStateFlow<Map<Int, DeviceHardwareRulesSnapshot>>(emptyMap())
    val hardwareRulesByDeviceId: StateFlow<Map<Int, DeviceHardwareRulesSnapshot>> =
        _hardwareRulesByDeviceId.asStateFlow()

    private val _inspectingDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val inspectingDeviceIds: StateFlow<Set<Int>> = _inspectingDeviceIds.asStateFlow()

    private val _isScanningAll = MutableStateFlow(false)
    val isScanningAll: StateFlow<Boolean> = _isScanningAll.asStateFlow()

    private fun mutexFor(device: DeviceEntity): Mutex =
        deviceMutexes.getOrPut(device.concurrencyKey()) { Mutex() }

    private fun isSyntheticDemoEndpoint(device: DeviceEntity): Boolean =
        isDemoShowroomMode() && device.ipAddress != "127.0.0.1" && device.ipAddress != "localhost"

    fun clearRuntimeState() {
        demoStore.clear()
        _hardwareRulesByDeviceId.value = emptyMap()
        _inspectingDeviceIds.value = emptySet()
        _isScanningAll.value = false
    }

    fun evictDevice(deviceId: Int) {
        demoStore.evictDevice(deviceId)
        _hardwareRulesByDeviceId.update { if (deviceId in it) it - deviceId else it }
        _inspectingDeviceIds.update { if (deviceId in it) it - deviceId else it }
    }

    suspend fun inspectDeviceHardwareRules(
        device: DeviceEntity,
        forceRecheck: Boolean = false
    ): DeviceHardwareRulesSnapshot {
        if (isSyntheticDemoEndpoint(device)) {
            val allDevices = dao.getAllDevices().first()
            val demoSnap = demoStore.getOrCreateSnapshot(device, allDevices)
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
            mutexFor(device).withLock {
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

    suspend fun saveHardwareRule(
        device: DeviceEntity,
        draft: HardwareRuleDraft
    ): HardwareRulesWriteResult {
        if (isSyntheticDemoEndpoint(device)) {
            val res = demoStore.saveRule(device, dao.getAllDevices().first(), draft)
            res.snapshot?.let { snap -> _hardwareRulesByDeviceId.update { it + (device.id to snap) } }
            return res
        }
        return runArmoredDeviceMutation(device) { fresh ->
            val ownerUdn = LanRuntimeUtils.canonicalHardwareUdn(fresh.udn)
                .ifBlank { LanRuntimeUtils.canonicalHardwareUdn(_hardwareRulesByDeviceId.value[fresh.id]?.deviceUdn.orEmpty()) }
                .ifBlank { fresh.udn.ifBlank { "uuid:Lightswitch-1_0-${fresh.serialNumber.ifBlank { fresh.id.toString() }}" } }
            hardwareRulesClient.mutateAndStoreRulesDb(
                device = fresh,
                autoSyncClockIfNeeded = true
            ) { sqliteFile ->
                HardwareRulesDbMutator.upsertRule(sqliteFile, ownerUdn, draft)
            }
        }
    }

    suspend fun toggleHardwareRule(
        device: DeviceEntity,
        rule: HardwareRule,
        enabled: Boolean
    ): HardwareRulesWriteResult {
        if (isSyntheticDemoEndpoint(device)) {
            val res = demoStore.toggleRule(device, dao.getAllDevices().first(), rule.ruleId, enabled)
            res.snapshot?.let { snap -> _hardwareRulesByDeviceId.update { it + (device.id to snap) } }
            return res
        }
        return runArmoredDeviceMutation(device) { fresh ->
            hardwareRulesClient.mutateAndStoreRulesDb(
                device = fresh,
                autoSyncClockIfNeeded = false
            ) { sqliteFile ->
                check(HardwareRulesDbMutator.toggleRuleEnabled(sqliteFile, rule.ruleId, enabled)) {
                    "Rule #${rule.ruleId} was no longer present on ${fresh.name}"
                }
            }
        }
    }

    suspend fun deleteHardwareRule(
        device: DeviceEntity,
        rule: HardwareRule
    ): HardwareRulesWriteResult {
        if (isSyntheticDemoEndpoint(device)) {
            val res = demoStore.deleteRule(device, dao.getAllDevices().first(), rule.ruleId)
            res.snapshot?.let { snap -> _hardwareRulesByDeviceId.update { it + (device.id to snap) } }
            return res
        }
        return runArmoredDeviceMutation(device) { fresh ->
            hardwareRulesClient.mutateAndStoreRulesDb(
                device = fresh,
                autoSyncClockIfNeeded = false
            ) { sqliteFile ->
                check(HardwareRulesDbMutator.deleteRule(sqliteFile, rule.ruleId)) {
                    "Rule #${rule.ruleId} was no longer present on ${fresh.name}"
                }
            }
        }
    }

    suspend fun syncDeviceClock(device: DeviceEntity): HardwareRulesWriteResult {
        if (isSyntheticDemoEndpoint(device)) {
            val res = demoStore.syncClock(device, dao.getAllDevices().first())
            res.snapshot?.let { snap -> _hardwareRulesByDeviceId.update { it + (device.id to snap) } }
            return res
        }
        _inspectingDeviceIds.update { it + device.id }
        return try {
            mutexFor(device).withLock {
                val fresh = dao.getDeviceById(device.id) ?: device
                val syncLog = lanSemaphore.withPermit { hardwareRulesClient.syncDeviceTime(fresh) }
                appendLog(syncLog)
                if (!syncLog.isLiveLanSuccess) {
                    return@withLock HardwareRulesWriteResult(
                        success = false,
                        snapshot = _hardwareRulesByDeviceId.value[fresh.id],
                        message = "Clock sync failed on ${fresh.name}: ${syncLog.statusText}"
                    )
                }
                val (refreshedSnap, refreshLogs) = lanSemaphore.withPermit {
                    hardwareRulesClient.fetchDeviceRulesSnapshot(fresh, saveRawBackup = false)
                }
                refreshLogs.forEach(appendLog)
                if (refreshedSnap.capability.isVerified) {
                    persistCapabilityVerdictLocked(fresh.id, refreshedSnap.capability, refreshedSnap.longPressCapable)
                }
                _hardwareRulesByDeviceId.update { it + (fresh.id to refreshedSnap) }
                HardwareRulesWriteResult(
                    success = true,
                    snapshot = refreshedSnap,
                    message = "Synced clock on ${fresh.name}",
                    clockSynced = true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            _inspectingDeviceIds.update { it - device.id }
        }
    }

    suspend fun updateDeviceSolarLocation(
        device: DeviceEntity,
        location: HardwareLocationInfo
    ): HardwareRulesWriteResult {
        if (isSyntheticDemoEndpoint(device)) {
            val res = demoStore.updateLocation(device, dao.getAllDevices().first(), location)
            res.snapshot?.let { snap -> _hardwareRulesByDeviceId.update { it + (device.id to snap) } }
            return res
        }
        return runArmoredDeviceMutation(device) { fresh ->
            hardwareRulesClient.mutateAndStoreRulesDb(
                device = fresh,
                autoSyncClockIfNeeded = true
            ) { sqliteFile ->
                HardwareRulesDbMutator.updateLocationInfo(sqliteFile, location)
            }
        }
    }

    suspend fun restoreHardwareRulesBackup(
        device: DeviceEntity,
        backupFilePath: String
    ): HardwareRulesWriteResult {
        if (isSyntheticDemoEndpoint(device)) {
            val res = demoStore.restoreBackup(device, dao.getAllDevices().first(), backupFilePath)
            res.snapshot?.let { snap -> _hardwareRulesByDeviceId.update { it + (device.id to snap) } }
            return res
        }
        return runArmoredDeviceMutation(device) { fresh ->
            hardwareRulesClient.restoreRulesFromBackup(fresh, backupFilePath)
        }
    }

    private suspend fun runArmoredDeviceMutation(
        device: DeviceEntity,
        action: suspend (DeviceEntity) -> Pair<HardwareRulesWriteResult, List<SoapLogEntry>>
    ): HardwareRulesWriteResult {
        _inspectingDeviceIds.update { it + device.id }
        return try {
            mutexFor(device).withLock {
                val fresh = dao.getDeviceById(device.id) ?: device
                val (result, logs) = lanSemaphore.withPermit { action(fresh) }
                logs.forEach(appendLog)
                result.snapshot?.let { snap ->
                    if (snap.capability.isVerified) {
                        persistCapabilityVerdictLocked(fresh.id, snap.capability, snap.longPressCapable)
                    }
                    _hardwareRulesByDeviceId.update { it + (fresh.id to snap) }
                }
                result
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            _inspectingDeviceIds.update { it - device.id }
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
}
