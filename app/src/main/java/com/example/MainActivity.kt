package com.example

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.WeMoBottomNavigationBar
import com.example.ui.components.WeMoDeviceRow
import com.example.ui.components.WeMoSsdpDiscoveryBanner
import com.example.ui.components.WeMoSsdpScannerModal
import com.example.ui.components.WeMoTopHeader
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
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val soapLogs by viewModel.soapLogs.collectAsStateWithLifecycle()
    val latestSsdpReport by viewModel.latestSsdpReport.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
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

    BackHandler(enabled = showSsdpScannerSheet || isEditMode || selectedTab != WeMoBottomTab.DEVICES) {
        when {
            showSsdpScannerSheet -> viewModel.closeSsdpScanner()
            isEditMode -> viewModel.setEditMode(false)
            else -> viewModel.selectTab(WeMoBottomTab.DEVICES)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
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
                onWordmarkClick = { viewModel.toggleBrandWordmark() }
            )
        },
        bottomBar = {
            WeMoBottomNavigationBar(
                selectedTab = selectedTab,
                onSelectTab = { viewModel.selectTab(it) }
            )
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
                            if (devices.isEmpty()) {
                                EmptyDevicesState(
                                    onAddClick = { viewModel.openAddDeviceDialog() },
                                    onScanSsdpClick = { viewModel.openSsdpScannerAndScan() },
                                    onRestoreClassicClick = { viewModel.resetToClassicScreenshotDevices() }
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(WemoBackgroundGray)
                                        .testTag("devices_list"),
                                    contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp)
                                ) {
                                    item(key = "ssdp_discovery_banner_header") {
                                        WeMoSsdpDiscoveryBanner(
                                            isScanning = isRefreshing,
                                            latestReport = latestSsdpReport,
                                            savedDeviceCount = devices.count { it.isReachable },
                                            onOpenScannerClick = { viewModel.openSsdpScannerAndScan() }
                                        )
                                    }

                                    items(devices, key = { it.id }) { device ->
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
                            devices = devices,
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

    if (showSsdpScannerSheet) {
        WeMoSsdpScannerModal(
            isScanning = isRefreshing,
            selectedSearchTarget = selectedSearchTarget,
            scanReport = latestSsdpReport,
            existingDevices = devices,
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
            onSave = { id, name, type, ip, port, brightness, reachable, isOn, autoOff ->
                viewModel.saveDevice(
                    id = id,
                    name = name,
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
            devices = devices,
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
            text = "No WeMi / WeMo Switches",
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            color = WemoTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scan your local Wi-Fi network via SSDP (239.255.255.250:1900), add a switch by IP, or restore the classic 8-device preset.",
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
