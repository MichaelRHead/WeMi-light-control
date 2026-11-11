package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.classifyDeviceCategory
import org.suppressingfire.wemi.data.inferRoomFromName
import org.suppressingfire.wemi.network.ScannedAp
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.SetupDeviceDescriptor
import org.suppressingfire.wemi.ui.theme.WemoErrorRed
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft

@Composable
fun ProvisionApDialog(
    scannedAps: List<ScannedAp>,
    isScanning: Boolean,
    isProvisioning: Boolean,
    defaultSsid: String = "",
    detectedDevice: SetupDeviceDescriptor? = null,
    statusMessage: String? = null,
    debugSnapshot: SetupDebugSnapshot = SetupDebugSnapshot(),
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
        detectedDevice?.friendlyName ?: "New Smart Switch"
    }
    val previewCategory = classifyDeviceCategory(
        name = previewName,
        deviceType = detectedDevice?.deviceType ?: DeviceType.SWITCH_PLUG
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Provision New Switch AP (10.22.22.1)",
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
                    text = "1. Connect your phone/tablet Wi-Fi to the factory-reset switch's setup AP (10.22.22.1).\n2. Scan or enter your 2.4 GHz Home Wi-Fi credentials and name your switch below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ProvisionSetupStatusBanner(
                    apHost = apHost,
                    detectedDevice = detectedDevice,
                    statusMessage = statusMessage
                )

                // Live On-Screen Setup Diagnostic & Trace Panel (Screenshot-Ready)
                ProvisionDebugPanel(
                    debugSnapshot = debugSnapshot,
                    selectedSsid = ssid,
                    keepOpenOnSuccess = keepDialogOpenOnSuccess,
                    onToggleKeepOpen = { keepDialogOpenOnSuccess = !keepDialogOpenOnSuccess }
                )

                ProvisionWifiTargetSection(
                    apHost = apHost,
                    onApHostChange = { apHost = it },
                    isScanning = isScanning,
                    isProvisioning = isProvisioning,
                    onScanApList = onScanApList,
                    scannedAps = scannedAps,
                    ssid = ssid,
                    password = password,
                    channelText = channelText,
                    authMode = authMode,
                    encryptionType = encryptionType,
                    onSelectScannedAp = { ap ->
                        ssid = ap.ssid
                        channelText = ap.channel.toString()
                        authMode = ap.authMode
                        encryptionType = ap.encryptionType
                    },
                    onSsidChange = { nextSsid ->
                        ssid = nextSsid
                        val matched = scannedAps.firstOrNull {
                            it.ssid.equals(nextSsid.trim(), ignoreCase = true)
                        }
                        if (matched != null) {
                            channelText = matched.channel.toString()
                            authMode = matched.authMode
                            encryptionType = matched.encryptionType
                        }
                    },
                    onPasswordChange = { password = it },
                    onChannelChange = { channelText = it },
                    onAuthModeChange = { authMode = it },
                    onEncryptionTypeChange = { encryptionType = it }
                )

                ProvisionCategoryAndRoomSection(
                    customDeviceName = customDeviceName,
                    onCustomDeviceNameChange = {
                        customDeviceName = it
                        if (it.isNotBlank()) {
                            selectedRoom = inferRoomFromName(it)
                        }
                    },
                    previewCategory = previewCategory,
                    selectedRoom = selectedRoom,
                    onSelectRoom = { selectedRoom = it }
                )

                ProvisionCryptoOptionsSection(
                    detectedDevice = detectedDevice,
                    encryptionMethod = encryptionMethod,
                    onSelectEncryptionMethod = { encryptionMethod = it },
                    addPasswordLengthsOverride = addPasswordLengthsOverride,
                    onSelectAddPasswordLengths = { addPasswordLengthsOverride = it }
                )
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

@Composable
private fun ProvisionSetupStatusBanner(
    apHost: String,
    detectedDevice: SetupDeviceDescriptor?,
    statusMessage: String?
) {
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
                ?: if (detectedDevice == null) {
                    "Ready to probe Smart Switch Setup AP at $apHost (ports 49151–49159)..."
                } else {
                    null
                }
            if (displayStatus != null) {
                Text(
                    text = displayStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWarningOrError) WemoErrorRed else WemoGreenDark
                )
            }
        }
    }
}
