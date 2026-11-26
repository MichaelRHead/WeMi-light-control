package org.suppressingfire.wemi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.ui.SwitchViewModel

/**
 * Hosts all mobile/tablet modal dialogs and bottom sheets (`AddWifiHomeNetworkDialog`,
 * `ProvisionApDialog`, `SsdpScannerModal`, `AddOrEditDeviceDialog`, `HardwareRuleEditorDialog`,
 * `HardwareLocationDialog`, `HardwareRulesBackupDialog`, and `DeleteHardwareRuleConfirmDialog`)
 * outside `MainActivity.kt` so `WeMiSwitchApp` stays focused on top-level navigation and layout.
 */
@Composable
internal fun WeMiModalDialogs(
    viewModel: SwitchViewModel,
    homeDevices: List<DeviceEntity>,
    homeNetworks: List<HomeNetworkEntity>,
    activeNetworkKey: String
) {
    val showAddHomeDialog by viewModel.showAddHomeDialog.collectAsStateWithLifecycle()
    val showProvisionApDialog by viewModel.showProvisionApDialog.collectAsStateWithLifecycle()
    val showSsdpScannerSheet by viewModel.showSsdpScannerSheet.collectAsStateWithLifecycle()
    val showAddDeviceDialog by viewModel.showAddDeviceDialog.collectAsStateWithLifecycle()
    val editingDevice by viewModel.editingDevice.collectAsStateWithLifecycle()
    val hardwareRuleEditorRequest by viewModel.hardwareRuleEditorRequest.collectAsStateWithLifecycle()
    val solarLocationDialogDevice by viewModel.solarLocationDialogDevice.collectAsStateWithLifecycle()
    val backupRestoreDialogDevice by viewModel.backupRestoreDialogDevice.collectAsStateWithLifecycle()
    val pendingDeleteHardwareRule by viewModel.pendingDeleteHardwareRule.collectAsStateWithLifecycle()
    val hardwareRulesByDeviceId by viewModel.hardwareRulesByDeviceId.collectAsStateWithLifecycle()
    val householdSyncState by viewModel.householdSyncState.collectAsStateWithLifecycle()

    if (showAddHomeDialog) {
        val detectedLocalIp = remember { viewModel.detectCurrentLanIp() }
        AddWifiHomeNetworkDialog(
            detectedLocalIp = detectedLocalIp,
            onDismiss = { viewModel.closeAddHomeDialog() },
            onSaveHome = { homeName, ssidLabel, subnetPrefix ->
                viewModel.addWifiHomeNetwork(homeName, ssidLabel, subnetPrefix)
            }
        )
    }

    if (showProvisionApDialog) {
        val scannedSetupAps by viewModel.scannedSetupAps.collectAsStateWithLifecycle()
        val detectedSetupDevice by viewModel.detectedSetupDevice.collectAsStateWithLifecycle()
        val setupApStatusMessage by viewModel.setupApStatusMessage.collectAsStateWithLifecycle()
        val setupDebugSnapshot by viewModel.setupDebugSnapshot.collectAsStateWithLifecycle()
        val isScanningSetupAp by viewModel.isScanningSetupAp.collectAsStateWithLifecycle()
        val isProvisioningAp by viewModel.isProvisioningAp.collectAsStateWithLifecycle()
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
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val selectedSearchTarget by viewModel.selectedSearchTarget.collectAsStateWithLifecycle()
        val latestSsdpReport by viewModel.latestSsdpReport.collectAsStateWithLifecycle()

        SsdpScannerModal(
            isScanning = isRefreshing,
            selectedSearchTarget = selectedSearchTarget,
            scanReport = latestSsdpReport,
            existingDevices = homeDevices,
            onSelectSearchTarget = { st -> viewModel.selectSearchTargetAndScan(st) },
            onRescan = { viewModel.openSsdpScannerAndScan(selectedSearchTarget) },
            onAdoptDevice = { item -> viewModel.adoptSsdpDevice(item) },
            onAdoptAll = { viewModel.adoptAllSsdpDevices() },
            onDismiss = { viewModel.closeSsdpScanner() },
            onScanManualSubnet = { subnet -> viewModel.scanManualSubnet(subnet) }
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

    hardwareRuleEditorRequest?.let { req ->
        HardwareRuleEditorDialog(
            homeDevices = homeDevices,
            snapshotsByDeviceId = hardwareRulesByDeviceId,
            preselectedDeviceId = req.preselectedDeviceId,
            editingRule = req.editingRule,
            onDismiss = { viewModel.closeHardwareRuleEditorDialog() },
            onSave = { targetDevice, draft -> viewModel.saveHardwareRule(targetDevice, draft) }
        )
    }

    solarLocationDialogDevice?.let { device ->
        HardwareLocationDialog(
            device = device,
            initialLocation = hardwareRulesByDeviceId[device.id]?.locationInfo,
            onDismiss = { viewModel.closeSolarLocationDialog() },
            onSaveLocation = { loc -> viewModel.saveSolarLocation(device, loc) }
        )
    }

    backupRestoreDialogDevice?.let { device ->
        HardwareRulesBackupDialog(
            device = device,
            backups = hardwareRulesByDeviceId[device.id]?.availableBackups.orEmpty(),
            onDismiss = { viewModel.closeBackupRestoreDialog() },
            onRestoreBackup = { entry -> viewModel.restoreHardwareRulesBackup(device, entry.filePath) }
        )
    }

    pendingDeleteHardwareRule?.let { (device, rule) ->
        DeleteHardwareRuleConfirmDialog(
            device = device,
            rule = rule,
            onDismiss = { viewModel.cancelDeleteHardwareRule() },
            onConfirmDelete = { viewModel.confirmDeleteHardwareRule() }
        )
    }

    if (householdSyncState.showSendDialog) {
        HouseholdSyncSendDialog(
            state = householdSyncState,
            onRescanPeers = { viewModel.householdSync.discoverLanPeers() },
            onPushToPeer = { ip, port, pin ->
                viewModel.householdSync.pushToLanPeer(ip, port, pin, isTvSender = false)
            },
            onDismiss = { viewModel.householdSync.closeSendDialog() }
        )
    }

    val pendingImport = householdSyncState.pendingImport
    if (pendingImport != null) {
        HouseholdImportConfirmDialog(
            pending = pendingImport,
            onConfirm = { mode -> viewModel.householdSync.confirmPendingImport(mode) },
            onCancel = { viewModel.householdSync.cancelPendingImport() }
        )
    } else if (householdSyncState.showReceiveDialog) {
        HouseholdSyncReceiveDialog(
            session = householdSyncState.activeReceiveSession,
            statusMessage = householdSyncState.receiveStatusMessage,
            onStartNewSession = { viewModel.householdSync.openReceiveDialog(isTv = false) },
            onDismiss = { viewModel.householdSync.closeReceiveDialog() }
        )
    }
}
