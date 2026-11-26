package org.suppressingfire.wemi.ui

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.HouseholdImportMode
import org.suppressingfire.wemi.network.HouseholdSyncBundle
import org.suppressingfire.wemi.network.HouseholdSyncCodec
import org.suppressingfire.wemi.network.HouseholdSyncLanClient
import org.suppressingfire.wemi.network.HouseholdSyncLanServer
import org.suppressingfire.wemi.network.HouseholdSyncPeer
import org.suppressingfire.wemi.network.HouseholdSyncServerSession

data class PendingHouseholdImport(
    val sourceDescription: String,
    val bundle: HouseholdSyncBundle
)

data class HouseholdSyncUiState(
    val showSendDialog: Boolean = false,
    val showReceiveDialog: Boolean = false,
    val pendingImport: PendingHouseholdImport? = null,
    val discoveredPeers: List<HouseholdSyncPeer> = emptyList(),
    val isDiscoveringPeers: Boolean = false,
    val isPushingToPeer: Boolean = false,
    val sendStatusMessage: String? = null,
    val sendErrorMessage: String? = null,
    val activeReceiveSession: HouseholdSyncServerSession? = null,
    val receiveStatusMessage: String? = null,
    val isImporting: Boolean = false
) {
    val hasAnyDialogOpen: Boolean
        get() = showSendDialog || showReceiveDialog || pendingImport != null
}

/**
 * ViewModel-scoped UI controller for zero-cloud Household Configuration Sync:
 * - Part 2a: JSON Export & Import via Android Storage Access Framework (`CreateDocument`/`OpenDocument`).
 * - Part 2b: One-tap PIN-encrypted local-LAN Phone-to-TV / Phone-to-Device transfer with automatic
 *   UDP peer discovery and manual `IP:port` fallback.
 */
