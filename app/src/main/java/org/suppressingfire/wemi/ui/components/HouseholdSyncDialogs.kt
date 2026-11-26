package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.network.HouseholdSyncCrypto
import org.suppressingfire.wemi.network.HouseholdSyncLanServer
import org.suppressingfire.wemi.network.HouseholdSyncPeer
import org.suppressingfire.wemi.ui.HouseholdSyncUiState
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

private val SyncErrorSurface = Color(0xFFFEE2E2)
private val SyncErrorText = Color(0xFFB91C1C)
private val SyncInfoSurface = Color(0xFFE0F2FE)

/**
 * Mobile / Tablet / Chromebook dialog for sending the current household configuration over local
 * Wi-Fi to an Android TV or another WeMi device displaying a 6-digit pairing code.
 */
@Composable
internal fun HouseholdSyncSendDialog(
    state: HouseholdSyncUiState,
    onRescanPeers: () -> Unit,
    onPushToPeer: (ip: String, port: Int, pairingCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    var targetIp by remember { mutableStateOf("") }
    var targetPortText by remember { mutableStateOf(HouseholdSyncLanServer.DEFAULT_LISTEN_PORT.toString()) }
    // Keep one-time pairing code in volatile memory only (never rememberSaveable)
    var pairingCode by remember { mutableStateOf("") }

    LaunchedEffect(state.discoveredPeers) {
        if (targetIp.isBlank() && state.discoveredPeers.size == 1) {
            val onlyPeer = state.discoveredPeers.first()
            targetIp = onlyPeer.ipAddress
            targetPortText = onlyPeer.port.toString()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.isPushingToPeer) onDismiss() },
        modifier = Modifier.testTag("household_sync_send_dialog"),
        title = {
            Text(
                text = "Send Household Setup",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = WemoTextPrimary
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
                    text = "Open Sync on your Android TV or Receive on another phone/Chromebook, then enter its 6-digit code.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = WemoTextSecondary
                )

                if (state.sendErrorMessage != null) {
                    Surface(
                        color = SyncErrorSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("household_sync_send_error")
                    ) {
                        Text(
                            text = state.sendErrorMessage,
                            color = SyncErrorText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (state.sendStatusMessage != null) {
                    Surface(
                        color = SyncInfoSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.sendStatusMessage,
                            color = WemoGreenDark,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby Receivers",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = WemoTextPrimary
                    )
                    TextButton(
                        onClick = onRescanPeers,
                        enabled = !state.isDiscoveringPeers && !state.isPushingToPeer,
                        modifier = Modifier.testTag("household_sync_rescan_peers_button")
                    ) {
                        if (state.isDiscoveringPeers) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan")
                        }
                    }
                }

                state.discoveredPeers.forEach { peer ->
                    val isSelected = targetIp == peer.ipAddress && targetPortText == peer.port.toString()
                    SyncPeerRow(
                        peer = peer,
                        isSelected = isSelected,
                        onSelect = {
                            targetIp = peer.ipAddress
                            targetPortText = peer.port.toString()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it.trim() },
                        label = { Text("Receiver IP") },
                        placeholder = { Text("192.168.1.42") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.7f)
                            .testTag("household_sync_target_ip_input")
                    )
                    OutlinedTextField(
                        value = targetPortText,
                        onValueChange = { raw -> targetPortText = raw.filter { it.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("household_sync_target_port_input")
                    )
                }

                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { raw ->
                        pairingCode = raw.filter { it.isDigit() || it == '-' }.take(7)
                    },
                    label = { Text("6-Digit Pairing Code") },
                    placeholder = { Text("123-456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("household_sync_pin_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = targetPortText.toIntOrNull() ?: HouseholdSyncLanServer.DEFAULT_LISTEN_PORT
                    onPushToPeer(targetIp, port, pairingCode)
                },
                enabled = !state.isPushingToPeer &&
                    targetIp.isNotBlank() &&
                    HouseholdSyncCrypto.isValidPairingCode(pairingCode),
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("household_sync_send_confirm_button")
            ) {
                Text(if (state.isPushingToPeer) "Sending..." else "Send Setup")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isPushingToPeer,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SyncPeerRow(
    peer: HouseholdSyncPeer,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) SyncInfoSurface else Color(0xFFF8FAFC),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) WemoGreenPrimary else Color(0xFFCBD5E1)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("household_sync_peer_${peer.ipAddress}_${peer.port}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (peer.isTv) Icons.Default.Tv else Icons.Default.Devices,
                contentDescription = null,
                tint = WemoGreenDark,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = WemoTextPrimary
                )
                Text(
                    text = "${peer.ipAddress}:${peer.port}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = WemoTextSecondary
                )
            }
            Text(
                text = if (isSelected) "Selected" else "Select",
                color = WemoGreenDark,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}
