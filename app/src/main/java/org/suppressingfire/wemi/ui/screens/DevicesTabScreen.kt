package org.suppressingfire.wemi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceCategory
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.network.LanNetworkSnapshot
import org.suppressingfire.wemi.network.SsdpScanReport
import org.suppressingfire.wemi.ui.components.DeviceCategoryIllustration
import org.suppressingfire.wemi.ui.components.DeviceRow
import org.suppressingfire.wemi.ui.components.RoomFilterBar
import org.suppressingfire.wemi.ui.components.RoomGroupHeaderCard
import org.suppressingfire.wemi.ui.components.SsdpDiscoveryBanner
import org.suppressingfire.wemi.ui.components.WifiHomeBar
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesTabScreen(
    allDevices: List<DeviceEntity>,
    homeDevices: List<DeviceEntity>,
    filteredDevices: List<DeviceEntity>,
    groupedDevicesByRoom: Map<String, List<DeviceEntity>>,
    availableRooms: List<String>,
    homeNetworks: List<HomeNetworkEntity>,
    activeNetworkKey: String,
    isReconnectingHome: Boolean,
    lanNetworkState: LanNetworkSnapshot,
    latestSsdpReport: SsdpScanReport?,
    isRefreshing: Boolean,
    isGroupedByRoom: Boolean,
    selectedRoomFilter: String?,
    collapsedRooms: Set<String>,
    togglingRooms: Set<String>,
    expandedDeviceIds: Set<Int>,
    togglingDeviceIds: Set<Int>,
    isEditMode: Boolean,
    activeAutoOffCountdownsSec: Map<Int, Int>,
    onRefreshDevices: () -> Unit,
    onOpenProvisionApDialog: () -> Unit,
    onSelectHomeNetwork: (String) -> Unit,
    onReconnectCurrentHome: () -> Unit,
    onOpenAddHomeDialog: () -> Unit,
    onOpenScannerAndScan: () -> Unit,
    onToggleGroupedByRoom: () -> Unit,
    onSelectRoomFilter: (String?) -> Unit,
    onOpenAddDeviceDialog: () -> Unit,
    onRestoreClassicDevices: () -> Unit,
    onToggleRoomPower: (String) -> Unit,
    onToggleRoomCollapse: (String) -> Unit,
    onUpdateRoomBrightness: (String, Int) -> Unit,
    onToggleDevicePower: (DeviceEntity) -> Unit,
    onToggleExpandDevice: (Int) -> Unit,
    onUpdateBrightness: (DeviceEntity, Int) -> Unit,
    onTriggerMotion: (DeviceEntity) -> Unit,
    onReconnectDevice: (DeviceEntity) -> Unit,
    onOpenEditDeviceDialog: (DeviceEntity) -> Unit,
    onDeleteDevice: (DeviceEntity) -> Unit,
    onMoveDevice: (DeviceEntity, Boolean) -> Unit,
    onPingDeviceLatency: (DeviceEntity) -> Unit,
    onToggleNightMode: (DeviceEntity) -> Unit,
    onResetHomesAndSwitches: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshDevices,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                containerColor = WemoRowSurface,
                color = WemoGreenPrimary,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("devices_pull_to_refresh")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(WemoBackgroundGray)
                .testTag("devices_list"),
            contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp)
        ) {
            if (lanNetworkState.isWemoSetupApSubnet) {
                item(key = "wemo_setup_ap_detected_banner") {
                    Surface(
                        color = WemoGreenDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clickable(onClick = onOpenProvisionApDialog)
                            .testTag("wemo_setup_ap_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = "Smart Switch Setup AP Detected (${lanNetworkState.localIpv4})",
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Tap here to provision this factory-reset smart switch (10.22.22.1) onto your 2.4 GHz Home Wi-Fi.",
                                color = Color.White.copy(alpha = 0.9f),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item(key = "wifi_home_selector_header") {
                WifiHomeBar(
                    homeNetworks = homeNetworks,
                    activeNetworkKey = activeNetworkKey,
                    allDevices = allDevices,
                    isReconnectingHome = isReconnectingHome,
                    onSelectHomeNetwork = onSelectHomeNetwork,
                    onReconnectCurrentHome = onReconnectCurrentHome,
                    onOpenAddHomeDialog = onOpenAddHomeDialog,
                    onResetHomesAndSwitches = onResetHomesAndSwitches
                )
            }

            item(key = "ssdp_discovery_banner_header") {
                SsdpDiscoveryBanner(
                    isScanning = isRefreshing,
                    latestReport = latestSsdpReport,
                    savedDeviceCount = homeDevices.count { it.isReachable },
                    onOpenScannerClick = onOpenScannerAndScan
                )
            }

            item(key = "room_grouping_filter_bar") {
                RoomFilterBar(
                    rooms = availableRooms,
                    isGroupedByRoom = isGroupedByRoom,
                    selectedRoomFilter = selectedRoomFilter,
                    onToggleGroupedByRoom = onToggleGroupedByRoom,
                    onSelectRoomFilter = onSelectRoomFilter
                )
            }

            if (homeDevices.isEmpty()) {
                item(key = "empty_home_devices_state") {
                    EmptyDevicesState(
                        onAddClick = onOpenAddDeviceDialog,
                        onScanSsdpClick = onOpenScannerAndScan,
                        onOpenProvisionApClick = onOpenProvisionApDialog,
                        onRestoreClassicClick = onRestoreClassicDevices
                    )
                }
            } else if (isGroupedByRoom) {
                groupedDevicesByRoom.forEach { (roomName, roomDevices) ->
                    val isCollapsed = roomName in collapsedRooms
                    item(key = "room_header_item_$roomName") {
                        RoomGroupHeaderCard(
                            roomName = roomName,
                            roomDevices = roomDevices,
                            isCollapsed = isCollapsed,
                            isRoomToggling = roomName in togglingRooms,
                            onToggleRoomPower = { onToggleRoomPower(roomName) },
                            onToggleCollapse = { onToggleRoomCollapse(roomName) },
                            onRoomBrightnessChange = { brightness ->
                                onUpdateRoomBrightness(roomName, brightness)
                            }
                        )
                    }

                    if (!isCollapsed) {
                        items(
                            items = roomDevices,
                            key = { "grouped_dev_${it.id}" }
                        ) { device ->
                            DeviceRow(
                                device = device,
                                isExpanded = device.id in expandedDeviceIds,
                                isEditMode = isEditMode,
                                isToggling = device.id in togglingDeviceIds,
                                onTogglePower = { onToggleDevicePower(device) },
                                onToggleExpand = { onToggleExpandDevice(device.id) },
                                onBrightnessChange = { level ->
                                    onUpdateBrightness(device, level)
                                },
                                onTriggerMotion = { onTriggerMotion(device) },
                                onReconnect = { onReconnectDevice(device) },
                                onEditDevice = { onOpenEditDeviceDialog(device) },
                                onDeleteDevice = { onDeleteDevice(device) },
                                onMoveUp = { onMoveDevice(device, true) },
                                onMoveDown = { onMoveDevice(device, false) },
                                onPingLatency = { onPingDeviceLatency(device) },
                                onToggleNightMode = { onToggleNightMode(device) },
                                autoOffRemainingSeconds = activeAutoOffCountdownsSec[device.id],
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredDevices,
                    key = { "flat_dev_${it.id}" }
                ) { device ->
                    DeviceRow(
                        device = device,
                        isExpanded = device.id in expandedDeviceIds,
                        isEditMode = isEditMode,
                        isToggling = device.id in togglingDeviceIds,
                        onTogglePower = { onToggleDevicePower(device) },
                        onToggleExpand = { onToggleExpandDevice(device.id) },
                        onBrightnessChange = { level ->
                            onUpdateBrightness(device, level)
                        },
                        onTriggerMotion = { onTriggerMotion(device) },
                        onReconnect = { onReconnectDevice(device) },
                        onEditDevice = { onOpenEditDeviceDialog(device) },
                        onDeleteDevice = { onDeleteDevice(device) },
                        onMoveUp = { onMoveDevice(device, true) },
                        onMoveDown = { onMoveDevice(device, false) },
                        onPingLatency = { onPingDeviceLatency(device) },
                        onToggleNightMode = { onToggleNightMode(device) },
                        autoOffRemainingSeconds = activeAutoOffCountdownsSec[device.id]
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyDevicesState(
    onAddClick: () -> Unit,
    onScanSsdpClick: () -> Unit,
    onOpenProvisionApClick: () -> Unit,
    onRestoreClassicClick: () -> Unit
) {
    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WemoDividerGray),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("empty_devices_state_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = WemoGreenSoft.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceCategoryIllustration(
                        category = DeviceCategory.SWITCH_PLUG,
                        isReachable = true,
                        isOn = true,
                        modifier = Modifier.size(width = 36.dp, height = 46.dp)
                    )
                    DeviceCategoryIllustration(
                        category = DeviceCategory.LIGHT,
                        isReachable = true,
                        isOn = true,
                        brightness = 100,
                        modifier = Modifier.size(width = 36.dp, height = 46.dp)
                    )
                    DeviceCategoryIllustration(
                        category = DeviceCategory.CHRISTMAS_TREE,
                        isReachable = true,
                        isOn = true,
                        modifier = Modifier.size(width = 36.dp, height = 46.dp)
                    )
                    DeviceCategoryIllustration(
                        category = DeviceCategory.FAN,
                        isReachable = true,
                        isOn = false,
                        modifier = Modifier.size(width = 36.dp, height = 46.dp)
                    )
                }
            }

            Text(
                text = "No Smart Switches on This Wi-Fi Home",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = WemoTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scan this Wi-Fi network via SSDP (239.255.255.250:1900) to remember discovered switches here, provision a factory-reset switch (10.22.22.1), add a switch by IP, or restore the default preset.",
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = WemoTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onScanSsdpClick,
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_state_scan_ssdp_button")
            ) {
                Text("Scan Local Network (SSDP)")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onOpenProvisionApClick,
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_state_provision_ap_button")
            ) {
                Text("Setup Factory-Reset Switch AP (10.22.22.1)")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_state_add_manual_button")
                ) {
                    Text("Add Switch Manually", color = WemoGreenDark)
                }
                OutlinedButton(
                    onClick = onRestoreClassicClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_state_restore_classic_button")
                ) {
                    Text("Restore Classic Setup", color = WemoTextSecondary)
                }
            }
        }
    }
}
