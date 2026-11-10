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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import org.suppressingfire.wemi.ui.BottomTab
import org.suppressingfire.wemi.ui.SwitchViewModel
import org.suppressingfire.wemi.ui.components.AddOrEditDeviceDialog
import org.suppressingfire.wemi.ui.components.AddRuleDialog
import org.suppressingfire.wemi.ui.components.AddWifiHomeNetworkDialog
import org.suppressingfire.wemi.ui.components.ProvisionApDialog
import org.suppressingfire.wemi.ui.components.BottomNavigationBar
import org.suppressingfire.wemi.ui.components.SsdpScannerModal
import org.suppressingfire.wemi.ui.components.TopHeader
import org.suppressingfire.wemi.ui.components.TvDashboardScreen
import org.suppressingfire.wemi.ui.screens.DevicesTabScreen
import org.suppressingfire.wemi.ui.screens.MoreScreen
import org.suppressingfire.wemi.ui.screens.RulesScreen
import org.suppressingfire.wemi.ui.theme.WeMiTheme
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WorkSansFontFamily

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
    viewModel: SwitchViewModel = viewModel()
) {
    val configuration = LocalConfiguration.current
    val isTvMode = remember(configuration) {
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val homeNetworks by viewModel.homeNetworks.collectAsStateWithLifecycle()
    val activeNetworkKey by viewModel.activeNetworkKey.collectAsStateWithLifecycle()
    val isReconnectingHome by viewModel.isReconnectingHome.collectAsStateWithLifecycle()
    val showAddHomeDialog by viewModel.showAddHomeDialog.collectAsStateWithLifecycle()
    val showProvisionApDialog by viewModel.showProvisionApDialog.collectAsStateWithLifecycle()
    val scannedSetupAps by viewModel.scannedSetupAps.collectAsStateWithLifecycle()
    val detectedSetupDevice by viewModel.detectedSetupDevice.collectAsStateWithLifecycle()
    val setupApStatusMessage by viewModel.setupApStatusMessage.collectAsStateWithLifecycle()
    val setupDebugSnapshot by viewModel.setupDebugSnapshot.collectAsStateWithLifecycle()
    val isScanningSetupAp by viewModel.isScanningSetupAp.collectAsStateWithLifecycle()
    val isProvisioningAp by viewModel.isProvisioningAp.collectAsStateWithLifecycle()
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
    val selectedSearchTarget by viewModel.selectedSearchTarget.collectAsStateWithLifecycle()
    val expandedDeviceIds by viewModel.expandedDeviceIds.collectAsStateWithLifecycle()
    val togglingDeviceIds by viewModel.togglingDeviceIds.collectAsStateWithLifecycle()
    val editingDevice by viewModel.editingDevice.collectAsStateWithLifecycle()
    val showAddDeviceDialog by viewModel.showAddDeviceDialog.collectAsStateWithLifecycle()
    val showAddRuleDialog by viewModel.showAddRuleDialog.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val useWeMiWordmark by viewModel.useWeMiWordmark.collectAsStateWithLifecycle()
    val isDemoShowroomMode by viewModel.isDemoShowroomMode.collectAsStateWithLifecycle()

    val homeDevices by remember(devices, activeNetworkKey) {
        derivedStateOf {
            devices.filter { it.networkKey == activeNetworkKey }
        }
    }

    val availableRooms by remember(homeDevices) {
        derivedStateOf {
            homeDevices
                .map { it.roomName }
                .distinct()
                .sortedBy { it.equals("Unassigned", ignoreCase = true) }
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
            val rawGroups = filteredDevices.groupBy { it.roomName }
            val assigned = rawGroups.filterKeys { !it.equals("Unassigned", ignoreCase = true) }
            val unassigned = rawGroups.filterKeys { it.equals("Unassigned", ignoreCase = true) }
            assigned + unassigned
        }
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!isTvMode) {
                TopHeader(
                    isRefreshing = isRefreshing,
                    isEditMode = isEditMode,
                    useWeMiWordmark = useWeMiWordmark,
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
                    onWordmarkClick = { viewModel.toggleBrandWordmark() }
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
                    onScanSsdpClick = { viewModel.openSsdpScannerAndScan() }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    when (selectedTab) {
                        BottomTab.DEVICES -> {
                            DevicesTabScreen(
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
                                activeAutoOffCountdownsSec = activeAutoOffCountdownsSec,
                                onRefreshDevices = { viewModel.refreshDevicesFromNetwork() },
                                onOpenProvisionApDialog = { viewModel.openProvisionApDialog() },
                                onSelectHomeNetwork = { viewModel.selectHomeNetwork(it) },
                                onReconnectCurrentHome = { viewModel.reconnectCurrentHomeNetwork() },
                                onOpenAddHomeDialog = { viewModel.openAddHomeDialog() },
                                onOpenScannerAndScan = { viewModel.openSsdpScannerAndScan() },
                                onToggleGroupedByRoom = { viewModel.toggleGroupByRoom() },
                                onSelectRoomFilter = { viewModel.selectRoomFilter(it) },
                                onOpenAddDeviceDialog = { viewModel.openAddDeviceDialog() },
                                onRestoreClassicDevices = { viewModel.resetToClassicScreenshotDevices() },
                                onToggleRoomPower = { viewModel.toggleRoomPower(it) },
                                onToggleRoomCollapse = { viewModel.toggleRoomCollapse(it) },
                                onUpdateRoomBrightness = { room, level ->
                                    viewModel.updateRoomBrightness(room, level)
                                },
                                onToggleDevicePower = { viewModel.toggleDevicePower(it) },
                                onToggleExpandDevice = { viewModel.toggleExpandDevice(it) },
                                onUpdateBrightness = { dev, level ->
                                    viewModel.updateBrightness(dev, level)
                                },
                                onTriggerMotion = { viewModel.simulateOrRefreshMotion(it) },
                                onReconnectDevice = { viewModel.reconnectDevice(it) },
                                onOpenEditDeviceDialog = { viewModel.openEditDeviceDialog(it) },
                                onDeleteDevice = { viewModel.deleteDevice(it) },
                                onMoveDevice = { dev, moveUp -> viewModel.moveDevice(dev, moveUp) },
                                onPingDeviceLatency = { viewModel.pingDeviceLatency(it) },
                                onToggleNightMode = { viewModel.toggleDimmerNightMode(it) },
                                onResetHomesAndSwitches = { rescan ->
                                    viewModel.resetLanHomesSwitchesAndRooms(rescan)
                                }
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
                                useWeMiWordmark = useWeMiWordmark,
                                isDemoShowroomMode = isDemoShowroomMode,
                                onTurnAllOn = { viewModel.turnAllSwitches(true) },
                                onTurnAllOff = { viewModel.turnAllSwitches(false) },
                                onScanNetwork = { viewModel.openSsdpScannerAndScan() },
                                onResetClassicDevices = { viewModel.resetToClassicScreenshotDevices() },
                                onResetLanHomesAndSwitches = {
                                    viewModel.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = true)
                                },
                                onToggleDemoShowroomMode = { viewModel.toggleDemoShowroomMode(it) },
                                onOpenProvisionApWizard = { viewModel.openProvisionApDialog() },
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

    if (showProvisionApDialog) {
        val currentSsid = homeNetworks.find { it.networkKey == activeNetworkKey }?.ssidLabel.orEmpty()
        ProvisionApDialog(
            scannedAps = scannedSetupAps,
            isScanning = isScanningSetupAp,
            isProvisioning = isProvisioningAp,
            defaultSsid = currentSsid,
            detectedDevice = detectedSetupDevice,
            statusMessage = setupApStatusMessage,
            debugSnapshot = setupDebugSnapshot,
            onScanApList = { apHost -> viewModel.scanSetupApNetworks(apHost) },
            onProvision = { apHost, ssid, pwd, ch, auth, enc, method, customName, roomName ->
                viewModel.provisionSetupAp(
                    apHost = apHost,
                    ssid = ssid,
                    password = pwd,
                    channel = ch,
                    authMode = auth,
                    encryptionType = enc,
                    encryptionMethod = method,
                    customDeviceName = customName,
                    roomName = roomName
                )
            },
            onProvisionAdvanced = { apHost, ssid, pwd, ch, auth, enc, method, addLengths, customName, roomName, keepOpen ->
                viewModel.provisionSetupAp(
                    apHost = apHost,
                    ssid = ssid,
                    password = pwd,
                    channel = ch,
                    authMode = auth,
                    encryptionType = enc,
                    encryptionMethod = method,
                    addPasswordLengths = addLengths,
                    customDeviceName = customName,
                    roomName = roomName,
                    keepDialogOpenOnSuccess = keepOpen
                )
            },
            onDismiss = { viewModel.closeProvisionApDialog() }
        )
    }

    if (showSsdpScannerSheet) {
        SsdpScannerModal(
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
        AddOrEditDeviceDialog(
            initialDevice = editingDevice,
            onDismiss = {
                viewModel.closeAddDeviceDialog()
                viewModel.closeEditDeviceDialog()
            },
            onSave = { id, name, roomName, type, ip, port, brightness, reachable, isOn, autoOff, syncHwName ->
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
                    autoOffMinutes = autoOff,
                    syncFriendlyNameToHardware = syncHwName
                )
            },
            onDelete = if (editingDevice != null) {
                { dev -> viewModel.deleteDevice(dev) }
            } else {
                null
            },
            onScanNetwork = { viewModel.openSsdpScannerAndScan() },
            onOpenProvisionWizard = { viewModel.openProvisionApDialog() }
        )
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
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
