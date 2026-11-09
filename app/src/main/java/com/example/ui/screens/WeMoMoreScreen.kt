package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SoapLogEntry
import com.example.data.WeMoDeviceEntity
import com.example.ui.theme.WemoBackgroundGray
import com.example.ui.theme.WemoErrorRed
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoRowSurface
import com.example.ui.theme.WemoTextPrimary
import com.example.ui.theme.WemoTextSecondary
import com.example.ui.theme.WorkSansFontFamily

@Composable
fun WeMoMoreScreen(
    devices: List<WeMoDeviceEntity>,
    soapLogs: List<SoapLogEntry>,
    useWeMiWordmark: Boolean,
    isDemoShowroomMode: Boolean = false,
    onTurnAllOn: () -> Unit,
    onTurnAllOff: () -> Unit,
    onScanNetwork: () -> Unit,
    onResetClassicDevices: () -> Unit,
    onResetLanHomesAndSwitches: () -> Unit = {},
    onToggleDemoShowroomMode: (Boolean) -> Unit = {},
    onOpenProvisionApWizard: () -> Unit = {},
    onToggleBrandWordmark: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WemoBackgroundGray)
            .testTag("more_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Master Switch Controls Card
        item {
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
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            color = WemoTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${devices.count { it.isReachable && it.isOn }} of ${devices.count { it.isReachable }} detected devices are currently ON.",
                        fontFamily = WorkSansFontFamily,
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

        // Local UPnP / SOAP Controller Settings Card
        item {
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
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            color = WemoTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Communicates directly with WeMi / Belkin WeMo switches over local Wi-Fi via SSDP (UDP 239.255.255.250:1900), UPnP GENA Push (/upnp/event/basicevent1), and SOAP HTTP (/upnp/control/basicevent1 on ports 49151–49159) — no cloud account required.",
                        fontFamily = WorkSansFontFamily,
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
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = WemoTextPrimary
                            )
                            Text(
                                text = if (isDemoShowroomMode) {
                                    "Showing 13 showcase devices across 3 Wi-Fi Homes"
                                } else {
                                    "Real LAN Mode • Controlling physical Belkin WeMo switches only"
                                },
                                fontFamily = WorkSansFontFamily,
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
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = WemoTextPrimary
                            )
                            Text(
                                text = if (useWeMiWordmark) "Showing 'wemi' in top bar" else "Showing classic 'wemo' in top bar",
                                fontFamily = WorkSansFontFamily,
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
                        Text("Provision New WeMo AP (10.22.22.1)", color = WemoGreenDark)
                    }
                }
            }
        }

        // Play Store Listing & Graphic Assets Card
        item {
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
                            fontFamily = WorkSansFontFamily,
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
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = WemoTextPrimary
                            )
                            Text(
                                text = "512×512 Icon, 1024×500 Feature Graphic, Store Listing & Privacy Policy bundled in /play_store_assets/",
                                fontFamily = WorkSansFontFamily,
                                fontSize = 12.sp,
                                color = WemoTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Live SOAP / UPnP Network Inspector & Telemetry Exporter
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
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
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = WemoTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    val reportText = com.example.network.WeMoTelemetryFileExporter.buildFullTelemetryReport(
                                        setupSnapshot = com.example.network.WeMoSetupDebugSnapshot(
                                            timestamp = "MoreTabExport",
                                            isDemoMode = isDemoShowroomMode
                                        ),
                                        devices = devices,
                                        soapLogs = soapLogs
                                    )
                                    com.example.network.WeMoTelemetryFileExporter.copyAndShareTelemetryReport(
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
                            fontFamily = WorkSansFontFamily,
                            fontSize = 13.sp,
                            color = WemoTextSecondary
                        )
                    } else {
                        soapLogs.take(10).forEach { entry ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFF3F6F8),
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
    }
}
