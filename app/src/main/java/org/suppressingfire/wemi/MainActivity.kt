package org.suppressingfire.wemi

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.suppressingfire.wemi.ui.BottomTab
import org.suppressingfire.wemi.ui.SwitchViewModel
import org.suppressingfire.wemi.ui.components.BottomNavigationBar
import org.suppressingfire.wemi.ui.components.TopHeader
import org.suppressingfire.wemi.ui.components.TvDashboardScreen
import org.suppressingfire.wemi.ui.components.WeMiModalDialogs
import org.suppressingfire.wemi.ui.screens.DevicesTabActions
import org.suppressingfire.wemi.ui.screens.DevicesTabScreen
import org.suppressingfire.wemi.ui.screens.DevicesTabUiState
import org.suppressingfire.wemi.ui.screens.MoreScreen
import org.suppressingfire.wemi.ui.screens.RulesScreen
import org.suppressingfire.wemi.ui.theme.WeMiTheme
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.widget.WidgetUpdater

class MainActivity : ComponentActivity() {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = SwitchViewModel.Factory

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
    viewModel: SwitchViewModel = viewModel()
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTvMode = remember(configuration) {
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val homeNetworks by viewModel.homeNetworks.collectAsStateWithLifecycle()
    val activeNetworkKey by viewModel.activeNetworkKey.collectAsStateWithLifecycle()
    val isReconnectingHome by viewModel.isReconnectingHome.collectAsStateWithLifecycle()
    val lanNetworkState by viewModel.lanNetworkState.collectAsStateWithLifecycle()
    val activeAutoOffCountdownsSec by viewModel.activeAutoOffCountdownsSec.collectAsStateWithLifecycle()
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
    val showAddHomeDialog by viewModel.showAddHomeDialog.collectAsStateWithLifecycle()
    val showProvisionApDialog by viewModel.showProvisionApDialog.collectAsStateWithLifecycle()
    val expandedDeviceIds by viewModel.expandedDeviceIds.collectAsStateWithLifecycle()
    val togglingDeviceIds by viewModel.togglingDeviceIds.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isDemoShowroomMode by viewModel.isDemoShowroomMode.collectAsStateWithLifecycle()
    val setupDebugSnapshot by viewModel.setupDebugSnapshot.collectAsStateWithLifecycle()

    val homeDevices = remember(devices, activeNetworkKey) {
        devices.filter { it.networkKey == activeNetworkKey }
    }

    val availableRooms = remember(homeDevices) {
        org.suppressingfire.wemi.data.groupDevicesByCanonicalRoom(homeDevices).keys.toList()
    }

    val filteredDevices = remember(homeDevices, selectedRoomFilter) {
        if (selectedRoomFilter == null) {
            homeDevices
        } else {
            homeDevices.filter {
                it.normalizedRoomName.equals(selectedRoomFilter, ignoreCase = true)
            }
        }
    }

    val groupedDevicesByRoom = remember(filteredDevices) {
        org.suppressingfire.wemi.data.groupDevicesByCanonicalRoom(filteredDevices)
    }

    BackHandler(
        enabled = showSsdpScannerSheet ||
            showAddHomeDialog ||
            showProvisionApDialog ||
            isEditMode ||
            selectedRoomFilter != null ||
            selectedTab != BottomTab.DEVICES
    ) {
        when {
            showSsdpScannerSheet -> viewModel.closeSsdpScanner()
            showAddHomeDialog -> viewModel.closeAddHomeDialog()
            showProvisionApDialog -> viewModel.closeProvisionApDialog()
            isEditMode -> viewModel.setEditMode(false)
            selectedRoomFilter != null -> viewModel.selectRoomFilter(null)
            else -> viewModel.selectTab(BottomTab.DEVICES)
        }
    }

    val activeOnCount = remember(homeDevices) {
        homeDevices.count { it.isReachable && it.isOn && it.parsedType != org.suppressingfire.wemi.data.DeviceType.MOTION_SENSOR }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!isTvMode) {
                TopHeader(
                    isRefreshing = isRefreshing,
                    isEditMode = isEditMode,
                    onRefreshClick = { viewModel.openSsdpScannerAndScan() },
                    onEditClick = {
                        if (selectedTab != BottomTab.DEVICES) {
                            viewModel.selectTab(BottomTab.DEVICES)
                        }
                        viewModel.toggleEditMode()
                    },
                    onAddClick = {
                        if (selectedTab == BottomTab.RULES) {
                            viewModel.openAddRuleDialog()
                        } else {
                            viewModel.openAddDeviceDialog()
                        }
                    },
                    activeOnCount = activeOnCount,
                    onAllOffClick = { viewModel.turnAllSwitches(false) }
                )
            }
        },
        bottomBar = {
            if (!isTvMode) {
                BottomNavigationBar(
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
                TvDashboardScreen(
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
                    onScanSsdpClick = { viewModel.refreshDevicesFromNetwork() }
                )
            } else {
                val devicesTabActions = remember(viewModel, context) {
                    DevicesTabActions(
                        onRefreshDevices = { viewModel.refreshDevicesFromNetwork() },
                        onOpenProvisionApDialog = { viewModel.openProvisionApDialog() },
                        onSelectHomeNetwork = { viewModel.selectHomeNetwork(it) },
                        onReconnectCurrentHome = { viewModel.reconnectCurrentHomeNetwork() },
                        onOpenAddHomeDialog = { viewModel.openAddHomeDialog() },
                        onOpenScannerAndScan = { viewModel.openSsdpScannerAndScan() },
                        onToggleGroupedByRoom = { viewModel.toggleGroupByRoom() },
                        onSelectRoomFilter = { viewModel.selectRoomFilter(it) },
                        onOpenAddDeviceDialog = { viewModel.openAddDeviceDialog() },
                        onToggleRoomPower = { viewModel.toggleRoomPower(it) },
                        onToggleRoomCollapse = { viewModel.toggleRoomCollapse(it) },
                        onUpdateRoomBrightness = { room, level -> viewModel.updateRoomBrightness(room, level) },
                        onToggleDevicePower = { viewModel.toggleDevicePower(it) },
                        onToggleExpandDevice = { viewModel.toggleExpandDevice(it) },
                        onUpdateBrightness = { dev, level -> viewModel.updateBrightness(dev, level) },
                        onTriggerMotion = { viewModel.simulateOrRefreshMotion(it) },
                        onReconnectDevice = { viewModel.reconnectDevice(it) },
                        onOpenEditDeviceDialog = { viewModel.openEditDeviceDialog(it) },
                        onDeleteDevice = { viewModel.deleteDevice(it) },
                        onMoveDevice = { dev, moveUp -> viewModel.moveDevice(dev, moveUp) },
                        onPingDeviceLatency = { viewModel.pingDeviceLatency(it) },
                        onToggleNightMode = { viewModel.toggleDimmerNightMode(it) },
                        onPinRoomWidget = { roomName ->
                            val msg = WidgetUpdater.requestPinRoomWidget(
                                context = context,
                                roomName = roomName,
                                networkKey = viewModel.activeNetworkKey.value,
                                allDevices = viewModel.devices.value
                            )
                            viewModel.showTransientStatus(msg)
                        },
                        onPinDeviceWidget = { device ->
                            val msg = WidgetUpdater.requestPinDeviceWidget(
                                context = context,
                                device = device,
                                allDevices = viewModel.devices.value
                            )
                            viewModel.showTransientStatus(msg)
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    when (selectedTab) {
                        BottomTab.DEVICES -> {
                            DevicesTabScreen(
                                state = DevicesTabUiState(
                                    allDevices = devices,
                                    homeDevices = homeDevices,
                                    filteredDevices = filteredDevices,
                                    groupedDevicesByRoom = groupedDevicesByRoom,
                                    availableRooms = availableRooms,
                                    homeNetworks = homeNetworks,
                                    activeNetworkKey = activeNetworkKey,
                                    isReconnectingHome = isReconnectingHome,
                                    lanNetworkState = lanNetworkState,
                                    latestSsdpReport = latestSsdpReport,
                                    isRefreshing = isRefreshing,
                                    isGroupedByRoom = isGroupedByRoom,
                                    selectedRoomFilter = selectedRoomFilter,
                                    collapsedRooms = collapsedRooms,
                                    togglingRooms = togglingRooms,
                                    expandedDeviceIds = expandedDeviceIds,
                                    togglingDeviceIds = togglingDeviceIds,
                                    isEditMode = isEditMode,
                                    isDemoShowroomMode = isDemoShowroomMode,
                                    activeAutoOffCountdownsSec = activeAutoOffCountdownsSec
                                ),
                                actions = devicesTabActions
                            )
                        }

                        BottomTab.RULES -> {
                            RulesScreen(
                                rules = rules,
                                onToggleRule = { viewModel.toggleRule(it) },
                                onRunRuleNow = { viewModel.runRuleNow(it) },
                                onDeleteRule = { viewModel.deleteRule(it) },
                                onAddRuleClick = { viewModel.openAddRuleDialog() }
                            )
                        }

                        BottomTab.MORE -> {
                            MoreScreen(
                                devices = homeDevices,
                                soapLogs = soapLogs,
                                isDemoShowroomMode = isDemoShowroomMode,
                                allDevices = devices,
                                homeNetworks = homeNetworks,
                                setupDebugSnapshot = setupDebugSnapshot,
                                lanNetworkState = lanNetworkState,
                                onTurnAllOn = { viewModel.turnAllSwitches(true) },
                                onTurnAllOff = { viewModel.turnAllSwitches(false) },
                                onScanNetwork = { viewModel.openSsdpScannerAndScan() },
                                onResetClassicDevices = { viewModel.resetToClassicScreenshotDevices() },
                                onResetLanHomesAndSwitches = { rescan ->
                                    viewModel.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = rescan)
                                },
                                onToggleDemoShowroomMode = { viewModel.toggleDemoShowroomMode(it) },
                                onOpenProvisionApWizard = { viewModel.openProvisionApDialog() },
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
                Box(
                    modifier = Modifier
                        .background(
                            color = WemoGreenDark.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .testTag("status_feedback_banner")
                ) {
                    Text(
                        text = statusBannerMessage.orEmpty(),
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    if (!isTvMode) {
        WeMiModalDialogs(
            viewModel = viewModel,
            homeDevices = homeDevices,
            homeNetworks = homeNetworks,
            activeNetworkKey = activeNetworkKey
        )
    }
}
