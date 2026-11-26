package org.suppressingfire.wemi.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareDeviceTimeInfo
import org.suppressingfire.wemi.network.HardwareLocationInfo
import org.suppressingfire.wemi.network.HardwareLongPressAction
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.HardwareRuleTimePoint
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesBackupEntry
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HardwareRulesDbMutator
import org.suppressingfire.wemi.network.HardwareRulesWriteResult
import org.suppressingfire.wemi.network.HardwareTimeAnchor

/**
 * In-memory Demo Showroom store for Step 1/Step 2 on-device hardware rules so users can preview
 * capability detection, rule creation/editing/toggling/deletion, clock sync, solar location
 * updates, and backup restore without physical hardware on the LAN.
 */
internal class DemoHardwareRulesStore(
    private val nowFormatted: () -> String
) {
    private val snapshotsByDeviceId = ConcurrentHashMap<Int, DeviceHardwareRulesSnapshot>()
    private val backupLock = Any()
    private val backupHistoryByDeviceId = HashMap<Int, MutableList<Pair<HardwareRulesBackupEntry, DeviceHardwareRulesSnapshot>>>()

    fun clear() {
        snapshotsByDeviceId.clear()
        synchronized(backupLock) { backupHistoryByDeviceId.clear() }
    }

    fun evictDevice(deviceId: Int) {
        snapshotsByDeviceId.remove(deviceId)
        synchronized(backupLock) { backupHistoryByDeviceId.remove(deviceId) }
    }

    fun getOrCreateSnapshot(device: DeviceEntity, allDevices: List<DeviceEntity>): DeviceHardwareRulesSnapshot =
        snapshotsByDeviceId.computeIfAbsent(device.id) { buildInitialDemoSnapshot(device, allDevices) }

    fun saveRule(
        device: DeviceEntity,
        allDevices: List<DeviceEntity>,
        draft: HardwareRuleDraft
    ): HardwareRulesWriteResult {
        val current = getOrCreateSnapshot(device, allDevices)
        if (current.capability != HardwareRulesCapability.VERIFIED_SUPPORTED) {
            return HardwareRulesWriteResult(false, current, "Demo RTOS switch does not support on-device rules")
        }
        pushDemoBackup(device.id, current)
        val nextId = draft.existingRuleId?.takeIf { it > 0 }
            ?: ((current.rules.maxOfOrNull { it.ruleId } ?: (device.id * 10)) + 1)
        val existingRule = current.rules.firstOrNull { it.ruleId == nextId }
        val cleanName = HardwareRulesDbMutator.sanitizeSqlText(draft.name)
            .ifBlank { HardwareRulesDbMutator.defaultRuleName(draft) }
        val newRule = draftToHardwareRule(nextId, cleanName, current.deviceUdn, draft, existingRule)
        val updatedRules = current.rules.filterNot { it.ruleId == nextId } + newRule
        val nextSnap = current.copy(
            dbVersion = current.dbVersion + 1,
            rules = updatedRules.sortedBy { it.ruleId },
            deviceTimeInfo = current.deviceTimeInfo?.copy(driftSeconds = 0, needsTimeSync = false),
            availableBackups = listDemoBackups(device.id),
            diagnosticMessage = "Saved '$cleanName' on switch (Demo DB v${current.dbVersion + 1})",
            inspectedAt = nowFormatted()
        )
        snapshotsByDeviceId[device.id] = nextSnap
        return HardwareRulesWriteResult(
            success = true,
            snapshot = nextSnap,
            message = "Saved '$cleanName' (DB v${nextSnap.dbVersion})",
            clockSynced = current.deviceTimeInfo?.needsTimeSync == true
        )
    }

    fun toggleRule(
        device: DeviceEntity,
        allDevices: List<DeviceEntity>,
        ruleId: Int,
        enabled: Boolean
    ): HardwareRulesWriteResult {
        val current = getOrCreateSnapshot(device, allDevices)
        pushDemoBackup(device.id, current)
        val updated = current.rules.map { if (it.ruleId == ruleId) it.copy(isEnabled = enabled) else it }
        val synced = enabled && current.deviceTimeInfo?.needsTimeSync == true
        val nextSnap = current.copy(
            dbVersion = current.dbVersion + 1,
            rules = updated,
            deviceTimeInfo = if (synced) current.deviceTimeInfo?.copy(driftSeconds = 0, needsTimeSync = false) else current.deviceTimeInfo,
            availableBackups = listDemoBackups(device.id),
            diagnosticMessage = if (enabled) "Enabled rule (Demo DB v${current.dbVersion + 1})" else "Paused rule (Demo DB v${current.dbVersion + 1})",
            inspectedAt = nowFormatted()
        )
        snapshotsByDeviceId[device.id] = nextSnap
        return HardwareRulesWriteResult(true, nextSnap, nextSnap.diagnosticMessage, clockSynced = synced)
    }

    fun deleteRule(
        device: DeviceEntity,
        allDevices: List<DeviceEntity>,
        ruleId: Int
    ): HardwareRulesWriteResult {
        val current = getOrCreateSnapshot(device, allDevices)
        pushDemoBackup(device.id, current)
        val updated = current.rules.filterNot { it.ruleId == ruleId }
        val nextSnap = current.copy(
            dbVersion = current.dbVersion + 1,
            rules = updated,
            availableBackups = listDemoBackups(device.id),
            diagnosticMessage = "Deleted rule (Demo DB v${current.dbVersion + 1})",
            inspectedAt = nowFormatted()
        )
        snapshotsByDeviceId[device.id] = nextSnap
        return HardwareRulesWriteResult(true, nextSnap, nextSnap.diagnosticMessage)
    }

    fun syncClock(device: DeviceEntity, allDevices: List<DeviceEntity>): HardwareRulesWriteResult {
        val current = getOrCreateSnapshot(device, allDevices)
        val nowSec = System.currentTimeMillis() / 1000L
        val nextSnap = current.copy(
            deviceTimeInfo = HardwareDeviceTimeInfo(deviceUtcSeconds = nowSec, driftSeconds = 0, needsTimeSync = false),
            diagnosticMessage = "Switch clock synced (Demo)",
            inspectedAt = nowFormatted()
        )
        snapshotsByDeviceId[device.id] = nextSnap
        return HardwareRulesWriteResult(true, nextSnap, "Synced clock on ${device.name}", clockSynced = true)
    }

    fun updateLocation(
        device: DeviceEntity,
        allDevices: List<DeviceEntity>,
        location: HardwareLocationInfo
    ): HardwareRulesWriteResult {
        val current = getOrCreateSnapshot(device, allDevices)
        val latNum = location.latitude.trim().toDoubleOrNull()
        val lonNum = location.longitude.trim().toDoubleOrNull()
        if (latNum == null || latNum !in -90.0..90.0 || lonNum == null || lonNum !in -180.0..180.0) {
            return HardwareRulesWriteResult(false, current, "Latitude must be between -90 and 90 and longitude between -180 and 180")
        }
        val normalizedLoc = location.copy(
            cityName = HardwareRulesDbMutator.sanitizeSqlText(location.cityName),
            region = HardwareRulesDbMutator.sanitizeSqlText(location.region),
            countryName = HardwareRulesDbMutator.sanitizeSqlText(location.countryName),
            latitude = String.format(Locale.US, "%.4f", latNum),
            longitude = String.format(Locale.US, "%.4f", lonNum),
            countryCode = HardwareRulesDbMutator.resolveCountryCode(location)
        )
        pushDemoBackup(device.id, current)
        val nextSnap = current.copy(
            dbVersion = current.dbVersion + 1,
            locationInfo = normalizedLoc,
            availableBackups = listDemoBackups(device.id),
            diagnosticMessage = "Updated solar location to ${normalizedLoc.formatSummary()} (Demo DB v${current.dbVersion + 1})",
            inspectedAt = nowFormatted()
        )
        snapshotsByDeviceId[device.id] = nextSnap
        return HardwareRulesWriteResult(true, nextSnap, nextSnap.diagnosticMessage)
    }

    fun restoreBackup(
        device: DeviceEntity,
        allDevices: List<DeviceEntity>,
        backupFilePath: String
    ): HardwareRulesWriteResult {
        val current = getOrCreateSnapshot(device, allDevices)
        val match = synchronized(backupLock) {
            backupHistoryByDeviceId[device.id]?.firstOrNull { it.first.filePath == backupFilePath }
        } ?: return HardwareRulesWriteResult(false, current, "Backup not found")
        pushDemoBackup(device.id, current)
        val restored = match.second.copy(
            dbVersion = current.dbVersion + 1,
            availableBackups = listDemoBackups(device.id),
            diagnosticMessage = "Restored DB v${match.first.dbVersion} backup (now Demo DB v${current.dbVersion + 1})",
            inspectedAt = nowFormatted()
        )
        snapshotsByDeviceId[device.id] = restored
        return HardwareRulesWriteResult(true, restored, restored.diagnosticMessage)
    }

    private fun pushDemoBackup(deviceId: Int, snapshot: DeviceHardwareRulesSnapshot) {
        val stamp = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
        val entry = HardwareRulesBackupEntry(
            filePath = "demo://backup/${deviceId}/v${snapshot.dbVersion}_${System.nanoTime()}",
            dbVersion = snapshot.dbVersion,
            createdAtLabel = stamp,
            sizeBytes = 4096L
        )
        synchronized(backupLock) {
            val list = backupHistoryByDeviceId.getOrPut(deviceId) { mutableListOf() }
            list.add(0, entry to snapshot)
            while (list.size > 5) list.removeAt(list.lastIndex)
        }
    }

    private fun listDemoBackups(deviceId: Int): List<HardwareRulesBackupEntry> =
        synchronized(backupLock) { backupHistoryByDeviceId[deviceId]?.map { it.first }.orEmpty() }

    private fun draftToHardwareRule(
        ruleId: Int,
        name: String,
        ownerUdn: String,
        draft: HardwareRuleDraft,
        existingRule: HardwareRule? = null
    ): HardwareRule {
        fun makePoint(anchor: HardwareTimeAnchor, h: Int, m: Int, offMin: Int): HardwareRuleTimePoint = when (anchor) {
            HardwareTimeAnchor.SUNRISE -> HardwareRuleTimePoint(21601, offMin * 60)
            HardwareTimeAnchor.SUNSET -> HardwareRuleTimePoint(64802, offMin * 60)
            HardwareTimeAnchor.FIXED_TIME -> HardwareRuleTimePoint(h * 3600 + m * 60, 0)
        }
        return when (draft.type) {
            HardwareRuleType.LONG_PRESS -> HardwareRule(
                ruleId = ruleId,
                name = name,
                type = HardwareRuleType.LONG_PRESS,
                rawFirmwareType = HardwareRuleType.LONG_PRESS.firmwareType,
                isEnabled = draft.isEnabled,
                ruleOrder = ruleId,
                ownerDeviceUdn = ownerUdn,
                daysOfWeek = listOf(-1),
                startTime = HardwareRuleTimePoint(60),
                endTime = HardwareRuleTimePoint(86400),
                ruleDurationSeconds = 86340,
                startAction = draft.longPressAction.code.toDouble(),
                endAction = -1.0,
                countdownSeconds = 0,
                sensorDurationSeconds = 0,
                targetDeviceUdns = draft.targetDeviceUdns
            )
            HardwareRuleType.COUNTDOWN_TIMER -> HardwareRule(
                ruleId = ruleId,
                name = name,
                type = HardwareRuleType.COUNTDOWN_TIMER,
                rawFirmwareType = HardwareRuleType.COUNTDOWN_TIMER.firmwareType,
                isEnabled = draft.isEnabled,
                ruleOrder = ruleId,
                ownerDeviceUdn = ownerUdn,
                daysOfWeek = draft.daysOfWeek,
                startTime = existingRule?.takeIf { it.type == HardwareRuleType.COUNTDOWN_TIMER }?.startTime ?: HardwareRuleTimePoint(0),
                endTime = existingRule?.takeIf { it.type == HardwareRuleType.COUNTDOWN_TIMER }?.endTime ?: HardwareRuleTimePoint(86400),
                ruleDurationSeconds = existingRule?.takeIf { it.type == HardwareRuleType.COUNTDOWN_TIMER }?.ruleDurationSeconds ?: 86400,
                startAction = 1.0,
                endAction = 0.0,
                countdownSeconds = draft.countdownMinutes.coerceIn(1, 1440) * 60,
                sensorDurationSeconds = 0
            )
            HardwareRuleType.SIMPLE_TIMER -> HardwareRule(
                ruleId = ruleId,
                name = name,
                type = HardwareRuleType.SIMPLE_TIMER,
                rawFirmwareType = HardwareRuleType.SIMPLE_TIMER.firmwareType,
                isEnabled = draft.isEnabled,
                ruleOrder = ruleId,
                ownerDeviceUdn = ownerUdn,
                daysOfWeek = draft.daysOfWeek,
                startTime = makePoint(draft.startAnchor, draft.startHour24, draft.startMinute, draft.startOffsetMinutes),
                endTime = null,
                ruleDurationSeconds = 0,
                startAction = if (draft.startActionOn) 1.0 else 0.0,
                endAction = -1.0,
                countdownSeconds = 0,
                sensorDurationSeconds = 0
            )
            else -> HardwareRule(
                ruleId = ruleId,
                name = name,
                type = HardwareRuleType.SCHEDULE_INTERVAL,
                rawFirmwareType = HardwareRuleType.SCHEDULE_INTERVAL.firmwareType,
                isEnabled = draft.isEnabled,
                ruleOrder = ruleId,
                ownerDeviceUdn = ownerUdn,
                daysOfWeek = draft.daysOfWeek,
                startTime = makePoint(draft.startAnchor, draft.startHour24, draft.startMinute, draft.startOffsetMinutes),
                endTime = makePoint(draft.endAnchor, draft.endHour24, draft.endMinute, draft.endOffsetMinutes),
                ruleDurationSeconds = 14400,
                startAction = if (draft.startActionOn) 1.0 else 0.0,
                endAction = if (draft.endActionOn) 1.0 else 0.0,
                countdownSeconds = 0,
                sensorDurationSeconds = 0
            )
        }
    }

    private fun buildInitialDemoSnapshot(
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
                        startAction = HardwareLongPressAction.TURN_OFF.code.toDouble(),
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
                        startAction = 1.0,
                        endAction = 0.0,
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
            locationInfo = HardwareLocationInfo("Mountain View", "CA", "United States", "37.3861", "-122.0839", "US"),
            existingTables = setOf("RULES", "RULEDEVICES", "TARGETDEVICES", "LOCATIONINFO"),
            integrityOk = true,
            diagnosticMessage = "Demo showcase snapshot (${rules.size} on-device rule(s))",
            inspectedAt = nowFormatted()
        )
    }
}
