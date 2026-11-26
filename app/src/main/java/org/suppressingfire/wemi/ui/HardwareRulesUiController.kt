package org.suppressingfire.wemi.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareLocationInfo
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.HardwareRulesCapability

/**
 * Dialog launch state for creating a new on-device switch rule or editing an existing one.
 */
data class HardwareRuleEditorRequest(
    val preselectedDeviceId: Int? = null,
    val editingRule: HardwareRule? = null
)

/**
 * Cohesive ViewModel-scoped UI controller for Step 1 & Step 2 on-device switch rules:
 * capability inspection, create/edit/toggle/delete rule mutations, `timesync1#TimeSync` clock sync,
 * `LOCATIONINFO` solar coordinates, and raw-ZIP backup restore dialogs.
 */
internal class HardwareRulesUiController(
    private val scope: CoroutineScope,
    private val repository: SwitchRepository,
    private val activeNetworkKeyProvider: () -> String,
    private val onShowTransientStatus: (String) -> Unit
) {
    val hardwareRulesByDeviceId: StateFlow<Map<Int, DeviceHardwareRulesSnapshot>> =
        repository.hardwareRulesByDeviceId
    val inspectingRuleDeviceIds: StateFlow<Set<Int>> = repository.inspectingRuleDeviceIds
    val isScanningAllDeviceRules: StateFlow<Boolean> = repository.isScanningAllDeviceRules

    private val _editorRequest = MutableStateFlow<HardwareRuleEditorRequest?>(null)
    val editorRequest: StateFlow<HardwareRuleEditorRequest?> = _editorRequest.asStateFlow()

    private val _locationDialogDevice = MutableStateFlow<DeviceEntity?>(null)
    val locationDialogDevice: StateFlow<DeviceEntity?> = _locationDialogDevice.asStateFlow()

    private val _backupDialogDevice = MutableStateFlow<DeviceEntity?>(null)
    val backupDialogDevice: StateFlow<DeviceEntity?> = _backupDialogDevice.asStateFlow()

    private val _pendingDeleteRule = MutableStateFlow<Pair<DeviceEntity, HardwareRule>?>(null)
    val pendingDeleteRule: StateFlow<Pair<DeviceEntity, HardwareRule>?> = _pendingDeleteRule.asStateFlow()

    private val scanAllLock = Any()

    @Volatile
    private var scanAllJob: Job? = null

    fun dismissAllDialogs() {
        _editorRequest.value = null
        _locationDialogDevice.value = null
        _backupDialogDevice.value = null
        _pendingDeleteRule.value = null
    }

    fun dismissDialogsForDevice(deviceId: Int) {
        if (_editorRequest.value?.preselectedDeviceId == deviceId) _editorRequest.value = null
        if (_locationDialogDevice.value?.id == deviceId) _locationDialogDevice.value = null
        if (_backupDialogDevice.value?.id == deviceId) _backupDialogDevice.value = null
        if (_pendingDeleteRule.value?.first?.id == deviceId) _pendingDeleteRule.value = null
    }

    fun inspectDeviceHardwareRules(device: DeviceEntity, forceRecheck: Boolean = false) {
        scope.launch {
            val snapshot = repository.inspectDeviceHardwareRules(device, forceRecheck)
            onShowTransientStatus(
                "${device.name}: ${snapshot.diagnosticMessage.ifBlank { snapshot.capability.displayLabel }}"
            )
        }
    }

    fun inspectAllHomeHardwareRules(forceRecheck: Boolean = false) {
        val targetKey = activeNetworkKeyProvider()
        val job: Job
        synchronized(scanAllLock) {
            if (scanAllJob?.isActive == true || isScanningAllDeviceRules.value) return
            job = scope.launch(start = CoroutineStart.LAZY) {
                val self = coroutineContext[Job]
                try {
                    val results = repository.inspectAllHomeHardwareRules(targetKey, forceRecheck)
                    if (results.isEmpty()) {
                        onShowTransientStatus("No reachable switches to read — reconnect offline switches on the Devices tab")
                        return@launch
                    }
                    val supportedCount = results.count { it.capability == HardwareRulesCapability.VERIFIED_SUPPORTED }
                    val totalRules = results.sumOf { snap -> snap.rules.count { it.appliesToInspectedSwitch } }
                    onShowTransientStatus(
                        "Scanned ${results.size} switch(es): $supportedCount on-device ready • $totalRules rule(s) read"
                    )
                } finally {
                    synchronized(scanAllLock) {
                        if (scanAllJob === self) {
                            scanAllJob = null
                        }
                    }
                }
            }
            scanAllJob = job
        }
        job.start()
    }

    fun openAddHardwareRuleDialog(device: DeviceEntity? = null) {
        _editorRequest.value = HardwareRuleEditorRequest(
            preselectedDeviceId = device?.id,
            editingRule = null
        )
    }

    fun openEditHardwareRuleDialog(device: DeviceEntity, rule: HardwareRule) {
        if (!rule.isEditableInApp) {
            val reason = when {
                rule.isSharedMultiSwitch -> "'${rule.name}' is shared across multiple switches; toggle or delete it directly"
                !rule.isOwnedByInspectedSwitch -> "'${rule.name}' is controlled by another switch; edit it on its owner switch"
                else -> "Toggle or delete '${rule.name}' directly; only Long Press, Auto-Off & Schedules can be edited"
            }
            onShowTransientStatus(reason)
            return
        }
        _editorRequest.value = HardwareRuleEditorRequest(
            preselectedDeviceId = device.id,
            editingRule = rule
        )
    }

    fun closeHardwareRuleEditorDialog() {
        _editorRequest.value = null
    }

    fun saveHardwareRule(device: DeviceEntity, draft: HardwareRuleDraft) {
        _editorRequest.value = null
        scope.launch {
            val result = repository.saveHardwareRule(device, draft)
            onShowTransientStatus(
                if (result.success) "${device.name}: ${result.message}" else "${device.name} failed: ${result.message}"
            )
        }
    }

    fun toggleHardwareRule(device: DeviceEntity, rule: HardwareRule, enabled: Boolean) {
        scope.launch {
            val result = repository.toggleHardwareRule(device, rule, enabled)
            val actionVerb = if (enabled) "Enabled" else "Paused"
            onShowTransientStatus(
                if (result.success) {
                    "$actionVerb '${rule.name}' on ${device.name}"
                } else {
                    "Could not update '${rule.name}': ${result.message}"
                }
            )
        }
    }

    fun requestDeleteHardwareRule(device: DeviceEntity, rule: HardwareRule) {
        _pendingDeleteRule.value = device to rule
    }

    fun cancelDeleteHardwareRule() {
        _pendingDeleteRule.value = null
    }

    fun confirmDeleteHardwareRule() {
        val pending = _pendingDeleteRule.value ?: return
        _pendingDeleteRule.value = null
        scope.launch {
            val (device, rule) = pending
            val result = repository.deleteHardwareRule(device, rule)
            onShowTransientStatus(
                if (result.success) {
                    "Deleted '${rule.name}' from ${device.name}"
                } else {
                    "Delete failed on ${device.name}: ${result.message}"
                }
            )
        }
    }

    fun syncDeviceClock(device: DeviceEntity) {
        scope.launch {
            val result = repository.syncDeviceClock(device)
            onShowTransientStatus(result.message)
        }
    }

    fun openSolarLocationDialog(device: DeviceEntity) {
        _locationDialogDevice.value = device
    }

    fun closeSolarLocationDialog() {
        _locationDialogDevice.value = null
    }

    fun saveSolarLocation(device: DeviceEntity, location: HardwareLocationInfo) {
        _locationDialogDevice.value = null
        scope.launch {
            val result = repository.updateDeviceSolarLocation(device, location)
            onShowTransientStatus(
                if (result.success) {
                    "${device.name}: Solar location set to ${location.formatSummary()}"
                } else {
                    "Solar location update failed: ${result.message}"
                }
            )
        }
    }

    fun openBackupRestoreDialog(device: DeviceEntity) {
        _backupDialogDevice.value = device
    }

    fun closeBackupRestoreDialog() {
        _backupDialogDevice.value = null
    }

    fun restoreBackup(device: DeviceEntity, backupFilePath: String) {
        _backupDialogDevice.value = null
        scope.launch {
            val result = repository.restoreHardwareRulesBackup(device, backupFilePath)
            onShowTransientStatus(
                if (result.success) "${device.name}: ${result.message}" else "Restore failed: ${result.message}"
            )
        }
    }
}
