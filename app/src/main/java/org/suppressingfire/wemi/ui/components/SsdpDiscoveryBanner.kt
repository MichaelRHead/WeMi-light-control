package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.SsdpScanReport
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Compact SSDP Discovery Status & Action Bar shown at the top of the Devices list.
 */
@Composable
fun SsdpDiscoveryBanner(
    isScanning: Boolean,
    latestReport: SsdpScanReport?,
    savedDeviceCount: Int,
    onOpenScannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WemoGreenPrimary.copy(alpha = 0.35f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .testTag("ssdp_discovery_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = WemoGreenSoft,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = WemoGreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = WemoGreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SSDP Local Network Discovery",
                        color = WemoTextPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (latestReport != null) {
                            "Found ${latestReport.discoveredDevices.size} UPnP nodes on 239.255.255.250:1900 (${latestReport.scanDurationMs}ms)"
                        } else {
                            "UDP 239.255.255.250:1900 • $savedDeviceCount smart switches configured"
                        },
                        color = WemoTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onOpenScannerClick,
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("open_ssdp_scanner_button")
            ) {
                Icon(
                    imageVector = Icons.Default.WifiFind,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "SSDP Scan",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Shown inside [SsdpScannerModal] only when running inside ChromeOS's virtual Android network
 * (`100.115.92.x`) and automatic discovery found zero switches, letting the user enter their
 * physical router `/24` prefix (e.g. `192.168.1`) for direct unicast switch discovery.
 */
@Composable
internal fun ArcVmManualSubnetFallbackCard(
    localInterfaceIp: String,
    scannedSubnets: List<String>,
    onScanManualSubnet: (String) -> Unit
) {
    var subnetInput by remember(scannedSubnets) {
        mutableStateOf(scannedSubnets.firstOrNull().orEmpty())
    }
    val validSubnetPrefix = LanRuntimeUtils.normalizeSubnetPrefixOrNull(subnetInput)
        ?.takeIf { LanRuntimeUtils.isPrivateRfc1918Ipv4(it) }

    Surface(
        color = Color(0xFFF0F9FF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WemoGreenPrimary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("arcvm_manual_subnet_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ChromeOS Virtual Network Detected ($localInterfaceIp)",
                color = WemoGreenDark,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            )
            Text(
                text = "ChromeOS isolates Android apps on a virtual subnet. If your router blocks multicast discovery, enter your home router IPv4 prefix (such as 192.168.1 or 192.168.86) to scan for local smart switches.",
                color = WemoTextSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = subnetInput,
                    onValueChange = { subnetInput = it },
                    label = { Text("Home Subnet (e.g. 192.168.1)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ssdp_manual_subnet_input")
                )
                Button(
                    onClick = { validSubnetPrefix?.let { onScanManualSubnet(it) } },
                    enabled = validSubnetPrefix != null,
                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ssdp_scan_manual_subnet_button")
                ) {
                    Text(
                        text = "Scan Subnet",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
