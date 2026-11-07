package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.data.WeMoRepository
import com.example.data.inferRoomFromName
import com.example.ui.theme.WemoErrorRed
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditWeMoDeviceDialog(
    initialDevice: WeMoDeviceEntity?,
    onDismiss: () -> Unit,
    onSave: (
        id: Int,
        name: String,
        roomName: String,
        deviceType: WeMoDeviceType,
        ipAddress: String,
        port: Int,
        brightness: Int,
        isReachable: Boolean,
        isOn: Boolean,
        autoOffMinutes: Int
    ) -> Unit,
    onDelete: ((WeMoDeviceEntity) -> Unit)? = null,
    onScanNetwork: (() -> Unit)? = null
) {
    val isEditing = initialDevice != null
    var name by remember { mutableStateOf(initialDevice?.name ?: "") }
    var roomName by remember { mutableStateOf(initialDevice?.roomName ?: "Living Room") }
    var selectedType by remember {
        mutableStateOf(initialDevice?.parsedType ?: WeMoDeviceType.SWITCH_PLUG)
    }
    var ipAddress by remember { mutableStateOf(initialDevice?.ipAddress ?: "192.168.1.115") }
    var portText by remember { mutableStateOf((initialDevice?.port ?: 49153).toString()) }
    var brightness by remember { mutableFloatStateOf((initialDevice?.brightness ?: 100).toFloat()) }
    var isReachable by remember { mutableStateOf(initialDevice?.isReachable ?: true) }
    var isOn by remember { mutableStateOf(initialDevice?.isOn ?: true) }
    var autoOffText by remember { mutableStateOf((initialDevice?.autoOffMinutes ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit ${initialDevice?.name}" else stringResource(R.string.dialog_add_device_title),
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
                if (!isEditing && onScanNetwork != null) {
                    OutlinedButton(
                        onClick = {
                            onScanNetwork()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_wifi_ssdp_button")
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_scan_network),
                            color = WemoGreenDark
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!isEditing) {
                            roomName = inferRoomFromName(it)
                        }
                    },
                    label = { Text(stringResource(R.string.label_device_name)) },
                    placeholder = { Text("e.g. Porch Light Switch") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_input")
                )

                Text(
                    text = "Room / Group",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WeMoRepository.DEFAULT_ROOMS.forEach { presetRoom ->
                        FilterChip(
                            selected = roomName.equals(presetRoom, ignoreCase = true),
                            onClick = { roomName = presetRoom },
                            label = { Text(presetRoom) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WemoGreenSoft,
                                selectedLabelColor = WemoGreenDark
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name (or type a custom room)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_room_input")
                )

                Text(
                    text = "Hardware Type",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val types = listOf(
                        WeMoDeviceType.SWITCH_PLUG to "Light Switch / Plug",
                        WeMoDeviceType.LIGHTBULB_DIMMER to "Dimmer / Lightbulb",
                        WeMoDeviceType.MOTION_SENSOR to "Motion Sensor"
                    )
                    types.forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WemoGreenSoft,
                                selectedLabelColor = WemoGreenDark
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text(stringResource(R.string.label_ip_address)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("device_ip_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("device_port_input")
                    )
                }

                if (selectedType == WeMoDeviceType.LIGHTBULB_DIMMER) {
                    Text(
                        text = "Initial Brightness: ${brightness.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = WemoGreenPrimary,
                            activeTrackColor = WemoGreenPrimary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Detected on Local Wi-Fi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isReachable) "Online & Controllable" else "Shows 'Not Detected'",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isReachable,
                        onCheckedChange = { isReachable = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary)
                    )
                }

                if (isEditing && onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { onDelete(initialDevice) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove Device from List", color = WemoErrorRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPort = portText.toIntOrNull() ?: 49153
                    val parsedAutoOff = autoOffText.toIntOrNull() ?: 0
                    val finalName = name.ifBlank { "Living Room Switch" }
                    val finalRoom = roomName.ifBlank { inferRoomFromName(finalName) }
                    onSave(
                        initialDevice?.id ?: 0,
                        finalName,
                        finalRoom,
                        selectedType,
                        ipAddress.ifBlank { "192.168.1.115" },
                        parsedPort,
                        brightness.roundToInt(),
                        isReachable,
                        isOn,
                        parsedAutoOff
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                modifier = Modifier.testTag("save_device_button")
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
