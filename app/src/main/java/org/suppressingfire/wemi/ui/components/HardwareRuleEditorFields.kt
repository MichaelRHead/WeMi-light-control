package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
private val OFFSET_PRESETS = listOf(-30, -15, 0, 15, 30)

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
                colors = chipColors
            )
            FilterChip(
                selected = selectedDays == setOf(2, 3, 4, 5, 6),
                onClick = { onDaysChanged(setOf(2, 3, 4, 5, 6)) },
                label = { Text("Weekdays", fontSize = 11.sp) },
                colors = chipColors
            )
            FilterChip(
                selected = selectedDays == setOf(1, 7),
                onClick = { onDaysChanged(setOf(1, 7)) },
                label = { Text("Weekends", fontSize = 11.sp) },
                colors = chipColors
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
                    colors = chipColors
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatClockTime(hour24, minute),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = WemoGreenDark
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onTimeChange((hour24 + 23) % 24, minute) }) { Text("-1h") }
                    TextButton(onClick = { onTimeChange((hour24 + 1) % 24, minute) }) { Text("+1h") }
                    TextButton(onClick = {
                        val total = (hour24 * 60 + minute + 15) % 1440
                        onTimeChange(total / 60, total % 60)
                    }) { Text("+15m") }
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OFFSET_PRESETS.forEach { off ->
                    val label = when {
                        off > 0 -> "+${off}m"
                        off < 0 -> "${off}m"
                        else -> "Exact"
                    }
                    FilterChip(
                        selected = offsetMinutes == off,
                        onClick = { onOffsetChange(off) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = chipColors
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
