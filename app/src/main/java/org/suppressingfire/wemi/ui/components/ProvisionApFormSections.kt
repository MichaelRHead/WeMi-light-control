package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.suppressingfire.wemi.data.DeviceCategory
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.ScannedAp
import org.suppressingfire.wemi.network.SetupDeviceDescriptor
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProvisionWifiTargetSection(
    apHost: String,
    onApHostChange: (String) -> Unit,
    isScanning: Boolean,
    isProvisioning: Boolean,
    onScanApList: (String) -> Unit,
    scannedAps: List<ScannedAp>,
    ssid: String,
    password: String,
    channelText: String,
    authMode: String,
    encryptionType: String,
    onSelectScannedAp: (ScannedAp) -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onChannelChange: (String) -> Unit,
    onAuthModeChange: (String) -> Unit,
    onEncryptionTypeChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = apHost,
            onValueChange = onApHostChange,
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
                    onClick = { onSelectScannedAp(ap) },
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
        onValueChange = onSsidChange,
        label = { Text("Target 2.4 GHz Wi-Fi SSID") },
        placeholder = { Text("e.g. Home-WiFi-2.4G") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provision_ssid_input")
    )

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
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
            onValueChange = onChannelChange,
            label = { Text("2.4G Ch") },
            singleLine = true,
            modifier = Modifier
                .weight(0.7f)
                .testTag("provision_channel_input")
        )
        OutlinedTextField(
            value = authMode,
            onValueChange = onAuthModeChange,
            label = { Text("Auth Mode") },
            singleLine = true,
            modifier = Modifier
                .weight(1.2f)
                .testTag("provision_auth_input")
        )
        OutlinedTextField(
            value = encryptionType,
            onValueChange = onEncryptionTypeChange,
            label = { Text("Encrypt") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("provision_encrypt_input")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProvisionCategoryAndRoomSection(
    customDeviceName: String,
    onCustomDeviceNameChange: (String) -> Unit,
    previewCategory: DeviceCategory,
    selectedRoom: String,
    onSelectRoom: (String) -> Unit
) {
    OutlinedTextField(
        value = customDeviceName,
        onValueChange = onCustomDeviceNameChange,
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
            DeviceCategoryIllustration(
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
        SwitchRepository.DEFAULT_ROOMS.forEach { presetRoom ->
            FilterChip(
                selected = selectedRoom.equals(presetRoom, ignoreCase = true),
                onClick = { onSelectRoom(presetRoom) },
                label = { Text(presetRoom) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WemoGreenSoft,
                    selectedLabelColor = WemoGreenDark
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProvisionCryptoOptionsSection(
    detectedDevice: SetupDeviceDescriptor?,
    encryptionMethod: Int,
    onSelectEncryptionMethod: (Int) -> Unit,
    addPasswordLengthsOverride: Boolean?,
    onSelectAddPasswordLengths: (Boolean?) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )

    Text(
        text = "Hardware AES-128-CBC Key Derivation",
        style = MaterialTheme.typography.labelMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val autoSuffix = detectedDevice?.let { " (Method ${it.recommendedEncryptionMethod})" }.orEmpty()
        val methodLabels = listOf(
            0 to "Auto-Detect from /setup.xml$autoSuffix",
            1 to "Method 1: Gen 1 OpenWrt (F7C030/059/063)",
            2 to "Method 2: Gen 2 RTOS (WLS040/0403/WDS060)",
            3 to "Method 3: BinaryOption Symmetric"
        )
        methodLabels.forEach { (methodId, label) ->
            FilterChip(
                selected = encryptionMethod == methodId,
                onClick = { onSelectEncryptionMethod(methodId) },
                label = { Text(label) },
                colors = chipColors
            )
        }
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
            onClick = { onSelectAddPasswordLengths(null) },
            label = { Text("Auto (M1/M3=Yes, M2=No)") },
            colors = chipColors,
            modifier = Modifier.testTag("provision_len_auto_chip")
        )
        FilterChip(
            selected = addPasswordLengthsOverride == true,
            onClick = { onSelectAddPasswordLengths(true) },
            label = { Text("Append +4 Hex Lengths") },
            colors = chipColors,
            modifier = Modifier.testTag("provision_len_true_chip")
        )
        FilterChip(
            selected = addPasswordLengthsOverride == false,
            onClick = { onSelectAddPasswordLengths(false) },
            label = { Text("No Length Suffix") },
            colors = chipColors,
            modifier = Modifier.testTag("provision_len_false_chip")
        )
    }
}
