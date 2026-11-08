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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.classifyDeviceCategory
import com.example.data.inferRoomFromName
import com.example.network.WeMoScannedAp
import com.example.network.WeMoSetupDeviceDescriptor
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvisionWeMoApDialog(
    scannedAps: List<WeMoScannedAp>,
    isScanning: Boolean,
    isProvisioning: Boolean,
    defaultSsid: String = "",
    detectedDevice: WeMoSetupDeviceDescriptor? = null,
    statusMessage: String? = null,
    onScanApList: (String) -> Unit,
    onProvision: (
        apHost: String,
        ssid: String,
        password: String,
        channel: Int,
        authMode: String,
        encryptionType: String,
        encryptionMethod: Int,
        customDeviceName: String,
        roomName: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var apHost by remember { mutableStateOf("10.22.22.1") }
    var ssid by remember { mutableStateOf(defaultSsid) }
    var password by remember { mutableStateOf("") }
    var channelText by remember { mutableStateOf("6") }
    var authMode by remember { mutableStateOf("WPA2PSK") }
    var encryptionType by remember { mutableStateOf("AES") }
    // 0 = Auto-Detect from /setup.xml (<rtos>, <iot>, <binaryOption>), 1 = Method 1, 2 = Method 2, 3 = Method 3
    var encryptionMethod by remember { mutableIntStateOf(0) }
    var customDeviceName by remember { mutableStateOf(detectedDevice?.friendlyName.orEmpty()) }
    var selectedRoom by remember { mutableStateOf("Living Room") }

    LaunchedEffect(detectedDevice) {
        if (detectedDevice != null && customDeviceName.isBlank()) {
            customDeviceName = detectedDevice.friendlyName
            selectedRoom = inferRoomFromName(detectedDevice.friendlyName)
        }
    }

    LaunchedEffect(scannedAps) {
        if (scannedAps.isNotEmpty()) {
            val match = scannedAps.firstOrNull { it.ssid.equals(ssid, ignoreCase = true) }
                ?: scannedAps.maxByOrNull { it.signalStrengthPercent }
            if (match != null) {
                ssid = match.ssid
                channelText = match.channel.toString()
                authMode = match.authMode
                encryptionType = match.encryptionType
            }
        }
    }

    val previewName = customDeviceName.ifBlank {
        detectedDevice?.friendlyName ?: "New WeMo Switch"
    }
    val previewCategory = classifyDeviceCategory(
        name = previewName,
        deviceType = detectedDevice?.deviceType ?: WeMoDeviceType.SWITCH_PLUG
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Provision New WeMo AP (10.22.22.1)",
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
                Text(
                    text = "1. Connect your phone/tablet Wi-Fi to the factory-reset switch's setup AP (e.g. WeMo.Light.xxx or WeMo.Mini.xxx).\n2. Scan or enter your 2.4 GHz Home Wi-Fi credentials and name your switch below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (detectedDevice != null || !statusMessage.isNullOrBlank()) {
                    Surface(
                        color = WemoGreenSoft.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provision_ap_status_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (detectedDevice != null) {
                                Text(
                                    text = "Detected Hardware: ${detectedDevice.friendlyName} (${detectedDevice.modelName} • Port ${detectedDevice.port})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WemoGreenDark
                                )
                                Text(
                                    text = "MAC ${detectedDevice.macAddress} • SN ${detectedDevice.serialNumber} • Recommended AES Method ${detectedDevice.recommendedEncryptionMethod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!statusMessage.isNullOrBlank()) {
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WemoGreenDark
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = apHost,
                        onValueChange = { apHost = it },
                        label = { Text("Setup AP IP") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("provision_ap_ip_input")
                    )
                    OutlinedButton(
                        onClick = { onScanApList(apHost.ifBlank { "10.22.22.1" }) },
                        enabled = !isScanning && !isProvisioning,
                        modifier = Modifier.testTag("scan_setup_ap_button")
                    ) {
                        Text(if (isScanning) "Scanning..." else "Scan APs")
                    }
                }

                if (scannedAps.isNotEmpty()) {
                    Text(
                        text = "Discovered 2.4 GHz Networks (from GetApList):",
                        style = MaterialTheme.typography.labelMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        scannedAps.forEach { ap ->
                            FilterChip(
                                selected = ssid == ap.ssid,
                                onClick = {
                                    ssid = ap.ssid
                                    channelText = ap.channel.toString()
                                    authMode = ap.authMode
                                    encryptionType = ap.encryptionType
                                },
                                label = {
                                    Text("${ap.ssid} (Ch ${ap.channel} • ${ap.signalStrengthPercent}%)")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WemoGreenSoft,
                                    selectedLabelColor = WemoGreenDark
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Target 2.4 GHz Wi-Fi SSID") },
                    placeholder = { Text("e.g. Home-WiFi-2.4G") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provision_ssid_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Wi-Fi Password") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provision_password_input")
                )

                OutlinedTextField(
                    value = customDeviceName,
                    onValueChange = {
                        customDeviceName = it
                        if (it.isNotBlank()) {
                            selectedRoom = inferRoomFromName(it)
                        }
                    },
                    label = { Text("Switch Name (Optional)") },
                    placeholder = { Text("e.g. Porch Christmas Tree or Bedroom Ceiling Fan") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provision_device_name_input")
                )

                Surface(
                    color = WemoGreenSoft.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WeMoDeviceCategoryIllustration(
                            category = previewCategory,
                            isReachable = true,
                            isOn = true,
                            brightness = 100,
                            modifier = Modifier.size(width = 38.dp, height = 46.dp)
                        )
                        Column {
                            Text(
                                text = "Icon Category: ${previewCategory.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = WemoGreenDark
                            )
                            Text(
                                text = "Assigned Room: $selectedRoom",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Hardware AES-128-CBC Key Derivation",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = encryptionMethod == 0,
                        onClick = { encryptionMethod = 0 },
                        label = {
                            val autoSuffix = detectedDevice?.let { " (Method ${it.recommendedEncryptionMethod})" }.orEmpty()
                            Text("Auto-Detect from /setup.xml$autoSuffix")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        )
                    )
                    FilterChip(
                        selected = encryptionMethod == 1,
                        onClick = { encryptionMethod = 1 },
                        label = { Text("Method 1: Gen 1 OpenWrt (F7C030/059/063)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        )
                    )
                    FilterChip(
                        selected = encryptionMethod == 2,
                        onClick = { encryptionMethod = 2 },
                        label = { Text("Method 2: Gen 2 RTOS (WLS040/0403/WDS060)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        )
                    )
                    FilterChip(
                        selected = encryptionMethod == 3,
                        onClick = { encryptionMethod = 3 },
                        label = { Text("Method 3: BinaryOption Symmetric") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onProvision(
                        apHost.ifBlank { "10.22.22.1" },
                        ssid.ifBlank { defaultSsid.ifBlank { "Home-WiFi-2.4G" } },
                        password,
                        channelText.toIntOrNull() ?: 6,
                        authMode.ifBlank { "WPA2PSK" },
                        encryptionType.ifBlank { "AES" },
                        encryptionMethod,
                        customDeviceName.trim(),
                        selectedRoom.ifBlank { "Living Room" }
                    )
                },
                enabled = !isProvisioning,
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                modifier = Modifier.testTag("confirm_provision_ap_button")
            ) {
                Text(if (isProvisioning) "Provisioning..." else "Provision Switch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}


