package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DEFAULT_CABIN_HOME_KEY
import com.example.data.DEFAULT_OFFICE_HOME_KEY
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoHomeNetworkEntity
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
 * Displays the remembered Wi-Fi Home Network selector bar at the top of the switch list.
 * Each Wi-Fi network represents a distinct Home (e.g., Main Home, Lake Cabin, Studio Office).
 * Selecting a Wi-Fi Home loads its remembered switches and triggers a Welcome-Back
 * SOAP + SSDP reconnect/rediscovery pass.
 */
@Composable
fun WeMoWifiHomeBar(
    homeNetworks: List<WeMoHomeNetworkEntity>,
    activeNetworkKey: String,
    allDevices: List<WeMoDeviceEntity>,
    isReconnectingHome: Boolean,
    onSelectHomeNetwork: (String) -> Unit,
    onReconnectCurrentHome: () -> Unit,
    onOpenAddHomeDialog: () -> Unit,
    onResetHomesAndSwitches: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeHome = homeNetworks.find { it.networkKey == activeNetworkKey }
        ?: homeNetworks.firstOrNull()
    val homeDevices = remember(allDevices, activeNetworkKey) {
        allDevices.filter { it.networkKey == activeNetworkKey }
    }
    val onlineCount = homeDevices.count { it.isReachable }
    val offlineCount = homeDevices.size - onlineCount
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WemoGreenPrimary.copy(alpha = 0.35f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .testTag("wifi_home_selector_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            // Top Row: Compact Active Wi-Fi Home summary + "Reconnect" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = WemoGreenSoft,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = WemoGreenDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        AutoResizingDeviceNameText(
                            text = activeHome?.homeName ?: "My Home",
                            color = WemoTextPrimary,
                            initialFontSizeSp = 15f,
                            minFontSizeSp = 11.5f,
                            singleLineOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = buildString {
                                append("$onlineCount/${homeDevices.size} Active")
                                if (offlineCount > 0) {
                                    append(" • $offlineCount Offline")
                                }
                                if (activeHome != null) {
                                    append(" • ${activeHome.subnetPrefix}.0/24")
                                }
                            },
                            color = WemoTextSecondary,
                            fontFamily = WorkSansFontFamily,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // One-Tap Reconnect / Rediscover Remembered Devices on this Wi-Fi Network
                Button(
                    onClick = onReconnectCurrentHome,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (offlineCount > 0) WemoGreenPrimary else WemoGreenDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("reconnect_home_wifi_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Reconnect and rediscover switches on this Wi-Fi home",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isReconnectingHome) "Syncing..." else "Reconnect",
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom Row: Remembered Wi-Fi Network Home Chips + Add Home + Reset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                homeNetworks.forEach { network ->
                    val isSelected = network.networkKey == activeNetworkKey
                    val rememberedCount = allDevices.count { it.networkKey == network.networkKey }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectHomeNetwork(network.networkKey) },
                        leadingIcon = {
                            Icon(
                                imageVector = iconForHomeNetwork(network.networkKey),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "${network.homeName} ($rememberedCount)",
                                fontFamily = WorkSansFontFamily,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenPrimary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("home_chip_${network.networkKey}")
                    )
                }

                FilterChip(
                    selected = false,
                    onClick = onOpenAddHomeDialog,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AddHome,
                            contentDescription = "Add Wi-Fi Home Network",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Add Home",
                            fontFamily = WorkSansFontFamily,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("add_wifi_home_button")
                )

                FilterChip(
                    selected = false,
                    onClick = { showResetConfirmDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Reset Homes, Switches, and Rooms",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Reset",
                            fontFamily = WorkSansFontFamily,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("reset_lan_data_chip")
                )
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Reset Homes, Switches & Rooms?",
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This clears all remembered Wi-Fi Homes, discovered switches, and room assignments, then resets your primary LAN profile back to a clean 'My Home'.",
                    color = WemoTextSecondary,
                    fontFamily = WorkSansFontFamily,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetHomesAndSwitches(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                    modifier = Modifier.testTag("confirm_reset_and_rescan_button")
                ) {
                    Text("Reset & Rescan LAN")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            showResetConfirmDialog = false
                            onResetHomesAndSwitches(false)
                        },
                        modifier = Modifier.testTag("confirm_reset_empty_button")
                    ) {
                        Text("Reset Empty", color = WemoGreenDark)
                    }
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun AddWifiHomeNetworkDialog(
    detectedLocalIp: String,
    onDismiss: () -> Unit,
    onSaveHome: (homeName: String, ssidLabel: String, subnetPrefix: String) -> Unit
) {
    val defaultSubnet = detectedLocalIp.substringBeforeLast('.', "192.168.1")
    var homeName by remember { mutableStateOf("") }
    var ssidLabel by remember { mutableStateOf("") }
    var subnetPrefix by remember { mutableStateOf(defaultSubnet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Remember Wi-Fi Home Network",
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Each Wi-Fi network is saved as a separate Home. When you connect to this Wi-Fi network, WeMi Switch Control automatically recalls and reconnects all switches discovered here.",
                    color = WemoTextSecondary,
                    fontFamily = WorkSansFontFamily,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = homeName,
                    onValueChange = { homeName = it },
                    label = { Text("Home Name (e.g., Beach House, Parents' Home)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wifi_home_name_input")
                )
                OutlinedTextField(
                    value = ssidLabel,
                    onValueChange = { ssidLabel = it },
                    label = { Text("Wi-Fi Network Name / SSID (e.g., Beach-WiFi-5G)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wifi_home_ssid_input")
                )
                OutlinedTextField(
                    value = subnetPrefix,
                    onValueChange = { subnetPrefix = it },
                    label = { Text("Local IPv4 Subnet Prefix (e.g., 192.168.1)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wifi_home_subnet_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveHome(homeName, ssidLabel, subnetPrefix)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                modifier = Modifier.testTag("save_wifi_home_button")
            ) {
                Text("Save Home Wi-Fi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun iconForHomeNetwork(networkKey: String): ImageVector {
    return when (networkKey) {
        DEFAULT_CABIN_HOME_KEY -> Icons.Default.Cottage
        DEFAULT_OFFICE_HOME_KEY -> Icons.Default.Work
        else -> Icons.Default.Home
    }
}
