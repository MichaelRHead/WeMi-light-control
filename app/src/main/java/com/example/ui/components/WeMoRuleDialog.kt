package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddWeMoRuleDialog(
    devices: List<WeMoDeviceEntity>,
    onDismiss: () -> Unit,
    onCreateRule: (
        name: String,
        ruleType: String,
        targetDevice: WeMoDeviceEntity,
        triggerSummary: String,
        targetStateOn: Boolean,
        targetBrightness: Int
    ) -> Unit
) {
    val controllableDevices = devices.filter { it.parsedType != WeMoDeviceType.MOTION_SENSOR }
        .ifEmpty { devices }
    var ruleName by remember { mutableStateOf("") }
    var ruleType by remember { mutableStateOf("SCHEDULE") }
    var selectedDeviceIndex by remember { mutableIntStateOf(0) }
    var triggerSummary by remember { mutableStateOf("Daily at 6:30 PM") }
    var targetStateOn by remember { mutableStateOf(true) }
    var targetBrightness by remember { mutableFloatStateOf(100f) }

    val selectedDevice = controllableDevices.getOrNull(selectedDeviceIndex) ?: controllableDevices.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New WeMo Switch Rule",
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
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Rule Name") },
                    placeholder = { Text("e.g. Porch Sunset Timer") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_name_input")
                )

                Text("Rule Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SCHEDULE" to "Schedule",
                        "MOTION_TRIGGER" to "Motion Trigger",
                        "AUTO_OFF" to "Auto-Off Timer"
                    ).forEach { (typeKey, label) ->
                        FilterChip(
                            selected = ruleType == typeKey,
                            onClick = {
                                ruleType = typeKey
                                triggerSummary = when (typeKey) {
                                    "MOTION_TRIGGER" -> "When Bedroom Light Motion senses movement"
                                    "AUTO_OFF" -> "After 30 minutes ON"
                                    else -> "Daily at 6:30 PM"
                                }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WemoGreenSoft,
                                selectedLabelColor = WemoGreenDark
                            )
                        )
                    }
                }

                Text("Target Switch / Bulb", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    controllableDevices.forEachIndexed { idx, dev ->
                        FilterChip(
                            selected = selectedDeviceIndex == idx,
                            onClick = { selectedDeviceIndex = idx },
                            label = { Text("${dev.name} (${dev.roomName})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WemoGreenSoft,
                                selectedLabelColor = WemoGreenDark
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = triggerSummary,
                    onValueChange = { triggerSummary = it },
                    label = { Text("When / Trigger Condition") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (targetStateOn) "Action: Turn Switch ON" else "Action: Turn Switch OFF",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = targetStateOn,
                        onCheckedChange = { targetStateOn = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary)
                    )
                }

                if (targetStateOn && selectedDevice?.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER) {
                    Text(
                        text = "Target Brightness: ${targetBrightness.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = targetBrightness,
                        onValueChange = { targetBrightness = it },
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = WemoGreenPrimary,
                            activeTrackColor = WemoGreenPrimary
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedDevice != null) {
                        onCreateRule(
                            ruleName.ifBlank { "${selectedDevice.name} Rule" },
                            ruleType,
                            selectedDevice,
                            triggerSummary,
                            targetStateOn,
                            targetBrightness.roundToInt()
                        )
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                modifier = Modifier.testTag("save_rule_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
