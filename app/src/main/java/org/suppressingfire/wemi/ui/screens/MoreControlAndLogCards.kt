package org.suppressingfire.wemi.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.TelemetryFileExporter
import org.suppressingfire.wemi.ui.theme.WemoErrorRed
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

internal val MoreCodeSurfaceGray = Color(0xFFF3F6F8)

@Composable
internal fun MasterSwitchControlCard(
    devices: List<DeviceEntity>,
    onTurnAllOn: () -> Unit,
    onTurnAllOff: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WemoRowSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = WemoGreenPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Master Switch Control",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = WemoTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${devices.count { it.isReachable && it.isOn }} of ${devices.count { it.isReachable }} detected devices are currently ON.",
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                color = WemoTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTurnAllOn,
                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("all_switches_on_button")
                ) {
                    Text("All Switches ON")
                }
                OutlinedButton(
                    onClick = onTurnAllOff,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("all_switches_off_button")
                ) {
                    Text("All Switches OFF", color = WemoTextPrimary)
                }
            }
        }
    }
}

@Composable
internal fun LocalUpnpEngineCard(
    isDemoShowroomMode: Boolean,
    useWeMiWordmark: Boolean,
    onToggleDemoShowroomMode: (Boolean) -> Unit,
    onToggleBrandWordmark: () -> Unit,
    onScanNetwork: () -> Unit,
    onResetClassicDevices: () -> Unit,
    onResetLanHomesAndSwitches: () -> Unit,
    onOpenProvisionApWizard: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WemoRowSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = WemoGreenPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline Local UPnP / SOAP Engine",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = WemoTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Communicates directly with Belkin's discontinued smart switch platform over local Wi-Fi via SSDP (UDP 239.255.255.250:1900), UPnP GENA Push (/upnp/event/basicevent1), and SOAP HTTP (/upnp/control/basicevent1 on ports 49151–49159) — no cloud account required.",
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = WemoTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Demo Showroom Mode",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = WemoTextPrimary
                    )
                    Text(
                        text = if (isDemoShowroomMode) {
                            "Showing 14 showcase devices across 3 Wi-Fi Homes"
                        } else {
                            "Real LAN Mode • Controlling physical local switches only"
                        },
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = WemoTextSecondary
                    )
                }
                Switch(
                    checked = isDemoShowroomMode,
                    onCheckedChange = { onToggleDemoShowroomMode(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary),
                    modifier = Modifier.testTag("demo_showroom_mode_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Header Logo Wordmark",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = WemoTextPrimary
                    )
                    Text(
                        text = "Showing 'wemi' wordmark in top bar",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = WemoTextSecondary
                    )
                }
                Switch(
                    checked = useWeMiWordmark,
                    onCheckedChange = { onToggleBrandWordmark() },
                    colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onScanNetwork,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scan LAN (SSDP)", color = WemoGreenDark)
                }
                OutlinedButton(
                    onClick = onResetClassicDevices,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("restore_screenshot_preset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WemoTextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Preset", color = WemoTextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onResetLanHomesAndSwitches,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("more_reset_lan_homes_switches_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = WemoErrorRed
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset Homes, Discovered Switches & Rooms", color = WemoErrorRed)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenProvisionApWizard,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("more_provision_wemo_ap_button")
            ) {
                Text("Provision New Switch AP (10.22.22.1)", color = WemoGreenDark)
            }
        }
    }
}

@Composable
internal fun PlayStoreAssetsCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = WemoRowSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = WemoGreenPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google Play Store Assets Ready",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = WemoTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                painter = painterResource(id = R.drawable.img_wemi_teal_feature_1794086803999),
                contentDescription = "Google Play Feature Graphic Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.img_wemi_teal_icon_1794086800772),
                    contentDescription = "Google Play 512x512 High-Res Icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WeMi Switch Control",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = WemoTextPrimary
                    )
                    Text(
                        text = "512×512 Icon, 1024×500 Feature Graphic, Store Listing & Privacy Policy bundled in /play_store_assets/",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = WemoTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
internal fun SoapPacketLogCard(
    devices: List<DeviceEntity>,
    soapLogs: List<SoapLogEntry>,
    isDemoShowroomMode: Boolean,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    var exportedFeedback by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = WemoRowSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPnP / SOAP Packet Log (${soapLogs.size})",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = WemoTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val reportText = TelemetryFileExporter.buildFullTelemetryReport(
                                setupSnapshot = SetupDebugSnapshot(
                                    timestamp = "MoreTabExport",
                                    isDemoMode = isDemoShowroomMode
                                ),
                                devices = devices,
                                soapLogs = soapLogs
                            )
                            TelemetryFileExporter.copyAndShareTelemetryReport(
                                context = context,
                                reportText = reportText
                            )
                            exportedFeedback = true
                        },
                        modifier = Modifier.testTag("export_telemetry_file_button")
                    ) {
                        Text(
                            text = if (exportedFeedback) "Saved File" else "Save/Share Log",
                            color = WemoGreenDark,
                            fontSize = 12.sp
                        )
                    }
                    if (soapLogs.isNotEmpty()) {
                        TextButton(onClick = onClearLogs) {
                            Text("Clear", color = WemoErrorRed, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (soapLogs.isEmpty()) {
                Text(
                    text = "Toggle any light switch or tap Refresh to inspect live Belkin UPnP SOAP envelopes.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = WemoTextSecondary
                )
            } else {
                soapLogs.take(10).forEach { entry ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MoreCodeSurfaceGray,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${entry.deviceName} • ${entry.soapAction}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = WemoTextPrimary
                                )
                                Text(
                                    text = entry.timestamp,
                                    fontSize = 11.sp,
                                    color = WemoTextSecondary
                                )
                            }
                            Text(
                                text = entry.endpoint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = WemoGreenDark
                            )
                            Text(
                                text = entry.statusText,
                                fontSize = 11.sp,
                                color = WemoTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
