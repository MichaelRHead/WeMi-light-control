package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.network.HardwareTimeAnchor
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft

private val DAY_LABELS = listOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
private val OFFSET_PRESETS = listOf(-60, -30, -15, 0, 15, 30, 60)

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
    onOffsetChange: (Int) -> Unit
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
            Text(
                text = formatClockTime(hour24, minute),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = WemoGreenDark,
                modifier = Modifier.testTag("${tagPrefix}_time_value")
            )
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
                    Triple(5, "+5m", "Add 5 minutes")
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
            val options = if (offsetMinutes in OFFSET_PRESETS) {
                OFFSET_PRESETS
            } else {
                (OFFSET_PRESETS + offsetMinutes).sorted()
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
            ),
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
        color = androidx.compose.ui.graphics.Color(0xFF92400E),
        fontSize = 11.5.sp
    )
}

