package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.WeMoDeviceType
import com.example.data.WeMoRepository
import com.example.data.classifyDeviceCategory
import com.example.data.inferRoomFromName
import com.example.network.WeMoScannedAp
import com.example.network.WeMoSetupDebugSnapshot
import com.example.network.WeMoSetupDeviceDescriptor
import com.example.ui.theme.WemoErrorRed
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvisionWeMoApDialog(
    scannedAps: List<WeMoScannedAp>,
    isScanning: Boolean,
    isProvisioning: Boolean,
    defaultSsid: String = "",
    detectedDevice: WeMoSetupDeviceDescriptor? = null,
    statusMessage: String? = null,
    debugSnapshot: WeMoSetupDebugSnapshot = WeMoSetupDebugSnapshot(),
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
    onProvisionAdvanced: ((
        apHost: String,
        ssid: String,
        password: String,
        channel: Int,
        authMode: String,
        encryptionType: String,
        encryptionMethod: Int,
        addPasswordLengths: Boolean?,
        customDeviceName: String,
        roomName: String,
        keepDialogOpenOnSuccess: Boolean
    ) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var apHost by remember { mutableStateOf("10.22.22.1") }
    var ssid by remember { mutableStateOf(defaultSsid) }
    var password by remember { mutableStateOf("") }
    var channelText by remember { mutableStateOf("6") }
    var authMode by remember { mutableStateOf("WPA2PSK") }
    var encryptionType by remember { mutableStateOf("AES") }
    // 0 = Auto-Detect from /setup.xml (<rtos>, <iot>, <binaryOption>, <new_algo>), 1 = Method 1, 2 = Method 2, 3 = Method 3
    var encryptionMethod by remember { mutableIntStateOf(0) }
    // null = Auto (true for Method 1/3, false for Method 2), true = Append +4 hex lengths, false = No length suffix
    var addPasswordLengthsOverride by remember { mutableStateOf<Boolean?>(null) }
    var keepDialogOpenOnSuccess by remember { mutableStateOf(false) }
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

                val isWarningOrError = statusMessage?.contains("No response", ignoreCase = true) == true ||
                    statusMessage?.contains("rejected", ignoreCase = true) == true ||
                    statusMessage?.contains("unreachable", ignoreCase = true) == true ||
                    statusMessage?.contains("did not join", ignoreCase = true) == true
                Surface(
                    color = if (isWarningOrError) Color(0xFFFEF2F2) else WemoGreenSoft.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provision_ap_status_box")
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .testTag("provision_status_box"),
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
                                text = "MAC ${detectedDevice.macAddress} • SN ${detectedDevice.serialNumber} • Recommended AES Method ${detectedDevice.recommendedEncryptionMethod} (len=${detectedDevice.recommendedAddLengths})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val displayStatus = statusMessage?.takeIf { it.isNotBlank() }
                            ?: if (detectedDevice == null) "Ready to probe Belkin WeMo Setup AP at $apHost (ports 49151–49159)..." else null
                        if (displayStatus != null) {
                            Text(
                                text = displayStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isWarningOrError) WemoErrorRed else WemoGreenDark
                            )
                        }
                    }
                }

                // Live On-Screen Setup Diagnostic & Trace Panel (Screenshot-Ready)
                WeMoProvisionDebugPanel(
                    debugSnapshot = debugSnapshot,
                    selectedSsid = ssid,
                    keepOpenOnSuccess = keepDialogOpenOnSuccess,
                    onToggleKeepOpen = { keepDialogOpenOnSuccess = !keepDialogOpenOnSuccess }
                )

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
                    onValueChange = { nextSsid ->
                        ssid = nextSsid
                        val matched = scannedAps.firstOrNull { it.ssid.equals(nextSsid.trim(), ignoreCase = true) }
                        if (matched != null) {
                            channelText = matched.channel.toString()
                            authMode = matched.authMode
                            encryptionType = matched.encryptionType
                        }
                    },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = channelText,
                        onValueChange = { channelText = it },
                        label = { Text("2.4G Ch") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.7f)
                            .testTag("provision_channel_input")
                    )
                    OutlinedTextField(
                        value = authMode,
                        onValueChange = { authMode = it },
                        label = { Text("Auth Mode") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("provision_auth_input")
                    )
                    OutlinedTextField(
                        value = encryptionType,
                        onValueChange = { encryptionType = it },
                        label = { Text("Encrypt") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("provision_encrypt_input")
                    )
                }

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

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WeMoRepository.DEFAULT_ROOMS.forEach { presetRoom ->
                        FilterChip(
                            selected = selectedRoom.equals(presetRoom, ignoreCase = true),
                            onClick = { selectedRoom = presetRoom },
                            label = { Text(presetRoom) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WemoGreenSoft,
                                selectedLabelColor = WemoGreenDark
                            )
                        )
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

                Text(
                    text = "Password Length Suffix (xxyy hex)",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = addPasswordLengthsOverride == null,
                        onClick = { addPasswordLengthsOverride = null },
                        label = { Text("Auto (M1/M3=Yes, M2=No)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        ),
                        modifier = Modifier.testTag("provision_len_auto_chip")
                    )
                    FilterChip(
                        selected = addPasswordLengthsOverride == true,
                        onClick = { addPasswordLengthsOverride = true },
                        label = { Text("Append +4 Hex Lengths") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        ),
                        modifier = Modifier.testTag("provision_len_true_chip")
                    )
                    FilterChip(
                        selected = addPasswordLengthsOverride == false,
                        onClick = { addPasswordLengthsOverride = false },
                        label = { Text("No Length Suffix") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        ),
                        modifier = Modifier.testTag("provision_len_false_chip")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resolvedHost = apHost.ifBlank { "10.22.22.1" }
                    val resolvedSsid = ssid.ifBlank { defaultSsid.ifBlank { "Home-WiFi-2.4G" } }
                    val resolvedCh = channelText.toIntOrNull() ?: 6
                    val resolvedAuth = authMode.ifBlank { "WPA2PSK" }
                    val resolvedEnc = encryptionType.ifBlank { "AES" }
                    val resolvedName = customDeviceName.trim()
                    val resolvedRoom = selectedRoom.ifBlank { "Living Room" }

                    if (onProvisionAdvanced != null) {
                        onProvisionAdvanced(
                            resolvedHost,
                            resolvedSsid,
                            password,
                            resolvedCh,
                            resolvedAuth,
                            resolvedEnc,
                            encryptionMethod,
                            addPasswordLengthsOverride,
                            resolvedName,
                            resolvedRoom,
                            keepDialogOpenOnSuccess
                        )
                    } else {
                        onProvision(
                            resolvedHost,
                            resolvedSsid,
                            password,
                            resolvedCh,
                            resolvedAuth,
                            resolvedEnc,
                            encryptionMethod,
                            resolvedName,
                            resolvedRoom
                        )
                    }
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

@Suppress("DEPRECATION")
@Composable
private fun WeMoProvisionDebugPanel(
    debugSnapshot: WeMoSetupDebugSnapshot,
    selectedSsid: String,
    keepOpenOnSuccess: Boolean,
    onToggleKeepOpen: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }
    var sharedFeedback by remember { mutableStateOf(false) }
    val displayLines = remember(debugSnapshot, selectedSsid) {
        debugSnapshot.toDisplayLines(selectedSsid)
    }

    Surface(
        color = Color(0xFF12161F),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2E384D)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provision_debug_panel")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SETUP DEBUG DETAILS (SCREENSHOT)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0xFF8BE836),
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            val reportText = debugSnapshot.toClipboardText(selectedSsid)
                            clipboardManager.setText(AnnotatedString(reportText))
                            runCatching {
                                com.example.network.WeMoTelemetryFileExporter.saveTelemetryReportToFile(context, reportText)
                            }
                            copiedFeedback = true
                        },
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier
                            .height(24.dp)
                            .testTag("copy_provision_debug_button")
                    ) {
                        Text(
                            text = if (copiedFeedback) "Copied" else "Copy",
                            fontSize = 10.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val reportText = debugSnapshot.toClipboardText(selectedSsid)
                            com.example.network.WeMoTelemetryFileExporter.copyAndShareTelemetryReport(
                                context = context,
                                reportText = reportText,
                                subject = "WeMi Setup AP Diagnostic Report"
                            )
                            sharedFeedback = true
                        },
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier
                            .height(24.dp)
                            .testTag("share_provision_debug_button")
                    ) {
                        Text(
                            text = if (sharedFeedback) "Saved" else "Save/Share",
                            fontSize = 10.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provision_debug_text"),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                displayLines.forEach { line ->
                    val lineColor = when {
                        line.contains("WARN", ignoreCase = true) ||
                            line.contains("ERROR", ignoreCase = true) ||
                            line.contains("MISSING", ignoreCase = true) ||
                            line.contains("No WeMo setup", ignoreCase = true) ||
                            line.contains("Status=2", ignoreCase = true) ||
                            line.contains("HOLD", ignoreCase = true) -> Color(0xFFFCA5A5)
                        line.contains("Status=1", ignoreCase = true) ||
                            line.contains("HTTP_200", ignoreCase = true) ||
                            line.contains("CLOSE] CloseSetup -> success", ignoreCase = true) -> Color(0xFF86EFAC)
                        else -> Color(0xFFE2E8F0)
                    }
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = lineColor
                    )
                }
            }

            FilterChip(
                selected = keepOpenOnSuccess,
                onClick = onToggleKeepOpen,
                label = {
                    Text(
                        text = if (keepOpenOnSuccess) {
                            "Keep Dialog Open on Success: ON (for screenshot)"
                        } else {
                            "Keep Dialog Open on Success: OFF (stays open on any error)"
                        },
                        fontSize = 10.5.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF1E293B),
                    labelColor = Color(0xFFCBD5E1),
                    selectedContainerColor = Color(0xFF14532D),
                    selectedLabelColor = Color(0xFFBBF7D0)
                ),
                modifier = Modifier.testTag("keep_provision_dialog_open_chip")
            )
        }
    }
}
