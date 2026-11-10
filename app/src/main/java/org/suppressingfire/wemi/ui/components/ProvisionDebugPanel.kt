package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.TelemetryFileExporter

/**
 * Screenshot-ready on-screen diagnostic and trace panel rendered inside [ProvisionApDialog].
 *
 * Displays live `/setup.xml` / `GetMetaInfo` MAC & serial source, `GetApList` channel/auth match,
 * AES-128-CBC encryption method & length suffix, and step-by-step `ConnectHomeNetwork` /
 * `GetNetworkStatus` / `CloseSetup` trace lines, with one-tap `Copy` and `Save/Share` export to
 * `wemi-telemetry-latest.txt`.
 */
@Suppress("DEPRECATION")
@Composable
internal fun ProvisionDebugPanel(
    debugSnapshot: SetupDebugSnapshot,
    selectedSsid: String,
    keepOpenOnSuccess: Boolean,
    onToggleKeepOpen: () -> Unit
) {
    val context = LocalContext.current
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
                                TelemetryFileExporter.saveTelemetryReportToFile(context, reportText)
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
                            TelemetryFileExporter.copyAndShareTelemetryReport(
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
                            line.contains("No setup descriptor", ignoreCase = true) ||
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
