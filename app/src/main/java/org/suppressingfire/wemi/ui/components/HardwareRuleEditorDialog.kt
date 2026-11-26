package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareLongPressAction
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HardwareTimeAnchor
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Create/Edit dialog for native on-device switch rules (`Long Press`, `Auto-Off Delay`,
 * `Schedule` with fixed clock or Sunrise/Sunset, and `Timed Action`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareRuleEditorDialog(
    homeDevices: List<DeviceEntity>,
    snapshotsByDeviceId: Map<Int, DeviceHardwareRulesSnapshot>,
    preselectedDeviceId: Int?,
    editingRule: HardwareRule?,
    onDismiss: () -> Unit,
    onSave: (DeviceEntity, HardwareRuleDraft) -> Unit
) {
    val controllable = remember(homeDevices) {
        homeDevices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
    }
    val eligibleOwners = remember(controllable, snapshotsByDeviceId, preselectedDeviceId, editingRule?.ruleId) {
        controllable.filter { dev ->
            val cap = snapshotsByDeviceId[dev.id]?.capability ?: dev.parsedHardwareRulesCapability
            val explicitlySelected = dev.id == preselectedDeviceId && editingRule != null
            explicitlySelected || (dev.isReachable && cap != HardwareRulesCapability.VERIFIED_UNSUPPORTED)
        }
    }
    val initialDraft = remember(editingRule) { editingRule?.let(HardwareRuleDraft::fromHardwareRule) }
    val knownByKey = remember(controllable) {
        controllable.associateBy { normalizedUdnKey(canonicalOrSyntheticUdn(it)) }
    }
    val preservedExtraUdns = remember(initialDraft, knownByKey) {
        val seen = HashSet<String>()
        initialDraft?.targetDeviceUdns.orEmpty()
            .map { LanRuntimeUtils.canonicalHardwareUdn(it).ifBlank { it.trim() } }
            .filter { it.isNotBlank() }
            .filter { udn ->
                val key = normalizedUdnKey(udn)
                key !in knownByKey && seen.add(key)
            }
    }

    var selectedOwnerId by rememberSaveable(preselectedDeviceId, editingRule?.ruleId) {
        mutableStateOf(preselectedDeviceId ?: eligibleOwners.firstOrNull()?.id)
    }
    val selectedOwner = eligibleOwners.firstOrNull { it.id == selectedOwnerId } ?: eligibleOwners.firstOrNull()
    val ownerSnapshot = selectedOwner?.let { snapshotsByDeviceId[it.id] }
    val ownerLongPressCapable = (ownerSnapshot?.longPressCapable ?: selectedOwner?.hardwareRulesLongPressCapable ?: false) ||
        editingRule?.type == HardwareRuleType.LONG_PRESS

    var ruleName by rememberSaveable(editingRule?.ruleId) { mutableStateOf(initialDraft?.name.orEmpty()) }
    var ruleType by rememberSaveable(editingRule?.ruleId) {
        mutableStateOf(
            initialDraft?.type ?: if (ownerLongPressCapable) HardwareRuleType.LONG_PRESS else HardwareRuleType.COUNTDOWN_TIMER
        )
    }
    var isEnabled by rememberSaveable(editingRule?.ruleId) { mutableStateOf(initialDraft?.isEnabled ?: true) }
    var selectedDays by rememberSaveable(editingRule?.ruleId, stateSaver = IntSetSaver) {
        mutableStateOf((initialDraft?.daysOfWeek ?: listOf(1, 2, 3, 4, 5, 6, 7)).toSet())
    }
    var longPressAction by rememberSaveable(editingRule?.ruleId) {
        mutableStateOf(initialDraft?.longPressAction ?: HardwareLongPressAction.TOGGLE)
    }
    var selectedTargetUdns by rememberSaveable(
        editingRule?.ruleId,
        selectedOwner?.id,
        stateSaver = StringListSaver
    ) {
        val seen = HashSet<String>()
        val seed = initialDraft?.targetDeviceUdns.orEmpty()
            .map { raw ->
                val key = normalizedUdnKey(raw)
                knownByKey[key]?.let(::canonicalOrSyntheticUdn)
                    ?: LanRuntimeUtils.canonicalHardwareUdn(raw).ifBlank { raw.trim() }
            }
            .filter { it.isNotBlank() && seen.add(normalizedUdnKey(it)) }
        val fallback = if (seed.isEmpty() && selectedOwner != null) {
            controllable.firstOrNull { it.id != selectedOwner.id }
                ?.let { peer -> listOf(canonicalOrSyntheticUdn(peer)) }
                ?: listOf(canonicalOrSyntheticUdn(selectedOwner))
        } else {
            seed
        }
        mutableStateOf(fallback)
    }
    var countdownMinutesText by rememberSaveable(editingRule?.ruleId) {
        mutableStateOf((initialDraft?.countdownMinutes ?: 30).toString())
    }
    var startAnchor by rememberSaveable(editingRule?.ruleId) { mutableStateOf(initialDraft?.startAnchor ?: HardwareTimeAnchor.FIXED_TIME) }
    var startHour by rememberSaveable(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.startHour24 ?: 18) }
    var startMinute by rememberSaveable(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.startMinute ?: 30) }
    var startOffsetMin by rememberSaveable(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.startOffsetMinutes ?: 0) }
    var endAnchor by rememberSaveable(editingRule?.ruleId) { mutableStateOf(initialDraft?.endAnchor ?: HardwareTimeAnchor.FIXED_TIME) }
    var endHour by rememberSaveable(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.endHour24 ?: 23) }
    var endMinute by rememberSaveable(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.endMinute ?: 0) }
    var endOffsetMin by rememberSaveable(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.endOffsetMinutes ?: 0) }
    var startActionOn by rememberSaveable(editingRule?.ruleId) { mutableStateOf(initialDraft?.startActionOn ?: true) }
    var validationError by rememberSaveable(editingRule?.ruleId) { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerLongPressCapable) {
        if (!ownerLongPressCapable && ruleType == HardwareRuleType.LONG_PRESS) {
            ruleType = HardwareRuleType.COUNTDOWN_TIMER
        }
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )
    val parsedCountdownLive = countdownMinutesText.toIntOrNull()
    val isCountdownInvalid = (countdownMinutesText.isNotBlank() && (parsedCountdownLive == null || parsedCountdownLive !in 1..1440)) ||
        (validationError != null && ruleType == HardwareRuleType.COUNTDOWN_TIMER && (parsedCountdownLive == null || parsedCountdownLive !in 1..1440))
    val isOvernightSchedule = startAnchor == HardwareTimeAnchor.FIXED_TIME &&
        endAnchor == HardwareTimeAnchor.FIXED_TIME &&
        (endHour * 60 + endMinute) < (startHour * 60 + startMinute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingRule != null) "Edit On-Device Rule" else "New On-Device Switch Rule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (editingRule == null && preselectedDeviceId == null && eligibleOwners.size > 1) {
                        Text("Switch", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            eligibleOwners.forEach { dev ->
                                FilterChip(
                                    selected = selectedOwner?.id == dev.id,
                                    onClick = {
                                        selectedOwnerId = dev.id
                                        validationError = null
                                        val lp = snapshotsByDeviceId[dev.id]?.longPressCapable ?: dev.hardwareRulesLongPressCapable
                                        if (!lp && ruleType == HardwareRuleType.LONG_PRESS) {
                                            ruleType = HardwareRuleType.COUNTDOWN_TIMER
                                        }
                                    },
                                    label = { Text(dev.name, fontSize = 12.sp) },
                                    colors = chipColors,
                                    modifier = Modifier.testTag("rule_owner_chip_${dev.id}")
                                )
                            }
                        }
                    } else if (selectedOwner != null) {
                        Text(
                            text = "Switch: ${selectedOwner.name} (${selectedOwner.normalizedRoomName})",
                            style = MaterialTheme.typography.bodySmall,
                            color = WemoGreenDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it; validationError = null },
                        label = { Text("Rule Name") },
                        placeholder = { Text("e.g. Goodnight Long Press or Porch Sunset") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hardware_rule_name_input")
                    )

                    RuleTypeSelector(
                        ruleType = ruleType,
                        editingRule = editingRule,
                        ownerLongPressCapable = ownerLongPressCapable,
                        hasOwnerSnapshot = ownerSnapshot != null,
                        onSelectType = { ruleType = it; validationError = null }
                    )

                    when (ruleType) {
                        HardwareRuleType.LONG_PRESS -> {
                            LongPressRuleFields(
                                longPressAction = longPressAction,
                                controllable = controllable,
                                preservedExtraUdns = preservedExtraUdns,
                                selectedTargetUdns = selectedTargetUdns,
                                onActionChange = { longPressAction = it; validationError = null },
                                onToggleTargetUdn = { targetUdn ->
                                    val key = normalizedUdnKey(targetUdn)
                                    val exists = selectedTargetUdns.any { normalizedUdnKey(it) == key }
                                    selectedTargetUdns = if (exists) {
                                        selectedTargetUdns.filterNot { normalizedUdnKey(it) == key }
                                    } else {
                                        selectedTargetUdns + targetUdn
                                    }
                                    validationError = null
                                }
                            )
                        }

                        HardwareRuleType.COUNTDOWN_TIMER -> {
                            CountdownRuleFields(
                                countdownMinutesText = countdownMinutesText,
                                isCountdownInvalid = isCountdownInvalid,
                                selectedDays = selectedDays,
                                onMinutesChange = { countdownMinutesText = it; validationError = null },
                                onDaysChanged = { selectedDays = it; validationError = null }
                            )
                        }

                        HardwareRuleType.SCHEDULE_INTERVAL -> {
                            TimePointPickerSection(
                                title = if (startActionOn) "Turn ON At" else "Turn OFF At",
                                tagPrefix = "start",
                                anchor = startAnchor,
                                hour24 = startHour,
                                minute = startMinute,
                                offsetMinutes = startOffsetMin,
                                initialCustomOffset = initialDraft?.startOffsetMinutes,
                                onAnchorChange = { startAnchor = it; validationError = null },
                                onTimeChange = { h, m -> startHour = h; startMinute = m; validationError = null },
                                onOffsetChange = { startOffsetMin = it; validationError = null }
                            )
                            TimePointPickerSection(
                                title = if (startActionOn) "Then Turn OFF At" else "Then Turn ON At",
                                tagPrefix = "end",
                                anchor = endAnchor,
                                hour24 = endHour,
                                minute = endMinute,
                                offsetMinutes = endOffsetMin,
                                initialCustomOffset = initialDraft?.endOffsetMinutes,
                                isNextDay = isOvernightSchedule,
                                onAnchorChange = { endAnchor = it; validationError = null },
                                onTimeChange = { h, m -> endHour = h; endMinute = m; validationError = null },
                                onOffsetChange = { endOffsetMin = it; validationError = null }
                            )
                            RuleSwitchRow(
                                label = if (startActionOn) "Mode: Turn ON → then OFF" else "Mode: Turn OFF → then ON",
                                checked = startActionOn,
                                contentDesc = "Toggle schedule start action between Turn ON and Turn OFF",
                                testTagName = "schedule_action_switch",
                                onCheckedChange = { startActionOn = it; validationError = null }
                            )
                            if ((startAnchor != HardwareTimeAnchor.FIXED_TIME || endAnchor != HardwareTimeAnchor.FIXED_TIME) &&
                                ownerSnapshot?.locationInfo == null
                            ) {
                                SolarLocationTipText()
                            }
                            DaysOfWeekPicker(
                                selectedDays = selectedDays,
                                onDaysChanged = {
                                    selectedDays = it
                                    validationError = null
                                }
                            )
                        }

                        HardwareRuleType.SIMPLE_TIMER -> {
                            TimePointPickerSection(
                                title = "Trigger Time",
                                tagPrefix = "start",
                                anchor = startAnchor,
                                hour24 = startHour,
                                minute = startMinute,
                                offsetMinutes = startOffsetMin,
                                initialCustomOffset = initialDraft?.startOffsetMinutes,
                                onAnchorChange = { startAnchor = it; validationError = null },
                                onTimeChange = { h, m -> startHour = h; startMinute = m; validationError = null },
                                onOffsetChange = { startOffsetMin = it; validationError = null }
                            )
                            RuleSwitchRow(
                                label = if (startActionOn) "Action: Turn Switch ON" else "Action: Turn Switch OFF",
                                checked = startActionOn,
                                contentDesc = "Toggle timed action between Turn ON and Turn OFF",
                                testTagName = "simple_timer_action_switch",
                                onCheckedChange = { startActionOn = it; validationError = null }
                            )
                            if (startAnchor != HardwareTimeAnchor.FIXED_TIME && ownerSnapshot?.locationInfo == null) {
                                SolarLocationTipText()
                            }
                            DaysOfWeekPicker(
                                selectedDays = selectedDays,
                                onDaysChanged = {
                                    selectedDays = it
                                    validationError = null
                                }
                            )
                        }

                        else -> Unit
                    }

                    RuleSwitchRow(
                        label = if (isEnabled) "Rule Active on Switch" else "Rule Paused",
                        checked = isEnabled,
                        contentDesc = "Enable or pause this on-device rule",
                        testTagName = "hardware_rule_enabled_switch",
                        onCheckedChange = { isEnabled = it; validationError = null }
                    )

                    Surface(
                        color = WemoGreenSoft.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Backs up existing rules, syncs switch clock if needed, validates SQLite integrity, and verifies upload.",
                            color = WemoTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                validationError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag("hardware_rule_validation_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val owner = selectedOwner
                    if (owner == null) {
                        validationError = "No compatible switch selected"
                        return@Button
                    }
                    if (ruleType == HardwareRuleType.LONG_PRESS && !ownerLongPressCapable) {
                        validationError = "Long Press is not supported on ${owner.name}"
                        return@Button
                    }
                    if (ruleType == HardwareRuleType.LONG_PRESS && selectedTargetUdns.isEmpty()) {
                        validationError = "Select at least one target switch for Long Press"
                        return@Button
                    }
                    val parsedCountdown = countdownMinutesText.toIntOrNull()
                    if (ruleType == HardwareRuleType.COUNTDOWN_TIMER && (parsedCountdown == null || parsedCountdown !in 1..1440)) {
                        validationError = "Enter a delay between 1 and 1440 minutes"
                        return@Button
                    }
                    val identicalFixed = startAnchor == HardwareTimeAnchor.FIXED_TIME &&
                        endAnchor == HardwareTimeAnchor.FIXED_TIME &&
                        startHour == endHour && startMinute == endMinute
                    val identicalSolar = startAnchor == endAnchor &&
                        startAnchor != HardwareTimeAnchor.FIXED_TIME &&
                        startOffsetMin == endOffsetMin
                    if (ruleType == HardwareRuleType.SCHEDULE_INTERVAL && (identicalFixed || identicalSolar)) {
                        validationError = "Start time and end time must be different"
                        return@Button
                    }
                    onSave(
                        owner,
                        HardwareRuleDraft(
                            existingRuleId = editingRule?.ruleId,
                            name = ruleName.trim(),
                            type = ruleType,
                            isEnabled = isEnabled,
                            daysOfWeek = selectedDays.sorted().ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) },
                            longPressAction = longPressAction,
                            targetDeviceUdns = selectedTargetUdns,
                            countdownMinutes = (parsedCountdown ?: 30).coerceIn(1, 1440),
                            startAnchor = startAnchor,
                            startHour24 = startHour,
                            startMinute = startMinute,
                            startOffsetMinutes = startOffsetMin,
                            endAnchor = endAnchor,
                            endHour24 = endHour,
                            endMinute = endMinute,
                            endOffsetMinutes = endOffsetMin,
                            startActionOn = startActionOn,
                            endActionOn = !startActionOn
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenDark),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_hardware_rule_button")
            ) {
                Text("Save to Switch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