internal class HouseholdSyncUiController(
    private val scope: CoroutineScope,
    private val appContext: Context?,
    private val repository: SwitchRepository,
    private val onActiveNetworkKeyUpdated: (String) -> Unit,
    private val onAfterImportApplied: () -> Unit,
    private val onShowTransientStatus: (String) -> Unit
) {
    private val lanClient = HouseholdSyncLanClient(
        appContext = appContext,
        localIpProvider = { repository.detectCurrentLanIp() }
    )
    private var lanServer: HouseholdSyncLanServer? = null
    private var discoverJob: Job? = null
    private var pushJob: Job? = null
    private var receiveJob: Job? = null
    private val receiveSessionEpoch = java.util.concurrent.atomic.AtomicInteger(0)

    private val _uiState = MutableStateFlow(HouseholdSyncUiState())
    val uiState: StateFlow<HouseholdSyncUiState> = _uiState.asStateFlow()

    fun deviceDisplayLabel(isTv: Boolean): String {
        val rawModel = Build.MODEL?.trim().orEmpty()
        val label = if (rawModel.isNotEmpty() && !rawModel.equals("robolectric", ignoreCase = true)) {
            if (isTv) "$rawModel TV" else rawModel
        } else {
            if (isTv) "WeMi TV" else "WeMi Device"
        }
        return HouseholdSyncCodec.sanitizeLabel(label, maxLen = 48).ifEmpty {
            if (isTv) "WeMi TV" else "WeMi Device"
        }
    }

    fun exportHouseholdJsonToWriter(
        isTv: Boolean = false,
        writeJson: suspend (String) -> Unit
    ) {
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    repository.exportHouseholdJson(sourceDeviceLabel = deviceDisplayLabel(isTv))
                }
                withContext(Dispatchers.IO) { writeJson(json) }
                onShowTransientStatus("Exported household configuration JSON")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onShowTransientStatus("Could not export household JSON: ${e.message ?: "write failed"}")
            }
        }
    }

    fun readAndStageJsonImportFromStream(
        sourceDescription: String = "JSON File",
        openStream: () -> InputStream?
    ) {
        scope.launch {
            try {
                val jsonText = withContext(Dispatchers.IO) {
                    openStream()?.use { stream -> readBoundedUtf8(stream, HouseholdSyncCodec.MAX_JSON_BYTES + 1) }
                        ?: throw IllegalArgumentException("Could not open selected file")
                }
                stageJsonImport(jsonText = jsonText, sourceDescription = sourceDescription)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onShowTransientStatus("Could not import household JSON: ${e.message ?: "invalid file"}")
            }
        }
    }

    fun stageJsonImport(jsonText: String, sourceDescription: String = "JSON File") {
        try {
            val bundle = HouseholdSyncCodec.decodeFromJson(jsonText)
            _uiState.update {
                it.copy(
                    pendingImport = PendingHouseholdImport(
                        sourceDescription = sourceDescription,
                        bundle = bundle
                    )
                )
            }
        } catch (e: Exception) {
            onShowTransientStatus("Could not import household JSON: ${e.message ?: "invalid format"}")
        }
    }

    fun openSendDialog() {
        _uiState.update {
            it.copy(
                showSendDialog = true,
                sendStatusMessage = null,
                sendErrorMessage = null
            )
        }
        discoverLanPeers()
    }

    fun closeSendDialog() {
        discoverJob?.cancel()
        discoverJob = null
        pushJob?.cancel()
        pushJob = null
        _uiState.update {
            it.copy(
                showSendDialog = false,
                isDiscoveringPeers = false,
                isPushingToPeer = false,
                sendStatusMessage = null,
                sendErrorMessage = null
            )
        }
    }

    fun discoverLanPeers(discoveryUdpPort: Int = HouseholdSyncLanServer.DEFAULT_DISCOVERY_UDP_PORT) {
        discoverJob?.cancel()
        _uiState.update { it.copy(isDiscoveringPeers = true, sendErrorMessage = null) }
        discoverJob = scope.launch {
            try {
                val peers = lanClient.discoverPeers(discoveryUdpPort = discoveryUdpPort)
                _uiState.update {
                    it.copy(
                        discoveredPeers = peers,
                        isDiscoveringPeers = false,
                        sendStatusMessage = if (peers.isNotEmpty()) {
                            "Found ${peers.size} nearby WeMi receiver(s)"
                        } else {
                            "No receivers detected yet — open Sync on your TV/device or enter its IP & port below"
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isDiscoveringPeers = false,
                        sendStatusMessage = "Enter the receiver's IP, port, and 6-digit code below"
                    )
                }
            }
        }
    }

    fun pushToLanPeer(
        targetIp: String,
        targetPort: Int,
        pairingCode: String,
        isTvSender: Boolean = false
    ) {
        pushJob?.cancel()
        _uiState.update { it.copy(isPushingToPeer = true, sendErrorMessage = null, sendStatusMessage = "Encrypting & sending...") }
        pushJob = scope.launch {
            try {
                val label = deviceDisplayLabel(isTvSender)
                val bundle = withContext(Dispatchers.IO) {
                    repository.buildHouseholdExportBundle(sourceDeviceLabel = label)
                }
                if (bundle.homes.isEmpty() && bundle.devices.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isPushingToPeer = false,
                            sendStatusMessage = null,
                            sendErrorMessage = "No Wi-Fi Homes or switches to send yet"
                        )
                    }
                    return@launch
                }
                val result = lanClient.pushHouseholdBundle(
                    targetIp = targetIp,
                    targetPort = targetPort,
                    pairingCode = pairingCode,
                    bundle = bundle,
                    senderLabel = label
                )
                if (result.success) {
                    _uiState.update {
                        it.copy(
                            showSendDialog = false,
                            isPushingToPeer = false,
                            sendStatusMessage = null,
                            sendErrorMessage = null
                        )
                    }
                    onShowTransientStatus(result.statusMessage)
                } else {
                    _uiState.update {
                        it.copy(
                            isPushingToPeer = false,
                            sendStatusMessage = null,
                            sendErrorMessage = result.statusMessage
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPushingToPeer = false,
                        sendStatusMessage = null,
                        sendErrorMessage = "Could not send household config: ${e.message ?: "error"}"
                    )
                }
            }
        }
    }

    fun openReceiveDialog(
        isTv: Boolean,
        preferredListenPort: Int = HouseholdSyncLanServer.DEFAULT_LISTEN_PORT,
        discoveryUdpPort: Int = HouseholdSyncLanServer.DEFAULT_DISCOVERY_UDP_PORT
    ) {
        stopLanServer()
        val myEpoch = receiveSessionEpoch.incrementAndGet()
        _uiState.update {
            it.copy(
                showReceiveDialog = true,
                pendingImport = null,
                activeReceiveSession = null,
                receiveStatusMessage = "Starting local Wi-Fi pairing receiver..."
            )
        }
        receiveJob = scope.launch {
            val server = HouseholdSyncLanServer(
                localIpProvider = { repository.detectCurrentLanIp() },
                appContext = appContext,
                preferredListenPort = preferredListenPort,
                discoveryUdpPort = discoveryUdpPort
            )
            val session = try {
                withContext(Dispatchers.IO) {
                    server.startSession(
                        deviceLabel = deviceDisplayLabel(isTv),
                        isTv = isTv,
                        onPayloadReceived = { received ->
                            if (receiveSessionEpoch.get() != myEpoch) return@startSession
                            _uiState.update {
                                it.copy(
                                    activeReceiveSession = null,
                                    receiveStatusMessage = null,
                                    pendingImport = PendingHouseholdImport(
                                        sourceDescription = "${received.senderLabel} (${received.sourceIp})",
                                        bundle = received.bundle
                                    )
                                )
                            }
                        },
                        onSessionExpired = {
                            if (receiveSessionEpoch.get() != myEpoch) return@startSession
                            _uiState.update {
                                it.copy(
                                    activeReceiveSession = null,
                                    receiveStatusMessage = "Pairing code expired — tap New Code to try again"
                                )
                            }
                        },
                        onSessionLockedOut = {
                            if (receiveSessionEpoch.get() != myEpoch) return@startSession
                            _uiState.update {
                                it.copy(
                                    activeReceiveSession = null,
                                    receiveStatusMessage = "Pairing closed after too many wrong codes — tap New Code"
                                )
                            }
                        }
                    )
                }
            } catch (e: CancellationException) {
                server.close()
                throw e
            }
            if (receiveSessionEpoch.get() != myEpoch || !_uiState.value.showReceiveDialog) {
                server.close()
                return@launch
            }
            lanServer = server
            if (session == null) {
                server.close()
                lanServer = null
                val localIp = withContext(Dispatchers.IO) { repository.detectCurrentLanIp() }
                val isArcVm = org.suppressingfire.wemi.network.LanRuntimeUtils.isCarrierGradeNatIpv4(localIp) ||
                    org.suppressingfire.wemi.network.LanRuntimeUtils.isArcVmRuntime(appContext, localIp)
                val msg = if (isArcVm) {
                    "Chromebook runs inside an isolated network VM that cannot accept inbound Wi-Fi pairing. Use Import JSON here instead (Send to TV still works outbound)."
                } else {
                    "Could not start local LAN receiver — check Wi-Fi connection"
                }
                _uiState.update {
                    it.copy(
                        activeReceiveSession = null,
                        receiveStatusMessage = msg
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        activeReceiveSession = session,
                        receiveStatusMessage = "Ready on ${session.ipAddress}:${session.port}"
                    )
                }
            }
        }
    }

    fun closeReceiveDialog() {
        stopLanServer()
        _uiState.update {
            it.copy(
                showReceiveDialog = false,
                activeReceiveSession = null,
                receiveStatusMessage = null,
                pendingImport = null
            )
        }
    }

    fun cancelPendingImport() {
        stopLanServer()
        _uiState.update {
            it.copy(
                pendingImport = null,
                showReceiveDialog = false,
                activeReceiveSession = null,
                receiveStatusMessage = null
            )
        }
    }

    fun confirmPendingImport(mode: HouseholdImportMode) {
        val pending = _uiState.value.pendingImport ?: return
        stopLanServer()
        // Dismiss modal synchronously before Dispatchers.IO work (android-code-modularization Invariant 1)
        _uiState.update {
            it.copy(
                pendingImport = null,
                showReceiveDialog = false,
                activeReceiveSession = null,
                receiveStatusMessage = null,
                isImporting = true
            )
        }
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    repository.importHouseholdBundle(
                        bundle = pending.bundle,
                        mode = mode,
                        onActiveKeyResolved = { resolvedKey ->
                            onActiveNetworkKeyUpdated(resolvedKey)
                        }
                    )
                }
                onActiveNetworkKeyUpdated(summary.activeNetworkKey)
                val modeLabel = if (summary.mode == HouseholdImportMode.MERGE) "Merged" else "Imported"
                onShowTransientStatus(
                    "$modeLabel ${summary.homesAdded + summary.homesUpdated} home(s) & ${summary.devicesAdded + summary.devicesUpdated} switch(es) from ${pending.sourceDescription}"
                )
                onAfterImportApplied()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onShowTransientStatus("Could not apply household sync: ${e.message ?: "import failed"}")
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun dismissAllDialogs() {
        discoverJob?.cancel()
        discoverJob = null
        pushJob?.cancel()
        pushJob = null
        stopLanServer()
        _uiState.update { HouseholdSyncUiState() }
    }

    fun close() {
        dismissAllDialogs()
    }

    private fun stopLanServer() {
        receiveSessionEpoch.incrementAndGet()
        receiveJob?.cancel()
        receiveJob = null
        val active = lanServer
        lanServer = null
        active?.close()
    }

    private fun readBoundedUtf8(stream: InputStream, maxBytes: Int): String {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val read = stream.read(buf)
            if (read <= 0) break
            if (baos.size() + read > maxBytes) {
                throw IllegalArgumentException(
                    "File exceeds ${HouseholdSyncCodec.MAX_JSON_BYTES / 1024} KiB safety limit"
                )
            }
            baos.write(buf, 0, read)
        }
        return baos.toString("UTF-8")
    }
}
