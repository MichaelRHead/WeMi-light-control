package com.example

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.WeMoBottomTab
import com.example.ui.WeMoViewModel
import com.example.ui.components.AddOrEditWeMoDeviceDialog
import com.example.ui.components.AddWeMoRuleDialog
import com.example.ui.components.AddWifiHomeNetworkDialog
import com.example.ui.components.WeMoBottomNavigationBar
import com.example.ui.components.WeMoDeviceRow
import com.example.ui.components.WeMoRoomFilterBar
import com.example.ui.components.WeMoRoomGroupHeaderCard
import com.example.ui.components.WeMoSsdpDiscoveryBanner
import com.example.ui.components.WeMoSsdpScannerModal
import com.example.ui.components.WeMoTopHeader
import com.example.ui.components.WeMoTvDashboardScreen
import com.example.ui.components.WeMoWifiHomeBar
import com.example.ui.screens.WeMoMoreScreen
import com.example.ui.screens.WeMoRulesScreen
import com.example.ui.theme.WeMiTheme
import com.example.ui.theme.WemoBackgroundGray
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoRowSurface
import com.example.ui.theme.WemoTextPrimary
import com.example.ui.theme.WemoTextSecondary
import com.example.ui.theme.WorkSansFontFamily

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.WHITE,
                AndroidColor.LTGRAY
            )
        )
        setContent {
            WeMiTheme {
                WeMiSwitchApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeMiSwitchApp(
    viewModel: WeMoViewModel = viewModel()
) {
    val configuration = LocalConfiguration.current
    val isHardwareTelevision = remember(configuration) {
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    LaunchedEffect(isHardwareTelevision) {
        if (isHardwareTelevision) {
            viewModel.setTvMode(true)
        }
    }

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val homeNetworks by viewModel.homeNetworks.collectAsStateWithLifecycle()
    val activeNetworkKey by viewModel.activeNetworkKey.collectAsStateWithLifecycle()
    val isReconnectingHome by viewModel.isReconnectingHome.collectAsStateWithLifecycle()
    val showAddHomeDialog by viewModel.showAddHomeDialog.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val soapLogs by viewModel.soapLogs.collectAsStateWithLifecycle()
    val latestSsdpReport by viewModel.latestSsdpReport.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isGroupedByRoom by viewModel.isGroupedByRoom.collectAsStateWithLifecycle()
    val selectedRoomFilter by viewModel.selectedRoomFilter.collectAsStateWithLifecycle()
    val collapsedRooms by viewModel.collapsedRooms.collectAsStateWithLifecycle()
    val togglingRooms by viewModel.togglingRooms.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val showSsdpScannerSheet by viewModel.showSsdpScannerSheet.collectAsStateWithLifecycle()
    val selectedSearchTarget by viewModel.selectedSearchTarget.collectAsStateWithLifecycle()
    val expandedDeviceIds by viewModel.expandedDeviceIds.collectAsStateWithLifecycle()
    val togglingDeviceIds by viewModel.togglingDeviceIds.collectAsStateWithLifecycle()
    val editingDevice by viewModel.editingDevice.collectAsStateWithLifecycle()
    val showAddDeviceDialog by viewModel.showAddDeviceDialog.collectAsStateWithLifecycle()
    val showAddRuleDialog by viewModel.showAddRuleDialog.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val useWeMiWordmark by viewModel.useWeMiWordmark.collectAsStateWithLifecycle()
    val isTvMode by viewModel.isTvMode.collectAsStateWithLifecycle()

    val homeDevices by remember(devices, activeNetworkKey) {
        derivedStateOf {
            devices.filter { it.networkKey == activeNetworkKey }
        }
    }

    val availableRooms by remember(homeDevices) {
        derivedStateOf {
            homeDevices.map { it.roomName }.distinct()
        }
    }

    val filteredDevices by remember(homeDevices, selectedRoomFilter) {
        derivedStateOf {
            if (selectedRoomFilter == null) {
                homeDevices
            } else {
                homeDevices.filter { it.roomName.equals(selectedRoomFilter, ignoreCase = true) }
            }
        }
    }

    val groupedDevicesByRoom by remember(filteredDevices) {
        derivedStateOf {
            filteredDevices.groupBy { it.roomName }
        }
    }

    BackHandler(
        enabled = showSsdpScannerSheet ||
            showAddHomeDialog ||
            (isTvMode && !isHardwareTelevision) ||
            isEditMode ||
            selectedRoomFilter != null ||
            selectedTab != WeMoBottomTab.DEVICES
    ) {
        when {
            showSsdpScannerSheet -> viewModel.closeSsdpScanner()
            showAddHomeDialog -> viewModel.closeAddHomeDialog()
            isTvMode && !isHardwareTelevision -> viewModel.setTvMode(false)
            isEditMode -> viewModel.setEditMode(false)
            selectedRoomFilter != null -> viewModel.selectRoomFilter(null)
            else -> viewModel.selectTab(WeMoBottomTab.DEVICES)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!isTvMode) {
                WeMoTopHeader(
                    isRefreshing = isRefreshing,
                    isEditMode = isEditMode,
                    useWeMiWordmark = useWeMiWordmark,
                    onRefreshClick = { viewModel.openSsdpScannerAndScan() },
                    onEditClick = {
                        if (selectedTab != WeMoBottomTab.DEVICES) {
                            viewModel.selectTab(WeMoBottomTab.DEVICES)
                        }
                        viewModel.toggleEditMode()
                    },
                    onAddClick = {
                        if (selectedTab == WeMoBottomTab.RULES) {
                            viewModel.openAddRuleDialog()
                        } else {
                            viewModel.openAddDeviceDialog()
                        }
                    },
                    onWordmarkClick = { viewModel.toggleBrandWordmark() },
                    onOpenTvModeClick = { viewModel.setTvMode(true) }
                )
            }
        },
        bottomBar = {
            if (!isTvMode) {
                WeMoBottomNavigationBar(
                    selectedTab = selectedTab,
                    onSelectTab = { viewModel.selectTab(it) }
                )
            }
        },
        containerColor = WemoBackgroundGray
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(WemoBackgroundGray),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isTvMode) {
                WeMoTvDashboardScreen(
                    devices = homeDevices,
                    rooms = availableRooms,
                    selectedRoomFilter = selectedRoomFilter,
                    isRefreshing = isRefreshing,
                    togglingDeviceIds = togglingDeviceIds,
                    togglingRooms = togglingRooms,
                    latestSoapLog = soapLogs.firstOrNull(),
                    onSelectRoomFilter = { viewModel.selectRoomFilter(it) },
                    onToggleDevicePower = { viewModel.toggleDevicePower(it) },
                    onStepDeviceBrightness = { dev, delta -> viewModel.stepDeviceBrightness(dev, delta) },
                    onTriggerMotion = { viewModel.simulateOrRefreshMotion(it) },
                    onReconnectDevice = { viewModel.reconnectDevice(it) },
                    onToggleRoomPower = { viewModel.toggleRoomPower(it) },
                    onApplyTvScene = { viewModel.applyTvScene(it) },
                    onScanSsdpClick = { viewModel.openSsdpScannerAndScan() },
                    onExitTvMode = { viewModel.setTvMode(false) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    when (selectedTab) {
                        WeMoBottomTab.DEVICES -> {
                            val pullToRefreshState = rememberPullToRefreshState()
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.refreshDevicesFromNetwork() },
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
                                modifier = Modifier
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
                                    item(key = "wifi_home_selector_header") {
                                        WeMoWifiHomeBar(
                                            homeNetworks = homeNetworks,
                                            activeNetworkKey = activeNetworkKey,
                                            allDevices = devices,
                                            isReconnectingHome = isReconnectingHome,
                                            onSelectHomeNetwork = { viewModel.selectHomeNetwork(it) },
                                            onReconnectCurrentHome = { viewModel.reconnectCurrentHomeNetwork() },
                                            onOpenAddHomeDialog = { viewModel.openAddHomeDialog() }
                                        )
                                    }

                                    item(key = "ssdp_discovery_banner_header") {
                                        WeMoSsdpDiscoveryBanner(
                                            isScanning = isRefreshing,
                                            latestReport = latestSsdpReport,
                                            savedDeviceCount = homeDevices.count { it.isReachable },
                                            onOpenScannerClick = { viewModel.openSsdpScannerAndScan() }
                                        )
                                    }

                                    item(key = "room_grouping_filter_bar") {
                                        WeMoRoomFilterBar(
                                            rooms = availableRooms,
                                            isGroupedByRoom = isGroupedByRoom,
                                            selectedRoomFilter = selectedRoomFilter,
                                            onToggleGroupedByRoom = { viewModel.toggleGroupByRoom() },
                                            onSelectRoomFilter = { viewModel.selectRoomFilter(it) }
                                        )
                                    }

                                    if (homeDevices.isEmpty()) {
                                        item(key = "empty_home_devices_state") {
                                            EmptyDevicesState(
                                                onAddClick = { viewModel.openAddDeviceDialog() },
                                                onScanSsdpClick = { viewModel.openSsdpScannerAndScan() },
                                                onRestoreClassicClick = { viewModel.resetToClassicScreenshotDevices() }
                                            )
                                        }
                                    } else if (isGroupedByRoom) {
                                        groupedDevicesByRoom.forEach { (roomName, roomDevices) ->
                                            val isCollapsed = roomName in collapsedRooms
                                            item(key = "room_header_item_$roomName") {
                                                WeMoRoomGroupHeaderCard(
                                                    roomName = roomName,
                                                    roomDevices = roomDevices,
                                                    isCollapsed = isCollapsed,
                                                    isRoomToggling = roomName in togglingRooms,
                                                    onToggleRoomPower = {
                                                        viewModel.toggleRoomPower(roomName)
                                                    },
                                                    onToggleCollapse = {
                                                        viewModel.toggleRoomCollapse(roomName)
                                                    },
                                                    onRoomBrightnessChange = { brightness ->
                                                        viewModel.updateRoomBrightness(roomName, brightness)
                                                    }
                                                )
                                            }

                                            if (!isCollapsed) {
                                                items(
                                                    items = roomDevices,
                                                    key = { "grouped_dev_${it.id}" }
                                                ) { device ->
                                                    WeMoDeviceRow(
                                                        device = device,
                                                        isExpanded = device.id in expandedDeviceIds,
                                                        isEditMode = isEditMode,
                                                        isToggling = device.id in togglingDeviceIds,
                                                        onTogglePower = { viewModel.toggleDevicePower(device) },
                                                        onToggleExpand = { viewModel.toggleExpandDevice(device.id) },
                                                        onBrightnessChange = { level ->
                                                            viewModel.updateBrightness(device, level)
                                                        },
                                                        onTriggerMotion = {
                                                            viewModel.simulateOrRefreshMotion(device)
                                                        },
                                                        onReconnect = {
                                                            viewModel.reconnectDevice(device)
                                                        },
                                                        onEditDevice = {
                                                            viewModel.openEditDeviceDialog(device)
                                                        },
                                                        onDeleteDevice = {
                                                            viewModel.deleteDevice(device)
                                                        },
                                                        onMoveUp = {
                                                            viewModel.moveDevice(device, moveUp = true)
                                                        },
                                                        onMoveDown = {
                                                            viewModel.moveDevice(device, moveUp = false)
                                                        },
                                                        onPingLatency = {
                                                            viewModel.pingDeviceLatency(device)
                                                        },
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
                                            WeMoDeviceRow(
                                                device = device,
                                                isExpanded = device.id in expandedDeviceIds,
                                                isEditMode = isEditMode,
                                                isToggling = device.id in togglingDeviceIds,
                                                onTogglePower = { viewModel.toggleDevicePower(device) },
                                                onToggleExpand = { viewModel.toggleExpandDevice(device.id) },
                                                onBrightnessChange = { level ->
                                                    viewModel.updateBrightness(device, level)
                                                },
                                                onTriggerMotion = {
                                                    viewModel.simulateOrRefreshMotion(device)
                                                },
                                                onReconnect = {
                                                    viewModel.reconnectDevice(device)
                                                },
                                                onEditDevice = {
                                                    viewModel.openEditDeviceDialog(device)
                                                },
                                                onDeleteDevice = {
                                                    viewModel.deleteDevice(device)
                                                },
                                                onMoveUp = {
                                                    viewModel.moveDevice(device, moveUp = true)
                                                },
                                                onMoveDown = {
                                                    viewModel.moveDevice(device, moveUp = false)
                                                },
                                                onPingLatency = {
                                                    viewModel.pingDeviceLatency(device)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        WeMoBottomTab.RULES -> {
                            WeMoRulesScreen(
                                rules = rules,
                                onToggleRule = { viewModel.toggleRule(it) },
                                onRunRuleNow = { viewModel.runRuleNow(it) },
                                onDeleteRule = { viewModel.deleteRule(it) },
                                onAddRuleClick = { viewModel.openAddRuleDialog() }
                            )
                        }

                        WeMoBottomTab.MORE -> {
                            WeMoMoreScreen(
                                devices = homeDevices,
                                soapLogs = soapLogs,
                                useWeMiWordmark = useWeMiWordmark,
                                onTurnAllOn = { viewModel.turnAllSwitches(true) },
                                onTurnAllOff = { viewModel.turnAllSwitches(false) },
                                onScanNetwork = { viewModel.openSsdpScannerAndScan() },
                                onResetClassicDevices = { viewModel.resetToClassicScreenshotDevices() },
                                onToggleBrandWordmark = { viewModel.toggleBrandWordmark() },
                                onClearLogs = { viewModel.clearSoapLogs() }
                            )
                        }
                    }
                }
            }

            // Floating status toast pill at bottom of content area for instant SOAP / SSDP feedback
            AnimatedVisibility(
                visible = statusBannerMessage != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    color = WemoGreenDark.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .clickable { viewModel.dismissBanner() }
                        .testTag("status_feedback_banner")
                ) {
                    Text(
                        text = statusBannerMessage.orEmpty(),
                        color = Color.White,
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    if (showAddHomeDialog) {
        AddWifiHomeNetworkDialog(
            detectedLocalIp = viewModel.detectCurrentLanIp(),
            onDismiss = { viewModel.closeAddHomeDialog() },
            onSaveHome = { homeName, ssidLabel, subnetPrefix ->
                viewModel.addWifiHomeNetwork(homeName, ssidLabel, subnetPrefix)
            }
        )
    }

    if (showSsdpScannerSheet) {
        WeMoSsdpScannerModal(
            isScanning = isRefreshing,
            selectedSearchTarget = selectedSearchTarget,
            scanReport = latestSsdpReport,
            existingDevices = homeDevices,
            onSelectSearchTarget = { st -> viewModel.selectSearchTargetAndScan(st) },
            onRescan = { viewModel.openSsdpScannerAndScan(selectedSearchTarget) },
            onAdoptDevice = { item -> viewModel.adoptSsdpDevice(item) },
            onAdoptAll = { viewModel.adoptAllSsdpDevices() },
            onDismiss = { viewModel.closeSsdpScanner() }
        )
    }

    if (showAddDeviceDialog || editingDevice != null) {
        AddOrEditWeMoDeviceDialog(
            initialDevice = editingDevice,
            onDismiss = {
                viewModel.closeAddDeviceDialog()
                viewModel.closeEditDeviceDialog()
            },
            onSave = { id, name, roomName, type, ip, port, brightness, reachable, isOn, autoOff ->
                viewModel.saveDevice(
                    id = id,
                    name = name,
                    roomName = roomName,
                    deviceType = type,
                    ipAddress = ip,
                    port = port,
                    brightness = brightness,
                    isReachable = reachable,
                    isOn = isOn,
                    autoOffMinutes = autoOff
                )
            },
            onDelete = if (editingDevice != null) {
                { dev -> viewModel.deleteDevice(dev) }
            } else {
                null
            },
            onScanNetwork = { viewModel.openSsdpScannerAndScan() }
        )
    }

    if (showAddRuleDialog) {
        AddWeMoRuleDialog(
            devices = homeDevices,
            onDismiss = { viewModel.closeAddRuleDialog() },
            onCreateRule = { name, ruleType, targetDevice, triggerSummary, targetStateOn, brightness ->
                viewModel.addRule(
                    name = name,
                    ruleType = ruleType,
                    targetDevice = targetDevice,
                    triggerSummary = triggerSummary,
                    targetStateOn = targetStateOn,
                    targetBrightness = brightness
                )
            }
        )
    }
}

@Composable
private fun EmptyDevicesState(
    onAddClick: () -> Unit,
    onScanSsdpClick: () -> Unit,
    onRestoreClassicClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No WeMi / WeMo Switches on This Wi-Fi Home",
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            color = WemoTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scan this Wi-Fi network via SSDP (239.255.255.250:1900) to remember discovered switches here, add a switch by IP, or restore the default preset.",
            fontFamily = WorkSansFontFamily,
            fontSize = 14.sp,
            color = WemoTextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onScanSsdpClick,
            colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary)
        ) {
            Text("Scan Local Network (SSDP)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = WemoGreenDark)
        ) {
            Text("Add Switch Manually")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRestoreClassicClick,
            colors = ButtonDefaults.buttonColors(containerColor = WemoGreenDark)
        ) {
            Text("Restore Classic Setup")
        }
    }
}
