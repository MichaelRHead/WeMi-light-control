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

/**
 * Immutable UI state snapshot for the primary `Devices` tab, bundling active Wi-Fi Home
 * network selection, room-grouped device lists, and live per-row interaction state.
 */
data class DevicesTabUiState(
    val allDevices: List<DeviceEntity>,
    val homeDevices: List<DeviceEntity>,
    val filteredDevices: List<DeviceEntity>,
    val groupedDevicesByRoom: Map<String, List<DeviceEntity>>,
    val availableRooms: List<String>,
    val homeNetworks: List<HomeNetworkEntity>,
    val activeNetworkKey: String,
    val isReconnectingHome: Boolean,
    val lanNetworkState: LanNetworkSnapshot,
    val latestSsdpReport: SsdpScanReport?,
    val isRefreshing: Boolean,
    val isGroupedByRoom: Boolean,
    val selectedRoomFilter: String?,
    val collapsedRooms: Set<String>,
    val togglingRooms: Set<String>,
    val expandedDeviceIds: Set<Int>,
    val togglingDeviceIds: Set<Int>,
    val isEditMode: Boolean,
    val activeAutoOffCountdownsSec: Map<Int, Int>
)

/**
 * Cohesive callback bundle for user actions dispatched from [DevicesTabScreen].
 */
data class DevicesTabActions(
    val onRefreshDevices: () -> Unit,
    val onOpenProvisionApDialog: () -> Unit,
    val onSelectHomeNetwork: (String) -> Unit,
    val onReconnectCurrentHome: () -> Unit,
    val onOpenAddHomeDialog: () -> Unit,
    val onOpenScannerAndScan: () -> Unit,
    val onToggleGroupedByRoom: () -> Unit,
    val onSelectRoomFilter: (String?) -> Unit,
    val onOpenAddDeviceDialog: () -> Unit,
    val onRestoreClassicDevices: () -> Unit,
    val onToggleRoomPower: (String) -> Unit,
    val onToggleRoomCollapse: (String) -> Unit,
    val onUpdateRoomBrightness: (String, Int) -> Unit,
    val onToggleDevicePower: (DeviceEntity) -> Unit,
    val onToggleExpandDevice: (Int) -> Unit,
    val onUpdateBrightness: (DeviceEntity, Int) -> Unit,
    val onTriggerMotion: (DeviceEntity) -> Unit,
    val onReconnectDevice: (DeviceEntity) -> Unit,
    val onOpenEditDeviceDialog: (DeviceEntity) -> Unit,
    val onDeleteDevice: (DeviceEntity) -> Unit,
    val onMoveDevice: (DeviceEntity, Boolean) -> Unit,
    val onPingDeviceLatency: (DeviceEntity) -> Unit,
    val onToggleNightMode: (DeviceEntity) -> Unit,
    val onResetHomesAndSwitches: (Boolean) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesTabScreen(
    state: DevicesTabUiState,
    actions: DevicesTabActions,
    modifier: Modifier = Modifier
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefreshDevices,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = state.isRefreshing,
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
            if (state.lanNetworkState.isWemoSetupApSubnet) {
                item(key = "wemo_setup_ap_detected_banner") {
                    Surface(
                        color = WemoGreenDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clickable(onClick = actions.onOpenProvisionApDialog)
                            .testTag("wemo_setup_ap_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = "Smart Switch Setup AP Detected (${state.lanNetworkState.localIpv4})",
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
                    homeNetworks = state.homeNetworks,
                    activeNetworkKey = state.activeNetworkKey,
                    allDevices = state.allDevices,
                    isReconnectingHome = state.isReconnectingHome,
                    onSelectHomeNetwork = actions.onSelectHomeNetwork,
                    onReconnectCurrentHome = actions.onReconnectCurrentHome,
                    onOpenAddHomeDialog = actions.onOpenAddHomeDialog,
                    onResetHomesAndSwitches = actions.onResetHomesAndSwitches
                )
            }

            item(key = "ssdp_discovery_banner_header") {
                SsdpDiscoveryBanner(
                    isScanning = state.isRefreshing,
                    latestReport = state.latestSsdpReport,
                    savedDeviceCount = state.homeDevices.count { it.isReachable },
                    onOpenScannerClick = actions.onOpenScannerAndScan
                )
            }

            item(key = "room_grouping_filter_bar") {
                RoomFilterBar(
                    rooms = state.availableRooms,
                    isGroupedByRoom = state.isGroupedByRoom,
                    selectedRoomFilter = state.selectedRoomFilter,
                    onToggleGroupedByRoom = actions.onToggleGroupedByRoom,
                    onSelectRoomFilter = actions.onSelectRoomFilter
                )
            }

            if (state.homeDevices.isEmpty()) {
                item(key = "empty_home_devices_state") {
                    EmptyDevicesState(
                        onAddClick = actions.onOpenAddDeviceDialog,
                        onScanSsdpClick = actions.onOpenScannerAndScan,
                        onOpenProvisionApClick = actions.onOpenProvisionApDialog,
                        onRestoreClassicClick = actions.onRestoreClassicDevices
                    )
                }
            } else if (state.isGroupedByRoom) {
                state.groupedDevicesByRoom.forEach { (roomName, roomDevices) ->
                    val isCollapsed = roomName in state.collapsedRooms
                    item(key = "room_header_item_$roomName") {
                        RoomGroupHeaderCard(
                            roomName = roomName,
                            roomDevices = roomDevices,
                            isCollapsed = isCollapsed,
                            isRoomToggling = roomName in state.togglingRooms,
                            onToggleRoomPower = { actions.onToggleRoomPower(roomName) },
                            onToggleCollapse = { actions.onToggleRoomCollapse(roomName) },
                            onRoomBrightnessChange = { brightness ->
                                actions.onUpdateRoomBrightness(roomName, brightness)
                            }
                        )
                    }

                    if (!isCollapsed) {
                        items(
                            items = roomDevices,
                            key = { "grouped_dev_${it.id}" }
                        ) { device ->
                            BoundDeviceRow(
                                device = device,
                                state = state,
                                actions = actions,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.filteredDevices,
                    key = { "flat_dev_${it.id}" }
                ) { device ->
                    BoundDeviceRow(
                        device = device,
                        state = state,
                        actions = actions
                    )
                }
            }
        }
    }
}

@Composable
private fun BoundDeviceRow(
    device: DeviceEntity,
    state: DevicesTabUiState,
    actions: DevicesTabActions,
    modifier: Modifier = Modifier
) {
    DeviceRow(
        device = device,
        isExpanded = device.id in state.expandedDeviceIds,
        isEditMode = state.isEditMode,
        isToggling = device.id in state.togglingDeviceIds,
        onTogglePower = { actions.onToggleDevicePower(device) },
        onToggleExpand = { actions.onToggleExpandDevice(device.id) },
        onBrightnessChange = { level -> actions.onUpdateBrightness(device, level) },
        onTriggerMotion = { actions.onTriggerMotion(device) },
        onReconnect = { actions.onReconnectDevice(device) },
        onEditDevice = { actions.onOpenEditDeviceDialog(device) },
        onDeleteDevice = { actions.onDeleteDevice(device) },
        onMoveUp = { actions.onMoveDevice(device, true) },
        onMoveDown = { actions.onMoveDevice(device, false) },
        onPingLatency = { actions.onPingDeviceLatency(device) },
        onToggleNightMode = { actions.onToggleNightMode(device) },
        autoOffRemainingSeconds = state.activeAutoOffCountdownsSec[device.id],
        modifier = modifier
    )
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
