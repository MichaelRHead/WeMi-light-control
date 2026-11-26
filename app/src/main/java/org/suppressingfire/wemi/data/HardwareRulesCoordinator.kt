package org.suppressingfire.wemi.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
import org.suppressingfire.wemi.network.HardwareDeviceTimeInfo
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
    private val stateEpoch = AtomicLong(0L)
    private val scanAllCount = AtomicInteger(0)
    private val opCountLock = Any()
    private val activeOpCounts = HashMap<Int, Int>()

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

    private fun beginDeviceOp(deviceId: Int, opEpoch: Long) {
        synchronized(opCountLock) {
            if (stateEpoch.get() != opEpoch) return
            activeOpCounts[deviceId] = (activeOpCounts[deviceId] ?: 0) + 1
            _inspectingDeviceIds.value = activeOpCounts.keys.toSet()
        }
    }

    private fun endDeviceOp(deviceId: Int, opEpoch: Long) {
        synchronized(opCountLock) {
            if (stateEpoch.get() != opEpoch) return
            val next = (activeOpCounts[deviceId] ?: 0) - 1
            if (next <= 0) activeOpCounts.remove(deviceId) else activeOpCounts[deviceId] = next
            _inspectingDeviceIds.value = activeOpCounts.keys.toSet()
        }
    }

    fun clearRuntimeState() {
        stateEpoch.incrementAndGet()
        demoStore.clear()
        _hardwareRulesByDeviceId.value = emptyMap()
        synchronized(opCountLock) {
            activeOpCounts.clear()
            _inspectingDeviceIds.value = emptySet()
        }
        scanAllCount.set(0)
        _isScanningAll.value = false
    }

    fun evictDevice(deviceId: Int) {
        demoStore.evictDevice(deviceId)
        _hardwareRulesByDeviceId.update { if (deviceId in it) it - deviceId else it }
        synchronized(opCountLock) {
            if (activeOpCounts.remove(deviceId) != null) {
                _inspectingDeviceIds.value = activeOpCounts.keys.toSet()
            }
        }
    }

    suspend fun inspectDeviceHardwareRules(
        device: DeviceEntity,
        forceRecheck: Boolean = false
    ): DeviceHardwareRulesSnapshot {
        val opEpoch = stateEpoch.get()
        if (isSyntheticDemoEndpoint(device)) {
            val allDevices = dao.getAllDevices().first()
            val demoSnap = demoStore.getOrCreateSnapshot(device, allDevices)
            if (isCommitStillValid(opEpoch, device)) {
                persistCapabilityVerdict(opEpoch, device, demoSnap.capability, demoSnap.longPressCapable)
                _hardwareRulesByDeviceId.update { it + (device.id to demoSnap) }
            }
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
            if (isCommitStillValid(opEpoch, device)) {
                _hardwareRulesByDeviceId.update { it + (device.id to cached) }
            }
            return cached
        }

        beginDeviceOp(device.id, opEpoch)
        return try {
            mutexFor(device).withLock {
                val fresh = dao.getDeviceById(device.id) ?: device
                var healedPort: Int? = null
                val (snapshot, logs) = lanSemaphore.withPermit {
                    hardwareRulesClient.fetchDeviceRulesSnapshot(fresh) { port -> healedPort = port }
                }
                logs.forEach(appendLog)
                if (isCommitStillValid(opEpoch, fresh)) {
                    persistDeviceUpdatesLocked(
                        opEpoch = opEpoch,
                        target = fresh,
                        capability = snapshot.capability.takeIf { it.isVerified },
                        longPressCapable = snapshot.longPressCapable,
                        healedPort = healedPort
                    )
                    _hardwareRulesByDeviceId.update { it + (fresh.id to snapshot) }
                }
                snapshot
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            endDeviceOp(device.id, opEpoch)
        }
    }

    suspend fun inspectAllHomeHardwareRules(
        networkKey: String,
        forceRecheck: Boolean = false
    ): List<DeviceHardwareRulesSnapshot> {
        val opEpoch = stateEpoch.get()
        if (scanAllCount.incrementAndGet() > 0) _isScanningAll.value = true
        return try {
            val homeDevices = dao.getAllDevices().first()
                .filter { it.networkKey == networkKey && it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR }
            coroutineScope {
                homeDevices.map { dev ->
                    async { inspectDeviceHardwareRules(dev, forceRecheck = forceRecheck) }
                }.awaitAll()
            }
        } finally {
            if (stateEpoch.get() == opEpoch && scanAllCount.decrementAndGet() <= 0) {
                scanAllCount.set(0)
                _isScanningAll.value = false
            }
        }
    }

    suspend fun saveHardwareRule(device: DeviceEntity, draft: HardwareRuleDraft): HardwareRulesWriteResult {
        val opEpoch = stateEpoch.get()
        if (draft.existingRuleId != null) {
            val cached = _hardwareRulesByDeviceId.value[device.id]?.rules?.firstOrNull { it.ruleId == draft.existingRuleId }
            if (cached != null && (!cached.isOwnedByInspectedSwitch || cached.isSharedMultiSwitch)) {
                return refusedOwnershipResult(device.id, cached.isSharedMultiSwitch)
            }
        }
        if (isSyntheticDemoEndpoint(device)) {
            return runDemoMutation(device, opEpoch) { all -> demoStore.saveRule(device, all, draft) }
        }
        return runArmoredDeviceMutation(device, opEpoch) { fresh, onTargetUpdated, onPortResolved ->
            var active = fresh
            var ownerUdn = cachedOwnerUdn(active)
            if (ownerUdn.isBlank()) {
                val fetched = hardwareRulesClient.fetchCanonicalDeviceUdn(active)
                if (fetched != null && fetched.first.isNotBlank()) {
                    ownerUdn = fetched.first
                    onPortResolved(fetched.second)
                    persistDeviceUpdatesLocked(opEpoch, active, healedPort = fetched.second, resolvedUdn = ownerUdn)?.let { updated ->
                        active = updated
                        onTargetUpdated(updated)
                    }
                }
            }
            if (ownerUdn.isBlank()) {
                return@runArmoredDeviceMutation HardwareRulesWriteResult(
                    success = false,
                    snapshot = _hardwareRulesByDeviceId.value[active.id],
                    message = "Unable to read switch hardware ID from /setup.xml; scan or reconnect ${active.name} before saving rules"
                ) to emptyList()
            }
            hardwareRulesClient.mutateAndStoreRulesDb(active, autoSyncClockIfNeeded = true, onPortResolved = onPortResolved) { dbFile ->
                draft.existingRuleId?.takeIf { it > 0 }?.let { id -> HardwareRulesDbMutator.verifyRuleOwnedExclusivelyBy(dbFile, id, ownerUdn) }
                HardwareRulesDbMutator.upsertRule(dbFile, ownerUdn, draft)
            }
        }
    }

    suspend fun toggleHardwareRule(device: DeviceEntity, rule: HardwareRule, enabled: Boolean): HardwareRulesWriteResult {
        if (!rule.isOwnedByInspectedSwitch || rule.isSharedMultiSwitch) return refusedOwnershipResult(device.id, rule.isSharedMultiSwitch)
        val opEpoch = stateEpoch.get()
        if (isSyntheticDemoEndpoint(device)) {
            return runDemoMutation(device, opEpoch) { all -> demoStore.toggleRule(device, all, rule.ruleId, enabled) }
        }
        return runArmoredDeviceMutation(device, opEpoch) { fresh, _, onPortResolved ->
            val ownerUdn = cachedOwnerUdn(fresh)
            hardwareRulesClient.mutateAndStoreRulesDb(fresh, autoSyncClockIfNeeded = enabled, onPortResolved = onPortResolved) { dbFile ->
                HardwareRulesDbMutator.verifyRuleOwnedExclusivelyBy(dbFile, rule.ruleId, ownerUdn)
                check(HardwareRulesDbMutator.toggleRuleEnabled(dbFile, rule.ruleId, enabled)) {
                    "Rule #${rule.ruleId} was no longer present on ${fresh.name}"
                }
            }
        }
    }

    suspend fun deleteHardwareRule(device: DeviceEntity, rule: HardwareRule): HardwareRulesWriteResult {
        if (!rule.isOwnedByInspectedSwitch || rule.isSharedMultiSwitch) return refusedOwnershipResult(device.id, rule.isSharedMultiSwitch)
        val opEpoch = stateEpoch.get()
        if (isSyntheticDemoEndpoint(device)) {
            return runDemoMutation(device, opEpoch) { all -> demoStore.deleteRule(device, all, rule.ruleId) }
        }
        return runArmoredDeviceMutation(device, opEpoch) { fresh, _, onPortResolved ->
            val ownerUdn = cachedOwnerUdn(fresh)
            hardwareRulesClient.mutateAndStoreRulesDb(fresh, autoSyncClockIfNeeded = false, onPortResolved = onPortResolved) { dbFile ->
                HardwareRulesDbMutator.verifyRuleOwnedExclusivelyBy(dbFile, rule.ruleId, ownerUdn)
                check(HardwareRulesDbMutator.deleteRule(dbFile, rule.ruleId)) {
                    "Rule #${rule.ruleId} was no longer present on ${fresh.name}"
                }
            }
        }
    }

    suspend fun syncDeviceClock(device: DeviceEntity): HardwareRulesWriteResult {
        val opEpoch = stateEpoch.get()
        if (isSyntheticDemoEndpoint(device)) {
            return runDemoMutation(device, opEpoch) { all -> demoStore.syncClock(device, all) }
        }
        beginDeviceOp(device.id, opEpoch)
        return try {
            mutexFor(device).withLock {
                val fresh = dao.getDeviceById(device.id) ?: device
                var healedPort: Int? = null
                val syncLog = lanSemaphore.withPermit { hardwareRulesClient.syncDeviceTime(fresh) { healedPort = it } }
                appendLog(syncLog)
                if (!syncLog.isLiveLanSuccess) {
                    return@withLock HardwareRulesWriteResult(
                        false, _hardwareRulesByDeviceId.value[fresh.id],
                        "Clock sync failed on ${fresh.name}: ${syncLog.statusText}"
                    )
                }
                val deviceForRefresh = if (healedPort != null && healedPort != fresh.port) fresh.copy(port = healedPort!!) else fresh
                val (refreshedSnap, refreshLogs) = lanSemaphore.withPermit {
                    hardwareRulesClient.fetchDeviceRulesSnapshot(deviceForRefresh, saveRawBackup = false) { healedPort = it }
                }
                refreshLogs.forEach(appendLog)
                val existing = _hardwareRulesByDeviceId.value[fresh.id]
                val finalSnap = if (refreshedSnap.hasInspectionError && existing != null) {
                    existing.copy(
                        deviceTimeInfo = HardwareDeviceTimeInfo(System.currentTimeMillis() / 1000L, 0L, false),
                        inspectedAt = nowFormatted()
                    )
                } else refreshedSnap
                if (isCommitStillValid(opEpoch, fresh)) {
                    persistDeviceUpdatesLocked(
                        opEpoch, fresh, finalSnap.capability.takeIf { it.isVerified }, finalSnap.longPressCapable, healedPort
                    )
                    _hardwareRulesByDeviceId.update { it + (fresh.id to finalSnap) }
                }
                HardwareRulesWriteResult(true, finalSnap, "Synced clock on ${fresh.name}", clockSynced = true)
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            endDeviceOp(device.id, opEpoch)
        }
    }

    suspend fun updateDeviceSolarLocation(device: DeviceEntity, location: HardwareLocationInfo): HardwareRulesWriteResult {
        val opEpoch = stateEpoch.get()
        if (isSyntheticDemoEndpoint(device)) {
            return runDemoMutation(device, opEpoch) { all -> demoStore.updateLocation(device, all, location) }
        }
        return runArmoredDeviceMutation(device, opEpoch) { fresh, _, onPortResolved ->
            hardwareRulesClient.mutateAndStoreRulesDb(fresh, autoSyncClockIfNeeded = true, onPortResolved = onPortResolved) { dbFile ->
                HardwareRulesDbMutator.updateLocationInfo(dbFile, location)
            }
        }
    }

    suspend fun restoreHardwareRulesBackup(device: DeviceEntity, backupFilePath: String): HardwareRulesWriteResult {
        val opEpoch = stateEpoch.get()
        if (isSyntheticDemoEndpoint(device)) {
            return runDemoMutation(device, opEpoch) { all -> demoStore.restoreBackup(device, all, backupFilePath) }
        }
        return runArmoredDeviceMutation(device, opEpoch) { fresh, _, onPortResolved ->
            hardwareRulesClient.restoreRulesFromBackup(fresh, backupFilePath, onPortResolved)
        }
    }

    private fun cachedOwnerUdn(device: DeviceEntity): String =
        LanRuntimeUtils.canonicalHardwareUdn(device.udn)
            .ifBlank { LanRuntimeUtils.canonicalHardwareUdn(_hardwareRulesByDeviceId.value[device.id]?.deviceUdn.orEmpty()) }

    private suspend fun runDemoMutation(
        device: DeviceEntity,
        opEpoch: Long,
        block: (List<DeviceEntity>) -> HardwareRulesWriteResult
    ): HardwareRulesWriteResult {
        val res = block(dao.getAllDevices().first())
        if (isCommitStillValid(opEpoch, device)) {
            res.snapshot?.let { snap -> _hardwareRulesByDeviceId.update { it + (device.id to snap) } }
        }
        return res
    }

    private fun refusedOwnershipResult(deviceId: Int, isSharedMultiSwitch: Boolean): HardwareRulesWriteResult =
        HardwareRulesWriteResult(
            success = false,
            snapshot = _hardwareRulesByDeviceId.value[deviceId],
            message = if (isSharedMultiSwitch) {
                "Shared multi-switch rules cannot be modified from a single switch"
            } else {
                "This rule belongs to another switch and is read-only here"
            }
        )

    private suspend fun runArmoredDeviceMutation(
        device: DeviceEntity,
        opEpoch: Long,
        action: suspend (DeviceEntity, (DeviceEntity) -> Unit, (Int) -> Unit) -> Pair<HardwareRulesWriteResult, List<SoapLogEntry>>
    ): HardwareRulesWriteResult {
        beginDeviceOp(device.id, opEpoch)
        return try {
            mutexFor(device).withLock {
                var tracked = dao.getDeviceById(device.id) ?: device
                var healedPort: Int? = null
                val (result, logs) = lanSemaphore.withPermit {
                    action(tracked, { tracked = it }, { healedPort = it })
                }
                logs.forEach(appendLog)
                if (isCommitStillValid(opEpoch, tracked)) {
                    val snap = result.snapshot
                    if (snap != null) {
                        persistDeviceUpdatesLocked(
                            opEpoch, tracked, snap.capability.takeIf { it.isVerified }, snap.longPressCapable, healedPort
                        )
                        _hardwareRulesByDeviceId.update { it + (tracked.id to snap) }
                    } else {
                        if (healedPort != null) persistDeviceUpdatesLocked(opEpoch, tracked, healedPort = healedPort)
                        val refreshedBackups = hardwareRulesClient.listBackupsForDevice(tracked)
                        _hardwareRulesByDeviceId.update { map ->
                            val existing = map[tracked.id] ?: return@update map
                            map + (tracked.id to existing.copy(availableBackups = refreshedBackups))
                        }
                    }
                }
                result
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            endDeviceOp(device.id, opEpoch)
        }
    }

    private suspend fun isCommitStillValid(opEpoch: Long, target: DeviceEntity): Boolean {
        if (stateEpoch.get() != opEpoch) return false
        val current = dao.getDeviceById(target.id) ?: return false
        return current.concurrencyKey() == target.concurrencyKey()
    }

    private suspend fun persistCapabilityVerdict(
        opEpoch: Long,
        target: DeviceEntity,
        capability: HardwareRulesCapability,
        longPressCapable: Boolean
    ) {
        val current = dao.getDeviceById(target.id) ?: return
        if (stateEpoch.get() != opEpoch || current.concurrencyKey() != target.concurrencyKey()) return
        mutexFor(current).withLock { persistDeviceUpdatesLocked(opEpoch, current, capability, longPressCapable) }
    }

    private suspend fun persistDeviceUpdatesLocked(
        opEpoch: Long,
        target: DeviceEntity,
        capability: HardwareRulesCapability? = null,
        longPressCapable: Boolean? = null,
        healedPort: Int? = null,
        resolvedUdn: String? = null
    ): DeviceEntity? {
        if (stateEpoch.get() != opEpoch) return null
        val fresh = dao.getDeviceById(target.id) ?: return null
        if (fresh.concurrencyKey() != target.concurrencyKey()) return null
        val nextCap = capability?.name ?: fresh.hardwareRulesCapability
        val nextLongPress = longPressCapable ?: fresh.hardwareRulesLongPressCapable
        val nextVerifiedFw = if (capability?.isVerified == true) fresh.firmwareVersion.trim() else fresh.hardwareRulesVerifiedFirmware
        val nextPort = healedPort?.takeIf { it in 1..65535 } ?: fresh.port
        val nextUdn = resolvedUdn?.takeIf { it.isNotBlank() } ?: fresh.udn
        if (fresh.hardwareRulesCapability == nextCap &&
            fresh.hardwareRulesLongPressCapable == nextLongPress &&
            fresh.hardwareRulesVerifiedFirmware == nextVerifiedFw &&
            fresh.port == nextPort &&
            fresh.udn == nextUdn
        ) return fresh
        val updated = fresh.copy(
            hardwareRulesCapability = nextCap, hardwareRulesLongPressCapable = nextLongPress,
            hardwareRulesVerifiedFirmware = nextVerifiedFw, port = nextPort, udn = nextUdn
        )
        dao.updateDevice(updated)
        return updated
    }
}
