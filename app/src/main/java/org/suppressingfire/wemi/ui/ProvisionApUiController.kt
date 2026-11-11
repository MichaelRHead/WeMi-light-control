package org.suppressingfire.wemi.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.ScannedAp
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.SetupDeviceDescriptor

/**
 * Cohesive UI state controller for the zero-cloud `10.22.22.1` fresh-switch Setup AP wizard
 * (`ProvisionApDialog` and `ProvisionDebugPanel`), extracted from [SwitchViewModel] so the
 * provisioning wizard state machine and telemetry snapshot lifecycle stand alone cleanly.
 */
internal class ProvisionApUiController(
    private val scope: CoroutineScope,
    private val repository: SwitchRepository,
    private val networkMonitor: NetworkMonitor,
    private val activeNetworkKeyProvider: () -> String,
    private val onShowTransientStatus: (String) -> Unit
) {
    private val _showProvisionApDialog = MutableStateFlow(false)
    val showProvisionApDialog: StateFlow<Boolean> = _showProvisionApDialog.asStateFlow()

    private val _scannedSetupAps = MutableStateFlow<List<ScannedAp>>(emptyList())
    val scannedSetupAps: StateFlow<List<ScannedAp>> = _scannedSetupAps.asStateFlow()

    private val _detectedSetupDevice = MutableStateFlow<SetupDeviceDescriptor?>(null)
    val detectedSetupDevice: StateFlow<SetupDeviceDescriptor?> = _detectedSetupDevice.asStateFlow()

    private val _setupApStatusMessage = MutableStateFlow<String?>(null)
    val setupApStatusMessage: StateFlow<String?> = _setupApStatusMessage.asStateFlow()

    private val _setupDebugSnapshot = MutableStateFlow(SetupDebugSnapshot())
    val setupDebugSnapshot: StateFlow<SetupDebugSnapshot> = _setupDebugSnapshot.asStateFlow()

    private val _isScanningSetupAp = MutableStateFlow(false)
    val isScanningSetupAp: StateFlow<Boolean> = _isScanningSetupAp.asStateFlow()

    private val _isProvisioningAp = MutableStateFlow(false)
    val isProvisioningAp: StateFlow<Boolean> = _isProvisioningAp.asStateFlow()

    private fun nowTimeText(): String =
        SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    fun openProvisionApDialog() {
        _setupApStatusMessage.value = null
        _showProvisionApDialog.value = true
        val netSnap = networkMonitor.refreshSnapshot()
        val preferredHost = if (netSnap.isWemoSetupApSubnet) {
            netSnap.gatewayIp.ifBlank { "10.22.22.1" }
        } else {
            "10.22.22.1"
        }
        scanSetupApNetworks(preferredHost)
    }

    fun closeProvisionApDialog() {
        _showProvisionApDialog.value = false
    }

    fun scanSetupApNetworks(apHost: String = "10.22.22.1") {
        scope.launch {
            _isScanningSetupAp.value = true
            _setupApStatusMessage.value = null
            val cleanHost = apHost.trim().ifBlank { "10.22.22.1" }
            val netSnap = networkMonitor.refreshSnapshot()
            _setupDebugSnapshot.value = SetupDebugSnapshot(
                timestamp = nowTimeText(),
                phoneLocalIp = netSnap.localIpv4,
                phoneGatewayIp = netSnap.gatewayIp,
                phoneInterface = netSnap.interfaceName,
                isWemoSetupSubnet = netSnap.isWemoSetupApSubnet,
                isWifiSocketBound = netSnap.isWifiSocketBound,
                isDemoMode = repository.isDemoShowroomMode.value,
                targetApHost = cleanHost,
                probeSummary = "Probing $cleanHost (ports 49152..49159)...",
                apScanSummary = "Waiting for probe..."
            )

            val descriptor = repository.probeSetupApDevice(apHost = cleanHost)
            _detectedSetupDevice.value = descriptor
            val probeTrace = descriptor?.probeTraceSummary?.ifBlank { repository.latestSetupProbeSummary() }
                ?: repository.latestSetupProbeSummary()

            _setupDebugSnapshot.value = _setupDebugSnapshot.value.copy(
                timestamp = nowTimeText(),
                detectedDevice = descriptor,
                probeSummary = probeTrace,
                apScanSummary = "Scanning 2.4 GHz APs via WiFiSetup1#GetApList..."
            )

            val list = repository.scanSetupApNetworks(
                apHost = cleanHost,
                apPort = descriptor?.port ?: 49152
            )
            _scannedSetupAps.value = list
            _isScanningSetupAp.value = false

            val scanSummary = if (descriptor?.macAndSerialSource == "DemoShowroom") {
                "DEMO_AP_LIST(count=${list.size})"
            } else {
                repository.latestSetupApScanSummary()
            }
            _setupDebugSnapshot.value = _setupDebugSnapshot.value.copy(
                timestamp = nowTimeText(),
                scannedAps = list,
                apScanSummary = scanSummary
            )

            if (list.isNotEmpty() || descriptor != null) {
                val hwSummary = if (descriptor != null) {
                    "Detected ${descriptor.friendlyName} (${descriptor.hardwareSku} • Port ${descriptor.port} • Auto Method ${descriptor.recommendedEncryptionMethod})"
                } else {
                    "Smart Switch Setup AP ($cleanHost)"
                }
                val msg = "$hwSummary • Found ${list.size} nearby 2.4 GHz Wi-Fi network(s)"
                _setupApStatusMessage.value = msg
                onShowTransientStatus(msg)
            } else {
                val err = "No response from Setup AP at $cleanHost ($probeTrace) — Connect phone Wi-Fi to the switch's 10.22.22.1 setup network first."
                _setupApStatusMessage.value = err
                onShowTransientStatus(err)
            }
        }
    }

    fun provisionSetupAp(
        apHost: String = "10.22.22.1",
        ssid: String,
        password: String,
        channel: Int = 6,
        authMode: String = "WPA2PSK",
        encryptionType: String = "AES",
        encryptionMethod: Int = 0,
        addPasswordLengths: Boolean? = null,
        customDeviceName: String = "",
        roomName: String = "",
        deviceType: DeviceType? = null,
        keepDialogOpenOnSuccess: Boolean = false
    ) {
        scope.launch {
            val cleanHost = apHost.trim().ifBlank { "10.22.22.1" }
            val netSnap = networkMonitor.refreshSnapshot()
            _isProvisioningAp.value = true
            _setupApStatusMessage.value = "Provisioning switch on $cleanHost to '$ssid'..."
            _setupDebugSnapshot.value = _setupDebugSnapshot.value.copy(
                timestamp = nowTimeText(),
                phoneLocalIp = netSnap.localIpv4,
                phoneGatewayIp = netSnap.gatewayIp,
                phoneInterface = netSnap.interfaceName,
                isWemoSetupSubnet = netSnap.isWemoSetupApSubnet,
                isWifiSocketBound = netSnap.isWifiSocketBound,
                isDemoMode = repository.isDemoShowroomMode.value,
                targetApHost = cleanHost,
                traceLines = listOf("[START] Starting WiFiSetup1 provisioning to \"$ssid\" on $cleanHost...")
            )

            val res = repository.provisionSetupApDevice(
                apHost = cleanHost,
                ssid = ssid.trim(),
                plainPassword = password,
                channel = channel,
                authMode = authMode,
                encryptionType = encryptionType,
                encryptionMethod = encryptionMethod,
                addPasswordLengths = addPasswordLengths,
                customDeviceName = customDeviceName.trim(),
                roomName = roomName.trim(),
                deviceType = deviceType,
                activeNetworkKey = activeNetworkKeyProvider(),
                onProgress = { stepLine ->
                    val current = _setupDebugSnapshot.value
                    _setupDebugSnapshot.value = current.copy(
                        timestamp = nowTimeText(),
                        traceLines = (current.traceLines + stepLine).takeLast(24)
                    )
                }
            )
            _isProvisioningAp.value = false
            _setupApStatusMessage.value = res.statusMessage
            if (res.setupDevice != null) {
                _detectedSetupDevice.value = res.setupDevice
            }
            _setupDebugSnapshot.value = _setupDebugSnapshot.value.copy(
                timestamp = nowTimeText(),
                detectedDevice = res.setupDevice ?: _detectedSetupDevice.value,
                probeSummary = res.setupDevice?.probeTraceSummary?.ifBlank { repository.latestSetupProbeSummary() }
                    ?: _setupDebugSnapshot.value.probeSummary,
                traceLines = res.debugTraceLines.ifEmpty { _setupDebugSnapshot.value.traceLines }
            )
            if (res.success && !keepDialogOpenOnSuccess) {
                _showProvisionApDialog.value = false
            }
            onShowTransientStatus(res.statusMessage)
        }
    }
}
