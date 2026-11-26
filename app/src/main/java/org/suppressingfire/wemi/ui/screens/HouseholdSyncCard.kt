package org.suppressingfire.wemi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.network.HouseholdSyncCodec
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Zero-cloud Household Configuration Sync card on the `More` tab:
 * - Export/Import compact JSON via Android Storage Access Framework (zero storage permissions).
 * - One-tap PIN-encrypted local-LAN push/receive for syncing a phone setup onto Android TV,
 *   another phone, tablet, or Chromebook.
 */
@Composable
internal fun HouseholdSyncCard(
    homeCount: Int,
    deviceCount: Int,
    onExportHouseholdJson: (writeJson: suspend (String) -> Unit) -> Unit,
    onImportHouseholdStream: (sourceDescription: String, openStream: () -> java.io.InputStream?) -> Unit,
    onOpenSendLanSync: () -> Unit,
    onOpenReceiveLanSync: () -> Unit
) {
    val context = LocalContext.current
    val compactButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportHouseholdJson { jsonText ->
                (context.contentResolver.openOutputStream(uri, "wt")
                    ?: context.contentResolver.openOutputStream(uri))?.use { out ->
                    out.write(jsonText.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Could not open destination file")
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportHouseholdStream("JSON File") {
                context.contentResolver.openInputStream(uri)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = WemoRowSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more_household_sync_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = WemoGreenPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Household Config Sync",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = WemoTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Share your $homeCount home(s) and $deviceCount switch(es) (custom names, rooms, order, Wi-Fi names, local LAN IPs, and switch IDs) via JSON or PIN-authenticated local Wi-Fi pairing — no cloud account or Wi-Fi passwords included.",
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = WemoTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenSendLanSync,
                    contentPadding = compactButtonPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("more_send_lan_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Send to TV",
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenReceiveLanSync,
                    contentPadding = compactButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("more_receive_lan_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WemoGreenDark
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Receive",
                        color = WemoGreenDark,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { exportJsonLauncher.launch(HouseholdSyncCodec.DEFAULT_EXPORT_FILENAME) },
                    contentPadding = compactButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("more_export_household_json_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WemoTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export JSON",
                        color = WemoTextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = {
                        importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    contentPadding = compactButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("more_import_household_json_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WemoTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import JSON",
                        color = WemoTextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
