package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.TelemetryFileExporter

/**
 * Expandable on-screen diagnostic and trace panel rendered inside [ProvisionApDialog].
 *
 * Stays compact while idle and auto-expands when AP scanning, provisioning traces, or errors
 * occur, displaying `/setup.xml` / `GetMetaInfo` MAC & serial source, `GetApList` channel/auth
 * match, AES-128-CBC encryption details, and step-by-step trace lines with privacy-safe (redacted
 * by default, opt-in Full IDs) `Copy` and `Save/Share` export to `wemi-telemetry-latest.txt`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProvisionDebugPanel(
    debugSnapshot: SetupDebugSnapshot,
    selectedSsid: String,
    keepOpenOnSuccess: Boolean,
    hasWarningOrError: Boolean = false,
    statusMessage: String? = null,
    onToggleKeepOpen: () -> Unit
) {
    val context = LocalContext.current
    var includeFullIdentifiers by remember { mutableStateOf(false) }
    var copiedFeedback by remember(debugSnapshot, selectedSsid, includeFullIdentifiers) { mutableStateOf(false) }
    var sharedFeedback by remember(debugSnapshot, selectedSsid, includeFullIdentifiers) { mutableStateOf(false) }
    val hasActiveDiagnostics = hasWarningOrError || debugSnapshot.traceLines.isNotEmpty()
    var userExpandedOverride by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hasWarningOrError, statusMessage) {
        if (hasWarningOrError) {
            userExpandedOverride = null
        }
    }
    LaunchedEffect(debugSnapshot.traceLines.isNotEmpty()) {
        if (debugSnapshot.traceLines.isNotEmpty()) {
            userExpandedOverride = null
        }
    }
    val isExpanded = userExpandedOverride ?: hasActiveDiagnostics
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "SETUP DIAGNOSTICS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0xFF8BE836)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { userExpandedOverride = !isExpanded },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("toggle_provision_debug_button")
                    ) {
                        Text(
                            text = if (isExpanded) "Hide" else "Details",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val reportText = debugSnapshot.toClipboardText(
                                selectedSsid = selectedSsid,
                                includeSensitiveIdentifiers = includeFullIdentifiers
                            )
                            TelemetryFileExporter.copyTelemetryReportToClipboard(
                                context = context,
                                reportText = reportText,
                                label = "WeMi Setup AP Diagnostic Report"
                            )
                            runCatching {
                                TelemetryFileExporter.saveTelemetryReportToFile(context, reportText)
                            }
                            copiedFeedback = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("copy_provision_debug_button")
                    ) {
                        Text(
                            text = if (copiedFeedback) "Copied" else "Copy",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val reportText = debugSnapshot.toClipboardText(
                                selectedSsid = selectedSsid,
                                includeSensitiveIdentifiers = includeFullIdentifiers
                            )
                            TelemetryFileExporter.saveAndShareTelemetryReport(
                                context = context,
                                reportText = reportText,
                                subject = "WeMi Setup AP Diagnostic Report"
                            )
                            sharedFeedback = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("share_provision_debug_button")
                    ) {
                        Text(
                            text = if (sharedFeedback) "Saved" else "Save/Share",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (isExpanded) {
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
                                line.contains("TCP_TIMEOUT", ignoreCase = true) ||
                                line.contains("NO_ROUTE", ignoreCase = true) ||
                                line.contains("REFUSED", ignoreCase = true) ||
                                line.contains("unreachable", ignoreCase = true) ||
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
                                "Keep Dialog Open on Success: ON"
                            } else {
                                "Keep Dialog Open on Success: OFF (stays open on errors)"
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

                FilterChip(
                    selected = includeFullIdentifiers,
                    onClick = { includeFullIdentifiers = !includeFullIdentifiers },
                    label = {
                        Text(
                            text = if (includeFullIdentifiers) {
                                "Full IDs in Export: ON (unredacted SSID/MAC/SN)"
                            } else {
                                "Full IDs in Export: OFF (redacts SSID/MAC/SN)"
                            },
                            fontSize = 10.5.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFFCBD5E1),
                        selectedContainerColor = Color(0xFF1E3A5F),
                        selectedLabelColor = Color(0xFFBAE6FD)
                    ),
                    modifier = Modifier.testTag("provision_include_full_ids_chip")
                )
            }
        }
    }
}
