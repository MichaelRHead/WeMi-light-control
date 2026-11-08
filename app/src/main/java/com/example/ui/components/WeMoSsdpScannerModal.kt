package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SsdpDiscoveredDevice
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.network.SsdpScanReport
import com.example.ui.theme.WemoDividerGray
import com.example.ui.theme.WemoGreenBottom
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft
import com.example.ui.theme.WemoRowSurface
import com.example.ui.theme.WemoTextPrimary
import com.example.ui.theme.WemoTextSecondary
import com.example.ui.theme.WorkSansFontFamily

/**
 * Compact SSDP Discovery Status & Action Bar shown at the top of the Devices list.
 */
@Composable
fun WeMoSsdpDiscoveryBanner(
    isScanning: Boolean,
    latestReport: SsdpScanReport?,
    savedDeviceCount: Int,
    onOpenScannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, WemoGreenPrimary.copy(alpha = 0.35f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("ssdp_discovery_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WemoGreenSoft,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                strokeWidth = 2.5.dp,
                                color = WemoGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = WemoGreenDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SSDP Local Network Discovery",
                        color = WemoTextPrimary,
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (latestReport != null) {
                            "Found ${latestReport.discoveredDevices.size} UPnP nodes on 239.255.255.250:1900 (${latestReport.scanDurationMs}ms)"
                        } else {
                            "UDP 239.255.255.250:1900 • $savedDeviceCount WeMi/WeMo devices configured"
                        },
                        color = WemoTextSecondary,
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onOpenScannerClick,
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("open_ssdp_scanner_button")
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SSDP Scan",
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Interactive Bottom Sheet displaying the live SSDP M-SEARCH multicast scanner,
 * search target selector, and discovered WeMi/WeMo devices list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeMoSsdpScannerModal(
    isScanning: Boolean,
    selectedSearchTarget: String,
    scanReport: SsdpScanReport?,
    existingDevices: List<WeMoDeviceEntity>,
    onSelectSearchTarget: (String) -> Unit,
    onRescan: () -> Unit,
    onAdoptDevice: (SsdpDiscoveredDevice) -> Unit,
    onAdoptAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val discoveredList = scanReport?.discoveredDevices.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WemoRowSurface,
        modifier = Modifier.testTag("ssdp_scanner_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = WemoGreenPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SSDP Network Discovery Scan",
                            color = WemoTextPrimary,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "Multicast M-SEARCH • 239.255.255.250:1900",
                            color = WemoTextSecondary,
                            fontFamily = WorkSansFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_ssdp_scanner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close SSDP Scanner",
                        tint = WemoTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Target (ST) filter chips
            Text(
                text = "SSDP Search Target (ST Header):",
                color = WemoTextSecondary,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val targets = listOf(
                    "urn:Belkin:service:basicevent:1" to "Belkin BasicEvent",
                    "urn:Belkin:device:**" to "All WeMi/WeMo Devices",
                    "upnp:rootdevice" to "UPnP Root Devices"
                )
                targets.forEach { (stValue, label) ->
                    FilterChip(
                        selected = selectedSearchTarget == stValue,
                        onClick = { onSelectSearchTarget(stValue) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // M-SEARCH Request & Interface summary box
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Local Interface: ${scanReport?.localInterfaceIp ?: "192.168.1.50"}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = WemoGreenBottom
                        )
                        Text(
                            text = if (isScanning) "Broadcasting M-SEARCH..." else "Scan Time: ${scanReport?.scanDurationMs ?: 18}ms",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = WemoGreenDark
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "M-SEARCH * HTTP/1.1 | HOST: 239.255.255.250:1900 | ST: $selectedSearchTarget",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = WemoTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row: Rescan & Sync All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovered Devices (${discoveredList.size})",
                    color = WemoTextPrimary,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRescan,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("trigger_ssdp_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = WemoGreenDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rescan", color = WemoGreenDark, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onAdoptAll,
                        colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sync_all_ssdp_button")
                    ) {
                        Text("Adopt All", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)

            // Discovered devices list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 380.dp)
                    .testTag("ssdp_discovered_list"),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveredList, key = { "${it.ipAddress}:${it.port}_${it.serialNumber}_${it.name}" }) { item ->
                    val alreadySaved = existingDevices.any {
                        ((item.serialNumber.isNotBlank() && it.serialNumber.equals(item.serialNumber, ignoreCase = true)) ||
                            it.ipAddress == item.ipAddress ||
                            it.name.equals(item.name, ignoreCase = true)) && it.isReachable
                    }
                    SsdpDiscoveredDeviceRow(
                        item = item,
                        isAlreadySynced = alreadySaved,
                        onAdopt = { onAdoptDevice(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SsdpDiscoveredDeviceRow(
    item: SsdpDiscoveredDevice,
    isAlreadySynced: Boolean,
    onAdopt: () -> Unit
) {
    Surface(
        color = if (isAlreadySynced) Color(0xFFF8FAFC) else WemoGreenSoft.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isAlreadySynced) WemoDividerGray else WemoGreenPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ssdp_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(52.dp),
                contentAlignment = Alignment.Center
            ) {
                when (item.deviceType) {
                    WeMoDeviceType.SWITCH_PLUG -> WeMoSmartPlugIllustration(
                        isReachable = true,
                        isOn = item.isOn,
                        modifier = Modifier.size(width = 44.dp, height = 56.dp)
                    )
                    WeMoDeviceType.MOTION_SENSOR -> WeMoMotionSensorIllustration(
                        isReachable = true,
                        modifier = Modifier.size(width = 44.dp, height = 56.dp)
                    )
                    WeMoDeviceType.LIGHTBULB_DIMMER -> WeMoLightbulbIllustration(
                        isReachable = true,
                        isOn = item.isOn,
                        brightness = item.brightness,
                        modifier = Modifier.size(width = 44.dp, height = 56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        color = WemoTextPrimary,
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = WemoGreenSoft,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${item.responseTimeMs}ms",
                            color = WemoGreenDark,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (item.deviceType != WeMoDeviceType.MOTION_SENSOR) {
                        WeMoPowerStatePill(
                            isOn = item.isOn,
                            label = if (item.isOn) "ON" else "OFF"
                        )
                    }
                }

                Text(
                    text = "${item.ipAddress}:${item.port} • ${item.hardwareSku} • SN: ${item.serialNumber}",
                    color = WemoGreenDark,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
                Text(
                    text = "LOCATION: ${item.locationUrl}",
                    color = WemoTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "USN: ${item.usn}",
                    color = WemoTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isAlreadySynced) {
                OutlinedButton(
                    onClick = onAdopt,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("adopt_ssdp_device_${item.ipAddress}")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = WemoGreenDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adopted", color = WemoGreenDark, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onAdopt,
                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("adopt_ssdp_device_${item.ipAddress}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adopt", fontSize = 12.sp)
                }
            }
        }
    }
}
