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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

private val COUNTDOWN_PRESETS = listOf(5, 15, 30, 60, 120)

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
    val initialOwnerIdx = remember(eligibleOwners, preselectedDeviceId) {
        val idx = preselectedDeviceId?.let { id -> eligibleOwners.indexOfFirst { it.id == id } } ?: -1
        if (idx >= 0) idx else 0
    }

    var selectedOwnerIdx by remember(preselectedDeviceId, editingRule?.ruleId) { mutableIntStateOf(initialOwnerIdx) }
    val selectedOwner = eligibleOwners.getOrNull(selectedOwnerIdx) ?: eligibleOwners.firstOrNull()
    val ownerSnapshot = selectedOwner?.let { snapshotsByDeviceId[it.id] }
    val ownerLongPressCapable = (ownerSnapshot?.longPressCapable ?: selectedOwner?.hardwareRulesLongPressCapable ?: false) ||
        editingRule?.type == HardwareRuleType.LONG_PRESS

    var ruleName by remember(editingRule?.ruleId) { mutableStateOf(initialDraft?.name.orEmpty()) }
    var ruleType by remember(editingRule?.ruleId) {
        mutableStateOf(
            initialDraft?.type ?: if (ownerLongPressCapable) HardwareRuleType.LONG_PRESS else HardwareRuleType.COUNTDOWN_TIMER
        )
    }
    var isEnabled by remember(editingRule?.ruleId) { mutableStateOf(initialDraft?.isEnabled ?: true) }
    var selectedDays by remember(editingRule?.ruleId) {
        mutableStateOf((initialDraft?.daysOfWeek ?: listOf(1, 2, 3, 4, 5, 6, 7)).toSet())
    }
    var longPressAction by remember(editingRule?.ruleId) {
        mutableStateOf(initialDraft?.longPressAction ?: HardwareLongPressAction.TOGGLE)
    }
    var selectedTargetUdns by remember(editingRule?.ruleId, selectedOwner?.id) {
        val seed = initialDraft?.targetDeviceUdns.orEmpty()
            .map { LanRuntimeUtils.canonicalHardwareUdn(it).ifBlank { it } }
            .filter { it.isNotBlank() }
            .toSet()
        val fallback = if (seed.isEmpty() && selectedOwner != null) {
            controllable.firstOrNull { it.id != selectedOwner.id }
                ?.let { peer -> setOf(canonicalOrSyntheticUdn(peer)) }
                ?: setOf(canonicalOrSyntheticUdn(selectedOwner))
        } else {
            seed
        }
        mutableStateOf(fallback)
    }
    var countdownMinutesText by remember(editingRule?.ruleId) {
        mutableStateOf((initialDraft?.countdownMinutes ?: 30).toString())
    }
    var startAnchor by remember(editingRule?.ruleId) { mutableStateOf(initialDraft?.startAnchor ?: HardwareTimeAnchor.FIXED_TIME) }
    var startHour by remember(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.startHour24 ?: 18) }
    var startMinute by remember(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.startMinute ?: 30) }
    var startOffsetMin by remember(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.startOffsetMinutes ?: 0) }
    var endAnchor by remember(editingRule?.ruleId) { mutableStateOf(initialDraft?.endAnchor ?: HardwareTimeAnchor.FIXED_TIME) }
    var endHour by remember(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.endHour24 ?: 23) }
    var endMinute by remember(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.endMinute ?: 0) }
    var endOffsetMin by remember(editingRule?.ruleId) { mutableIntStateOf(initialDraft?.endOffsetMinutes ?: 0) }
    var startActionOn by remember(editingRule?.ruleId) { mutableStateOf(initialDraft?.startActionOn ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingRule == null && preselectedDeviceId == null && eligibleOwners.size > 1) {
                    Text("Switch", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        eligibleOwners.forEachIndexed { idx, dev ->
                            FilterChip(
                                selected = selectedOwnerIdx == idx,
                                onClick = {
                                    selectedOwnerIdx = idx
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
                    onValueChange = {
                        ruleName = it
                        validationError = null
                    },
                    label = { Text("Rule Name") },
                    placeholder = { Text("e.g. Goodnight Long Press or Porch Sunset") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hardware_rule_name_input")
                )

                Text("Rule Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val types = listOf(
                        Triple(HardwareRuleType.LONG_PRESS, "Long Press", "hardware_rule_type_long_press"),
                        Triple(HardwareRuleType.COUNTDOWN_TIMER, "Auto-Off Delay", "hardware_rule_type_countdown"),
                        Triple(HardwareRuleType.SCHEDULE_INTERVAL, "Schedule", "hardware_rule_type_schedule"),
                        Triple(HardwareRuleType.SIMPLE_TIMER, "Timed Action", "hardware_rule_type_simple")
                    )
                    types.forEach { (type, label, tag) ->
                        val enabledChoice = type != HardwareRuleType.LONG_PRESS || ownerLongPressCapable
                        FilterChip(
                            selected = ruleType == type,
                            enabled = enabledChoice && (editingRule == null || editingRule.type == type),
                            onClick = {
                                ruleType = type
                                validationError = null
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = chipColors,
                            modifier = Modifier.testTag(tag)
                        )
                    }
                }

                when (ruleType) {
                    HardwareRuleType.LONG_PRESS -> {
                        Text("Hold Wall Paddle (2s) Action", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                HardwareLongPressAction.TOGGLE,
                                HardwareLongPressAction.TURN_OFF,
                                HardwareLongPressAction.TURN_ON
                            ).forEach { act ->
                                FilterChip(
                                    selected = longPressAction == act,
                                    onClick = { longPressAction = act },
                                    label = { Text(act.label, fontSize = 12.sp) },
                                    colors = chipColors,
                                    modifier = Modifier.testTag("lp_action_${act.name}")
                                )
                            }
                        }

                        Text("Target Switches", style = MaterialTheme.typography.labelMedium)
                        val knownTargetUdns = controllable.map { canonicalOrSyntheticUdn(it) }.toSet()
                        val extraSavedUdns = selectedTargetUdns.filterNot { it in knownTargetUdns }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            controllable.forEach { dev ->
                                val targetUdn = canonicalOrSyntheticUdn(dev)
                                val isSelected = targetUdn in selectedTargetUdns
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTargetUdns = if (isSelected) selectedTargetUdns - targetUdn else selectedTargetUdns + targetUdn
                                        validationError = null
                                    },
                                    label = { Text(dev.name, fontSize = 12.sp) },
                                    colors = chipColors,
                                    modifier = Modifier.testTag("lp_target_chip_${dev.id}")
                                )
                            }
                            extraSavedUdns.forEach { extraUdn ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        selectedTargetUdns = selectedTargetUdns - extraUdn
                                        validationError = null
                                    },
                                    label = { Text(extraUdn.substringAfterLast('-').ifBlank { extraUdn }, fontSize = 12.sp) },
                                    colors = chipColors
                                )
                            }
                        }
                    }

                    HardwareRuleType.COUNTDOWN_TIMER -> {
                        Text("Turn OFF Automatically After", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            COUNTDOWN_PRESETS.forEach { mins ->
                                val label = if (mins >= 60) "${mins / 60}h" else "${mins}m"
                                FilterChip(
                                    selected = countdownMinutesText.toIntOrNull() == mins,
                                    onClick = {
                                        countdownMinutesText = mins.toString()
                                        validationError = null
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = chipColors,
                                    modifier = Modifier.testTag("countdown_preset_$mins")
                                )
                            }
                        }
                        OutlinedTextField(
                            value = countdownMinutesText,
                            onValueChange = { raw ->
                                countdownMinutesText = raw.filter { it.isDigit() }.take(4)
                                validationError = null
                            },
                            label = { Text("Delay (minutes, 1–1440)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("countdown_minutes_input")
                        )
                        DaysOfWeekPicker(
                            selectedDays = selectedDays,
                            onDaysChanged = { selectedDays = it }
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
                            onAnchorChange = { startAnchor = it },
                            onTimeChange = { h, m -> startHour = h; startMinute = m },
                            onOffsetChange = { startOffsetMin = it }
                        )
                        TimePointPickerSection(
                            title = if (startActionOn) "Then Turn OFF At" else "Then Turn ON At",
                            tagPrefix = "end",
                            anchor = endAnchor,
                            hour24 = endHour,
                            minute = endMinute,
                            offsetMinutes = endOffsetMin,
                            onAnchorChange = { endAnchor = it },
                            onTimeChange = { h, m -> endHour = h; endMinute = m },
                            onOffsetChange = { endOffsetMin = it }
                        )
                        RuleSwitchRow(
                            label = if (startActionOn) "Mode: Turn ON → then OFF" else "Mode: Turn OFF → then ON",
                            checked = startActionOn,
                            contentDesc = "Toggle schedule start action between Turn ON and Turn OFF",
                            testTagName = "schedule_action_switch",
                            onCheckedChange = { startActionOn = it }
                        )
                        if ((startAnchor != HardwareTimeAnchor.FIXED_TIME || endAnchor != HardwareTimeAnchor.FIXED_TIME) &&
                            ownerSnapshot?.locationInfo == null
                        ) {
                            SolarLocationTipText()
                        }
                        DaysOfWeekPicker(
                            selectedDays = selectedDays,
                            onDaysChanged = { selectedDays = it }
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
                            onAnchorChange = { startAnchor = it },
                            onTimeChange = { h, m -> startHour = h; startMinute = m },
                            onOffsetChange = { startOffsetMin = it }
                        )
                        RuleSwitchRow(
                            label = if (startActionOn) "Action: Turn Switch ON" else "Action: Turn Switch OFF",
                            checked = startActionOn,
                            contentDesc = "Toggle timed action between Turn ON and Turn OFF",
                            testTagName = "simple_timer_action_switch",
                            onCheckedChange = { startActionOn = it }
                        )
                        if (startAnchor != HardwareTimeAnchor.FIXED_TIME && ownerSnapshot?.locationInfo == null) {
                            SolarLocationTipText()
                        }
                        DaysOfWeekPicker(
                            selectedDays = selectedDays,
                            onDaysChanged = { selectedDays = it }
                        )
                    }

                    else -> Unit
                }

                RuleSwitchRow(
                    label = if (isEnabled) "Rule Active on Switch" else "Rule Paused",
                    checked = isEnabled,
                    contentDesc = "Enable or pause this on-device rule",
                    testTagName = "hardware_rule_enabled_switch",
                    onCheckedChange = { isEnabled = it }
                )

                validationError?.let { err ->
                    Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

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
        },
        confirmButton = {
            Button(
                onClick = {
                    val owner = selectedOwner
                    if (owner == null) {
                        validationError = "No compatible switch selected"
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
                    if (ruleType == HardwareRuleType.SCHEDULE_INTERVAL &&
                        startAnchor == HardwareTimeAnchor.FIXED_TIME &&
                        endAnchor == HardwareTimeAnchor.FIXED_TIME &&
                        startHour == endHour &&
                        startMinute == endMinute
                    ) {
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
                            targetDeviceUdns = selectedTargetUdns.toList(),
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
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
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
