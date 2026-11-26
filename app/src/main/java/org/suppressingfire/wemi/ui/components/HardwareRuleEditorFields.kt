package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.network.HardwareLongPressAction
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareTimeAnchor
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

private val DAY_LABELS = listOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
private val OFFSET_PRESETS = listOf(-60, -30, -15, 0, 15, 30, 60)
private val COUNTDOWN_PRESETS = listOf(5, 15, 30, 60, 120)

internal val IntSetSaver: Saver<Set<Int>, String> = Saver(
    save = { set -> set.sorted().joinToString(",") },
    restore = { raw ->
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            .ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
    }
)

internal val StringListSaver: Saver<List<String>, String> = Saver(
    save = { list -> list.joinToString("\n") },
    restore = { raw -> if (raw.isEmpty()) emptyList() else raw.split("\n").filter { it.isNotBlank() } }
)

internal fun normalizedUdnKey(raw: String): String =
    LanRuntimeUtils.canonicalHardwareUdn(raw).ifBlank { raw.trim() }.lowercase(Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RuleTypeSelector(
    ruleType: HardwareRuleType,
    editingRule: HardwareRule?,
    ownerLongPressCapable: Boolean,
    hasOwnerSnapshot: Boolean,
    onSelectType: (HardwareRuleType) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
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
                onClick = { onSelectType(type) },
                label = { Text(label, fontSize = 12.sp) },
                colors = chipColors,
                modifier = Modifier.testTag(tag)
            )
        }
    }
    if (editingRule == null && !ownerLongPressCapable) {
        Text(
            text = if (!hasOwnerSnapshot) {
                "Tap Refresh on this switch to check Long Press support"
            } else {
                "Long Press is not supported on this switch model"
            },
            color = WemoTextSecondary,
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("long_press_disabled_hint")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CountdownRuleFields(
    countdownMinutesText: String,
    isCountdownInvalid: Boolean,
    selectedDays: Set<Int>,
    onMinutesChange: (String) -> Unit,
    onDaysChanged: (Set<Int>) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )
    Text("Turn OFF Automatically After", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        COUNTDOWN_PRESETS.forEach { mins ->
            val label = if (mins >= 60) "${mins / 60}h" else "${mins}m"
            FilterChip(
                selected = countdownMinutesText.toIntOrNull() == mins,
                onClick = { onMinutesChange(mins.toString()) },
                label = { Text(label, fontSize = 12.sp) },
                colors = chipColors,
                modifier = Modifier.testTag("countdown_preset_$mins")
            )
        }
    }
    OutlinedTextField(
        value = countdownMinutesText,
        onValueChange = { raw -> onMinutesChange(raw.filter { it.isDigit() }.take(4)) },
        label = { Text("Delay (minutes, 1–1440)") },
        isError = isCountdownInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("countdown_minutes_input")
    )
    DaysOfWeekPicker(selectedDays = selectedDays, onDaysChanged = onDaysChanged)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LongPressRuleFields(
    longPressAction: HardwareLongPressAction,
    controllable: List<DeviceEntity>,
    preservedExtraUdns: List<String>,
    selectedTargetUdns: List<String>,
    onActionChange: (HardwareLongPressAction) -> Unit,
    onToggleTargetUdn: (String) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )
    val selectedKeys = remember(selectedTargetUdns) {
        selectedTargetUdns.mapTo(HashSet()) { normalizedUdnKey(it) }
    }

    Text("Hold Wall Paddle (2s) Action", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            HardwareLongPressAction.TOGGLE,
            HardwareLongPressAction.TURN_OFF,
            HardwareLongPressAction.TURN_ON
        ).forEach { act ->
            FilterChip(
                selected = longPressAction == act,
                onClick = { onActionChange(act) },
                label = { Text(act.label, fontSize = 12.sp) },
                colors = chipColors,
                modifier = Modifier.testTag("lp_action_${act.name}")
            )
        }
    }

    Text("Target Switches", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        controllable.forEach { dev ->
            val targetUdn = canonicalOrSyntheticUdn(dev)
            val isSelected = normalizedUdnKey(targetUdn) in selectedKeys
            FilterChip(
                selected = isSelected,
                onClick = { onToggleTargetUdn(targetUdn) },
                label = { Text(dev.name, fontSize = 12.sp) },
                colors = chipColors,
                modifier = Modifier.testTag("lp_target_chip_${dev.id}")
            )
        }
        preservedExtraUdns.forEach { extraUdn ->
            val isSelected = normalizedUdnKey(extraUdn) in selectedKeys
            FilterChip(
                selected = isSelected,
                onClick = { onToggleTargetUdn(extraUdn) },
                label = { Text(extraUdn.substringAfterLast('-').ifBlank { extraUdn }, fontSize = 12.sp) },
                colors = chipColors
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DaysOfWeekPicker(
    selectedDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Active Days", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = selectedDays.size == 7,
                onClick = { onDaysChanged(setOf(1, 2, 3, 4, 5, 6, 7)) },
                label = { Text("Every Day", fontSize = 11.sp) },
                colors = chipColors,
                modifier = Modifier.testTag("days_preset_everyday")
            )
            FilterChip(
                selected = selectedDays == setOf(2, 3, 4, 5, 6),
                onClick = { onDaysChanged(setOf(2, 3, 4, 5, 6)) },
                label = { Text("Weekdays", fontSize = 11.sp) },
                colors = chipColors,
                modifier = Modifier.testTag("days_preset_weekdays")
            )
            FilterChip(
                selected = selectedDays == setOf(1, 7),
                onClick = { onDaysChanged(setOf(1, 7)) },
                label = { Text("Weekends", fontSize = 11.sp) },
                colors = chipColors,
                modifier = Modifier.testTag("days_preset_weekends")
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DAY_LABELS.forEach { (dayId, label) ->
                val isSelected = dayId in selectedDays
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val next = if (isSelected && selectedDays.size > 1) selectedDays - dayId else selectedDays + dayId
                        onDaysChanged(next)
                    },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = chipColors,
                    modifier = Modifier.testTag("day_chip_$dayId")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimePointPickerSection(
    title: String,
    tagPrefix: String,
    anchor: HardwareTimeAnchor,
    hour24: Int,
    minute: Int,
    offsetMinutes: Int,
    onAnchorChange: (HardwareTimeAnchor) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onOffsetChange: (Int) -> Unit,
    initialCustomOffset: Int? = null,
    isNextDay: Boolean = false
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )
    val stepChipColors = AssistChipDefaults.assistChipColors(labelColor = WemoGreenDark)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HardwareTimeAnchor.entries.forEach { mode ->
                FilterChip(
                    selected = anchor == mode,
                    onClick = { onAnchorChange(mode) },
                    label = { Text(mode.label, fontSize = 11.5.sp) },
                    colors = chipColors,
                    modifier = Modifier.testTag("${tagPrefix}_anchor_${mode.name}")
                )
            }
        }
        if (anchor == HardwareTimeAnchor.FIXED_TIME) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatClockTime(hour24, minute),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = WemoGreenDark,
                    modifier = Modifier.testTag("${tagPrefix}_time_value")
                )
                if (isNextDay) {
                    Text(
                        text = "(next day)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = WemoTextSecondary,
                        modifier = Modifier.testTag("${tagPrefix}_next_day_cue")
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val steps = listOf(
                    Triple(-60, "-1h", "Subtract 1 hour"),
                    Triple(60, "+1h", "Add 1 hour"),
                    Triple(-15, "-15m", "Subtract 15 minutes"),
                    Triple(15, "+15m", "Add 15 minutes"),
                    Triple(-5, "-5m", "Subtract 5 minutes"),
                    Triple(5, "+5m", "Add 5 minutes"),
                    Triple(-1, "-1m", "Subtract 1 minute"),
                    Triple(1, "+1m", "Add 1 minute")
                )
                steps.forEach { (deltaMin, label, desc) ->
                    AssistChip(
                        onClick = {
                            val total = ((hour24 * 60 + minute + deltaMin) % 1440 + 1440) % 1440
                            onTimeChange(total / 60, total % 60)
                        },
                        label = { Text(label, fontSize = 11.5.sp, maxLines = 1, softWrap = false) },
                        colors = stepChipColors,
                        modifier = Modifier
                            .semantics { contentDescription = "$desc for $title" }
                            .testTag("${tagPrefix}_step_${deltaMin}")
                    )
                }
            }
        } else {
            val offsetLabel = when {
                offsetMinutes > 0 -> "${anchor.label} +${offsetMinutes}m"
                offsetMinutes < 0 -> "${anchor.label} ${offsetMinutes}m"
                else -> "Exact ${anchor.label}"
            }
            Text(
                text = offsetLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = WemoGreenDark,
                modifier = Modifier.testTag("${tagPrefix}_solar_value")
            )
            val options = remember(offsetMinutes, initialCustomOffset) {
                buildSet {
                    addAll(OFFSET_PRESETS)
                    initialCustomOffset?.let(::add)
                    add(offsetMinutes)
                }.sorted()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { off ->
                    val label = when {
                        off > 0 -> "+${off}m"
                        off < 0 -> "${off}m"
                        else -> "Exact"
                    }
                    FilterChip(
                        selected = offsetMinutes == off,
                        onClick = { onOffsetChange(off) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = chipColors,
                        modifier = Modifier.testTag("${tagPrefix}_offset_$off")
                    )
                }
            }
        }
    }
}

internal fun canonicalOrSyntheticUdn(device: DeviceEntity): String =
    LanRuntimeUtils.canonicalHardwareUdn(device.udn)
        .ifBlank { device.udn.trim() }
        .ifBlank { "uuid:Lightswitch-1_0-${device.serialNumber.ifBlank { device.id.toString() }}" }

internal fun formatClockTime(hour24: Int, minute: Int): String {
    val amPm = if (hour24 < 12) "AM" else "PM"
    val h12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return String.format(Locale.US, "%d:%02d %s", h12, minute.coerceIn(0, 59), amPm)
}

@Composable
internal fun RuleSwitchRow(
    label: String,
    checked: Boolean,
    contentDesc: String,
    testTagName: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary),
            modifier = Modifier
                .semantics { contentDescription = contentDesc }
                .testTag(testTagName)
        )
    }
}

@Composable
internal fun SolarLocationTipText() {
    Text(
        text = "Tip: Set Solar Location on this switch so Sunrise/Sunset times match your city.",
        color = Color(0xFF92400E),
        fontSize = 11.5.sp
    )
}

