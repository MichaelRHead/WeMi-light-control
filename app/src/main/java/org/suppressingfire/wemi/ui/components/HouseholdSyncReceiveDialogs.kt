package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.suppressingfire.wemi.network.HouseholdImportMode
import org.suppressingfire.wemi.network.HouseholdSyncCrypto
import org.suppressingfire.wemi.network.HouseholdSyncServerSession
import org.suppressingfire.wemi.ui.PendingHouseholdImport
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

private val SyncReceiveInfoSurface = Color(0xFFE0F2FE)

/**
 * Mobile / Tablet / Chromebook receiver dialog showing the temporary 6-digit pairing code and
 * local IP/port while listening for an encrypted push on the home Wi-Fi network.
 */
@Composable
internal fun HouseholdSyncReceiveDialog(
    session: HouseholdSyncServerSession?,
    statusMessage: String?,
    onStartNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    var remainingSeconds by remember(session?.expiresAtEpochMs) {
        mutableIntStateOf(
            session?.let { ((it.expiresAtEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0) } ?: 0
        )
    }

    LaunchedEffect(session?.expiresAtEpochMs) {
        if (session == null) return@LaunchedEffect
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = ((session.expiresAtEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("household_sync_receive_dialog"),
        title = {
            Text(
                text = "Receive Household Setup",
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "On your other WeMi device, open More -> Household Config Sync -> Send to TV and enter this one-time code:",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = WemoTextSecondary
                )
                if (session != null) {
                    Surface(
                        color = SyncReceiveInfoSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, WemoGreenPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = HouseholdSyncCrypto.formatPairingCode(session.pairingCode),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                color = WemoGreenDark,
                                modifier = Modifier.testTag("household_sync_receive_pin")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${session.ipAddress}:${session.port}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = WemoTextPrimary,
                                modifier = Modifier.testTag("household_sync_receive_endpoint")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val mins = remainingSeconds / 60
                            val secs = (remainingSeconds % 60).toString().padStart(2, '0')
                            Text(
                                text = "Encrypted local transfer • Expires in $mins:$secs",
                                fontSize = 12.sp,
                                color = WemoTextSecondary
                            )
                        }
                    }
                } else if (statusMessage != null) {
                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        color = WemoTextSecondary,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("household_sync_receive_status")
                    )
                }
            }
        },
        confirmButton = {
            if (session == null) {
                Button(
                    onClick = onStartNewSession,
                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("household_sync_receive_new_code_button")
                ) {
                    Text("New Code")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("household_sync_receive_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Confirmation modal shown after decoding an imported JSON file or receiving a verified local-LAN
 * push, letting the user choose between identity-safe Merge or full Replace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HouseholdImportConfirmDialog(
    pending: PendingHouseholdImport,
    onConfirm: (HouseholdImportMode) -> Unit,
    onCancel: () -> Unit
) {
    val bundle = pending.bundle
    val sampleRooms = remember(bundle) {
        bundle.devices.map { it.roomName }.distinct().take(5).joinToString(", ")
    }

    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("household_import_confirm_dialog"),
        title = {
            Text(
                text = "Import Household Setup?",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Source: ${pending.sourceDescription}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = WemoTextPrimary
                )
                Text(
                    text = "${bundle.homes.size} Wi-Fi Home(s) • ${bundle.devices.size} switch(es) across ${bundle.distinctRoomCount} room(s).",
                    fontSize = 13.sp,
                    color = WemoTextSecondary
                )
                if (sampleRooms.isNotEmpty()) {
                    Text(
                        text = "Rooms: $sampleRooms",
                        fontSize = 12.5.sp,
                        color = WemoGreenDark
                    )
                }
                Text(
                    text = "Merge updates matching switches by hardware serial/UDN while keeping other local switches. Replace swaps your saved homes and switches with this bundle.",
                    fontSize = 12.sp,
                    color = WemoTextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(HouseholdImportMode.MERGE) },
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("household_import_merge_button")
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onConfirm(HouseholdImportMode.REPLACE) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("household_import_replace_button")
                ) {
                    Text("Replace All", color = WemoGreenDark)
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("household_import_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
