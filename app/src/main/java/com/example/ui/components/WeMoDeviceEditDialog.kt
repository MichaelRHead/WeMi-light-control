package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.classifyDeviceCategory
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
        autoOffMinutes: Int,
        syncFriendlyNameToHardware: Boolean
    ) -> Unit,
    onDelete: ((WeMoDeviceEntity) -> Unit)? = null,
    onScanNetwork: (() -> Unit)? = null,
    onOpenProvisionWizard: (() -> Unit)? = null
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
    var syncNameToHardware by remember { mutableStateOf(true) }
    val detectedCategory = classifyDeviceCategory(
        name = name.ifBlank { "Switch" },
        deviceType = selectedType
    )

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

                if (!isEditing && onOpenProvisionWizard != null) {
                    OutlinedButton(
                        onClick = {
                            onOpenProvisionWizard()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_provision_ap_wizard_button")
                    ) {
                        Text(
                            text = "Provision New WeMo AP (10.22.22.1)",
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
                    placeholder = { Text("e.g. Christmas Tree, Ceiling Fan, Porch Light") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_input")
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WemoGreenSoft.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WeMoDeviceCategoryIllustration(
                            category = detectedCategory,
                            isReachable = isReachable,
                            isOn = isOn,
                            brightness = brightness.roundToInt(),
                            modifier = Modifier.size(width = 40.dp, height = 50.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Classified Icon: ${detectedCategory.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = WemoGreenDark
                            )
                            Text(
                                text = "Based on switch name (Tree Lights, Fans, Lamps/Lights, Sensors, Switches)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Name to WeMo Hardware",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Updates <friendlyName> on switch via ChangeFriendlyName SOAP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncNameToHardware,
                        onCheckedChange = { syncNameToHardware = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary),
                        modifier = Modifier.testTag("sync_hardware_name_switch")
                    )
                }

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
                        parsedAutoOff,
                        syncNameToHardware
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
