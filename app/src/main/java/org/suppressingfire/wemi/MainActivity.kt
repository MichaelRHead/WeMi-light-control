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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import org.suppressingfire.wemi.ui.components.ReleaseHistoryDialog
import org.suppressingfire.wemi.ui.components.TopHeader
import org.suppressingfire.wemi.ui.components.TvDashboardScreen
import org.suppressingfire.wemi.ui.components.TvHouseholdSyncOverlay
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
    val showAddDeviceDialog by viewModel.showAddDeviceDialog.collectAsStateWithLifecycle()
    val editingDevice by viewModel.editingDevice.collectAsStateWithLifecycle()
    val showAddHomeDialog by viewModel.showAddHomeDialog.collectAsStateWithLifecycle()
    val showProvisionApDialog by viewModel.showProvisionApDialog.collectAsStateWithLifecycle()
    val hardwareRuleEditorRequest by viewModel.hardwareRuleEditorRequest.collectAsStateWithLifecycle()
    val solarLocationDialogDevice by viewModel.solarLocationDialogDevice.collectAsStateWithLifecycle()
    val backupRestoreDialogDevice by viewModel.backupRestoreDialogDevice.collectAsStateWithLifecycle()
    val pendingDeleteHardwareRule by viewModel.pendingDeleteHardwareRule.collectAsStateWithLifecycle()
    val expandedDeviceIds by viewModel.expandedDeviceIds.collectAsStateWithLifecycle()
    val togglingDeviceIds by viewModel.togglingDeviceIds.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isDemoShowroomMode by viewModel.isDemoShowroomMode.collectAsStateWithLifecycle()
    val setupDebugSnapshot by viewModel.setupDebugSnapshot.collectAsStateWithLifecycle()
    val hardwareRulesByDeviceId by viewModel.hardwareRulesByDeviceId.collectAsStateWithLifecycle()
    val inspectingRuleDeviceIds by viewModel.inspectingRuleDeviceIds.collectAsStateWithLifecycle()
    val isScanningAllDeviceRules by viewModel.isScanningAllDeviceRules.collectAsStateWithLifecycle()
    val householdSyncState by viewModel.householdSyncState.collectAsStateWithLifecycle()
    var showReleaseHistoryDialog by rememberSaveable { mutableStateOf(false) }

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

    val hasHardwareRuleDialog = hardwareRuleEditorRequest != null ||
        solarLocationDialogDevice != null ||
        backupRestoreDialogDevice != null ||
        pendingDeleteHardwareRule != null

    BackHandler(
        enabled = showReleaseHistoryDialog ||
            householdSyncState.hasAnyDialogOpen ||
            hasHardwareRuleDialog ||
            showAddDeviceDialog ||
            editingDevice != null ||
            showSsdpScannerSheet ||
            showAddHomeDialog ||
            showProvisionApDialog ||
            isEditMode ||
            selectedRoomFilter != null ||
            selectedTab != BottomTab.DEVICES
    ) {
        when {
            showReleaseHistoryDialog -> showReleaseHistoryDialog = false
            householdSyncState.hasAnyDialogOpen -> viewModel.householdSync.dismissAllDialogs()
            hasHardwareRuleDialog -> viewModel.dismissHardwareRuleDialogs()
            showAddDeviceDialog || editingDevice != null -> {
                viewModel.closeAddDeviceDialog()
                viewModel.closeEditDeviceDialog()
            }
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
                            val hasCompatibleSwitch = homeDevices.any { dev ->
                                val cap = hardwareRulesByDeviceId[dev.id]?.capability ?: dev.parsedHardwareRulesCapability
                                dev.parsedType != org.suppressingfire.wemi.data.DeviceType.MOTION_SENSOR &&
                                    dev.isReachable &&
                                    cap != org.suppressingfire.wemi.network.HardwareRulesCapability.VERIFIED_UNSUPPORTED
                            }
                            if (hasCompatibleSwitch) {
                                viewModel.openAddHardwareRuleDialog()
                            } else {
                                viewModel.showTransientStatus(
                                    "No reachable on-device rules switch in this home — discover or reconnect a switch first"
                                )
                            }
                        } else {
                            viewModel.openAddDeviceDialog()
                        }
                    },
                    activeOnCount = activeOnCount,
                    onAllOffClick = { viewModel.turnAllSwitches(false) },
                    onVersionClick = { showReleaseHistoryDialog = true },
                    addButtonContentDescription = stringResource(
                        if (selectedTab == BottomTab.RULES) R.string.action_add_rule else R.string.action_add_device
                    )
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
                    onScanSsdpClick = { viewModel.refreshDevicesFromNetwork() },
                    onOpenHouseholdSync = { viewModel.householdSync.openReceiveDialog(isTv = true) }
                )
                if (householdSyncState.showReceiveDialog || householdSyncState.pendingImport != null) {
                    TvHouseholdSyncOverlay(
                        state = householdSyncState,
                        onStartNewSession = { viewModel.householdSync.openReceiveDialog(isTv = true) },
                        onConfirmImport = { mode -> viewModel.householdSync.confirmPendingImport(mode) },
                        onDismiss = { viewModel.householdSync.closeReceiveDialog() }
                    )
                }
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
                        },
                        onInspectDeviceHardwareRules = { device ->
                            viewModel.inspectDeviceHardwareRules(device, forceRecheck = true)
                        },
                        onAddDeviceHardwareRule = { device ->
                            viewModel.openAddHardwareRuleDialog(device)
                        },
                        onSyncDeviceClock = { device ->
                            viewModel.syncDeviceClock(device)
                        },
                        onOpenRulesTab = {
                            viewModel.selectTab(BottomTab.RULES)
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
                                    hardwareRulesByDeviceId = hardwareRulesByDeviceId,
                                    inspectingRuleDeviceIds = inspectingRuleDeviceIds
                                ),
                                actions = devicesTabActions
                            )
                        }

                        BottomTab.RULES -> {
                            RulesScreen(
                                homeDevices = homeDevices,
                                hardwareRulesByDeviceId = hardwareRulesByDeviceId,
                                inspectingRuleDeviceIds = inspectingRuleDeviceIds,
                                isScanningAllDeviceRules = isScanningAllDeviceRules,
                                onScanAllHardwareRules = { viewModel.inspectAllHomeHardwareRules() },
                                onAddHardwareRule = { device -> viewModel.openAddHardwareRuleDialog(device) },
                                onInspectDeviceHardwareRules = { device, force ->
                                    viewModel.inspectDeviceHardwareRules(device, forceRecheck = force)
                                },
                                onToggleHardwareRule = { device, rule, enabled ->
                                    viewModel.toggleHardwareRule(device, rule, enabled)
                                },
                                onEditHardwareRule = { device, rule ->
                                    viewModel.openEditHardwareRuleDialog(device, rule)
                                },
                                onDeleteHardwareRule = { device, rule ->
                                    viewModel.requestDeleteHardwareRule(device, rule)
                                },
                                onSyncDeviceClock = { device -> viewModel.syncDeviceClock(device) },
                                onEditSolarLocation = { device -> viewModel.openSolarLocationDialog(device) },
                                onOpenBackups = { device -> viewModel.openBackupRestoreDialog(device) },
                                onOpenDevicesTab = { viewModel.selectTab(BottomTab.DEVICES) }
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
                                hardwareRulesByDeviceId = hardwareRulesByDeviceId,
                                onTurnAllOn = { viewModel.turnAllSwitches(true) },
                                onTurnAllOff = { viewModel.turnAllSwitches(false) },
                                onScanNetwork = { viewModel.openSsdpScannerAndScan() },
                                onResetClassicDevices = { viewModel.resetToClassicScreenshotDevices() },
                                onResetLanHomesAndSwitches = { rescan ->
                                    viewModel.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = rescan)
                                },
                                onToggleDemoShowroomMode = { viewModel.toggleDemoShowroomMode(it) },
                                onOpenProvisionApWizard = { viewModel.openProvisionApDialog() },
                                onExportHouseholdJson = { writer ->
                                    viewModel.householdSync.exportHouseholdJsonToWriter(isTv = false, writeJson = writer)
                                },
                                onImportHouseholdStream = { src, opener ->
                                    viewModel.householdSync.readAndStageJsonImportFromStream(src, opener)
                                },
                                onOpenSendLanSync = { viewModel.householdSync.openSendDialog() },
                                onOpenReceiveLanSync = { viewModel.householdSync.openReceiveDialog(isTv = false) },
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
                val isErrorBanner = SwitchViewModel.isErrorStatusMessage(statusBannerMessage)
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isErrorBanner) {
                                Color(0xFFB91C1C).copy(alpha = 0.96f)
                            } else {
                                WemoGreenDark.copy(alpha = 0.95f)
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                        .semantics { liveRegion = LiveRegionMode.Polite }
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
        if (showReleaseHistoryDialog) {
            ReleaseHistoryDialog(
                onDismiss = { showReleaseHistoryDialog = false }
            )
        }
    }
}
