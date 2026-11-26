package org.suppressingfire.wemi.network

import android.content.Context
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HouseholdSyncPeer(
    val ipAddress: String,
    val port: Int,
    val deviceName: String,
    val isTv: Boolean = false
)

data class HouseholdSyncServerSession(
    val ipAddress: String,
    val port: Int,
    val pairingCode: String,
    val expiresAtEpochMs: Long,
    val discoveryUdpPort: Int
)

data class ReceivedHouseholdSync(
    val sourceIp: String,
    val senderLabel: String,
    val bundle: HouseholdSyncBundle
)

/**
 * Ephemeral, SPAKE2-authenticated local-LAN HTTP receiver for one-tap Phone-to-TV and
 * Phone-to-Device household configuration transfers.
 *
 * Security & lifecycle invariants:
 * - Lives only while the user keeps the Receive/Pair dialog open (auto-expires after 120s).
 * - Binds directly to the active local LAN IPv4 address (rejecting CGNAT / loopback in production).
 * - Enforces RFC 1918 source-IP validation (`127.0.0.1` test-only), `Semaphore(4)` connection cap,
 *   hardened HTTP framing ([HouseholdSyncHttpFraming]), and `2.5s` wall-clock request deadlines.
 * - Requires a 2-step RFC 9382 SPAKE2 handshake (`POST /wemi-sync/v1/hello` -> `POST /wemi-sync/v1/push`,
 *   `v = 2` only, zero v1 downgrade) serialized under [authLock] with single-use nonces, 5-attempt
 *   lockout, and single-payload acceptance.
 * - Rate-limits best-effort UDP peer discovery beacons (`UDP 49190`) to non-privileged ports only.
 */
class HouseholdSyncLanServer(
    private val localIpProvider: () -> String,
    private val appContext: Context? = null,
    private val preferredListenPort: Int = DEFAULT_LISTEN_PORT,
    private val discoveryUdpPort: Int = DEFAULT_DISCOVERY_UDP_PORT,
    private val sessionTtlMs: Long = DEFAULT_SESSION_TTL_MS,
    private val allowLoopbackForTests: Boolean = LanRuntimeUtils.isRunningInRobolectric()
) {
    companion object {
        const val HELLO_PATH = "/wemi-sync/v1/hello"
        const val PUSH_PATH = "/wemi-sync/v1/push"
        const val DEFAULT_LISTEN_PORT = 8990
        const val DEFAULT_DISCOVERY_UDP_PORT = 49190
        const val DISCOVERY_MULTICAST_GROUP = "239.255.255.250"
        const val DISCOVERY_PROBE_PREFIX = "WEMI-SYNC-DISCOVER v1"
        const val DISCOVERY_REPLY_PREFIX = "WEMI-SYNC-PEER v1"
        const val DEFAULT_SESSION_TTL_MS = 120_000L
        const val MAX_FAILED_AUTH_ATTEMPTS = 5
        private const val MAX_HELLO_CHALLENGES_PER_SESSION = 16
        private const val MAX_ACTIVE_CHALLENGES = 4
        private const val MAX_CONCURRENT_CONNECTIONS = 4
        private const val MAX_HEADERS = 24
        private const val MAX_HEADER_LINE_BYTES = 4096
        private const val MAX_WIRE_BODY_BYTES = 384 * 1024
        private const val REQUEST_DEADLINE_MS = 2_500L
        private val ALLOWED_PATHS = setOf(HELLO_PATH, PUSH_PATH)
        private val BODY_OPTIONAL_PATHS = setOf(HELLO_PATH)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)
    private val payloadAccepted = AtomicBoolean(false)
    private val failedAuthAttempts = AtomicInteger(0)
    private val connectionPermits = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private val authLock = Any()
    private val pendingChallenges = LinkedHashMap<String, HouseholdSyncPake.ServerChallenge>()
    private var helloChallengesIssued = 0

    @Volatile private var activePairingCode: String = ""
    @Volatile private var activeExpiresAtMs: Long = 0L
    @Volatile private var activeSession: HouseholdSyncServerSession? = null

    private var serverSocket: ServerSocket? = null
    private var udpBeaconHandle: HouseholdSyncHttpFraming.UdpBeaconHandle? = null
    private var tcpAcceptJob: Job? = null
    private var expiryJob: Job? = null

    val currentSession: HouseholdSyncServerSession?
        get() = activeSession

    @Synchronized
    fun startSession(
        deviceLabel: String,
        isTv: Boolean,
        bindAddress: InetAddress? = null,
        onPayloadReceived: (ReceivedHouseholdSync) -> Unit,
        onSessionExpired: () -> Unit,
        onSessionLockedOut: () -> Unit
    ): HouseholdSyncServerSession? {
        stopSession()

        val rawLocalIp = localIpProvider().trim()
        val resolvedIp = when {
            bindAddress != null -> bindAddress.hostAddress.orEmpty()
            allowLoopbackForTests && NetworkMonitor.activeLanBindingOverrideForTests == null -> "127.0.0.1"
            else -> rawLocalIp
        }
        if (LanRuntimeUtils.isCarrierGradeNatIpv4(resolvedIp) || resolvedIp.startsWith("10.22.22.")) {
            return null
        }
        if (!LanEndpointPolicy.isAllowedLanHost(resolvedIp, allowLoopback = allowLoopbackForTests)) {
            return null
        }
        val preferredAddr = bindAddress ?: runCatching { InetAddress.getByName(resolvedIp) }.getOrNull()
            ?: return null

        var boundToPreferred = true
        val tcpSocket = try {
            runCatching { bindTcpSocket(preferredAddr) }.getOrElse {
                boundToPreferred = false
                if (!allowLoopbackForTests) throw it
                bindTcpSocket(InetAddress.getByName("127.0.0.1"))
            }
        } catch (_: Exception) {
            return null
        }

        val pin = HouseholdSyncCrypto.generatePairingCode()
        val expiresAt = System.currentTimeMillis() + sessionTtlMs
        val cleanLabel = HouseholdSyncCodec.sanitizeLabel(deviceLabel, maxLen = 48).ifEmpty {
            if (isTv) "WeMi TV" else "WeMi Device"
        }

        serverSocket = tcpSocket
        synchronized(authLock) {
            pendingChallenges.clear()
            helloChallengesIssued = 0
            activePairingCode = pin
            activeExpiresAtMs = expiresAt
            payloadAccepted.set(false)
            failedAuthAttempts.set(0)
            isRunning.set(true)
        }

        udpBeaconHandle = HouseholdSyncHttpFraming.startUdpDiscoveryBeacon(
            scope = scope,
            appContext = appContext,
            discoveryUdpPort = discoveryUdpPort,
            tcpPort = tcpSocket.localPort,
            label = cleanLabel,
            isTv = isTv,
            localIp = resolvedIp,
            allowLoopbackForTests = allowLoopbackForTests,
            isStillAccepting = { isRunning.get() && !payloadAccepted.get() }
        )
        val displayIp = if (boundToPreferred) preferredAddr.hostAddress.orEmpty() else "127.0.0.1"
        val session = HouseholdSyncServerSession(
            ipAddress = displayIp,
            port = tcpSocket.localPort,
            pairingCode = pin,
            expiresAtEpochMs = expiresAt,
            discoveryUdpPort = udpBeaconHandle?.localPort ?: -1
        )
        activeSession = session

        tcpAcceptJob = scope.launch {
            while (isActive && isRunning.get() && !tcpSocket.isClosed) {
                try {
                    val client = tcpSocket.accept()
                    if (!connectionPermits.tryAcquire()) {
                        runCatching { client.close() }
                        continue
                    }
                    try {
                        scope.launch {
                            try {
                                handleClient(client, onPayloadReceived, onSessionLockedOut)
                            } finally {
                                connectionPermits.release()
                            }
                        }
                    } catch (t: Throwable) {
                        connectionPermits.release()
                        runCatching { client.close() }
                        if (t is CancellationException) throw t
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (!isRunning.get()) break
                }
            }
        }

        expiryJob = scope.launch {
            delay(sessionTtlMs)
            val shouldExpire = synchronized(authLock) {
                isRunning.get() && !payloadAccepted.get()
            }
            if (shouldExpire) {
                stopSession()
                onSessionExpired()
            }
        }

        return session
    }

    @Synchronized
    fun stopSession() {
        synchronized(authLock) {
            isRunning.set(false)
            activePairingCode = ""
            activeExpiresAtMs = 0L
            pendingChallenges.clear()
            helloChallengesIssued = 0
        }
        activeSession = null
        expiryJob?.cancel()
        expiryJob = null
        tcpAcceptJob?.cancel()
        tcpAcceptJob = null
        udpBeaconHandle?.close()
        udpBeaconHandle = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    @Synchronized
    fun close() {
        stopSession()
        scope.cancel()
    }

    private fun bindTcpSocket(targetAddr: InetAddress?): ServerSocket {
        return if (preferredListenPort > 0) {
            runCatching { ServerSocket(preferredListenPort, 16, targetAddr) }
                .getOrElse { ServerSocket(0, 16, targetAddr) }
        } else {
            ServerSocket(0, 16, targetAddr)
        }
    }

    private fun handleClient(
        clientSocket: Socket,
        onPayloadReceived: (ReceivedHouseholdSync) -> Unit,
        onSessionLockedOut: () -> Unit
    ) {
        clientSocket.use { sock ->
            try {
                val deadlineNs = System.nanoTime() + REQUEST_DEADLINE_MS * 1_000_000L
                val sourceIp = sock.inetAddress?.hostAddress.orEmpty()
                if (!LanEndpointPolicy.isAllowedLanHost(sourceIp, allowLoopback = allowLoopbackForTests)) {
                    HouseholdSyncHttpFraming.sendJsonResponse(
                        sock,
                        "403 Forbidden",
                        """{"ok":false,"error":"non_lan_source"}"""
                    )
                    return
                }
                val request = when (
                    val res = HouseholdSyncHttpFraming.readRequest(
                        sock = sock,
                        deadlineNs = deadlineNs,
                        maxHeaders = MAX_HEADERS,
                        maxHeaderLineBytes = MAX_HEADER_LINE_BYTES,
                        maxBodyBytes = MAX_WIRE_BODY_BYTES,
                        allowedPaths = ALLOWED_PATHS,
                        bodyOptionalPaths = BODY_OPTIONAL_PATHS
                    )
                ) {
                    is HouseholdSyncHttpFraming.ReadResult.Ok -> res
                    is HouseholdSyncHttpFraming.ReadResult.Error -> {
                        HouseholdSyncHttpFraming.sendJsonResponse(
                            sock,
                            res.httpStatus,
                            """{"ok":false,"error":"${res.errorCode}"}"""
                        )
                        return
                    }
                }

                when (request.path) {
                    HELLO_PATH -> handleHelloRequest(sock)
                    PUSH_PATH -> handlePushRequest(sock, sourceIp, request.bodyBytes, onPayloadReceived, onSessionLockedOut)
                }
            } catch (_: Exception) {
                // Close malformed/aborted client quietly
            }
        }
    }

    private fun handleHelloRequest(sock: Socket) {
        val (status, body) = synchronized(authLock) {
            when {
                payloadAccepted.get() -> "409 Conflict" to """{"ok":false,"error":"already_received"}"""
                !isRunning.get() || activePairingCode.length != 6 || System.currentTimeMillis() >= activeExpiresAtMs ->
                    "410 Gone" to """{"ok":false,"error":"session_closed"}"""
                failedAuthAttempts.get() >= MAX_FAILED_AUTH_ATTEMPTS ||
                    helloChallengesIssued >= MAX_HELLO_CHALLENGES_PER_SESSION ->
                    "429 Too Many Requests" to """{"ok":false,"error":"locked_out"}"""
                else -> {
                    helloChallengesIssued++
                    val challenge = HouseholdSyncPake.createServerChallenge(activePairingCode)
                    if (pendingChallenges.size >= MAX_ACTIVE_CHALLENGES) {
                        pendingChallenges.keys.firstOrNull()?.let { pendingChallenges.remove(it) }
                    }
                    pendingChallenges[challenge.nonceB64] = challenge
                    "200 OK" to challenge.responseJson
                }
            }
        }
        HouseholdSyncHttpFraming.sendJsonResponse(sock, status, body)
    }

    private fun handlePushRequest(
        sock: Socket,
        sourceIp: String,
        bodyBytes: ByteArray,
        onPayloadReceived: (ReceivedHouseholdSync) -> Unit,
        onSessionLockedOut: () -> Unit
    ) {
        val parsedPush = try {
            HouseholdSyncPake.parsePushEnvelopeV2(String(bodyBytes, Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            HouseholdSyncHttpFraming.sendJsonResponse(
                sock,
                "400 Bad Request",
                """{"ok":false,"error":"malformed_envelope"}"""
            )
            return
        }

        var acceptedPayload: ReceivedHouseholdSync? = null
        var shouldLockOut = false
        val (status, body) = synchronized(authLock) {
            when {
                payloadAccepted.get() -> "409 Conflict" to """{"ok":false,"error":"already_received"}"""
                !isRunning.get() || activePairingCode.length != 6 || System.currentTimeMillis() >= activeExpiresAtMs ->
                    "410 Gone" to """{"ok":false,"error":"session_closed"}"""
                failedAuthAttempts.get() >= MAX_FAILED_AUTH_ATTEMPTS ->
                    "429 Too Many Requests" to """{"ok":false,"error":"locked_out"}"""
                else -> {
                    // Single-use nonce: burn immediately before verifying to prevent replays.
                    val challenge = pendingChallenges.remove(parsedPush.nonceB64)
                    if (challenge == null) {
                        val failures = failedAuthAttempts.incrementAndGet()
                        if (failures >= MAX_FAILED_AUTH_ATTEMPTS) {
                            isRunning.set(false)
                            shouldLockOut = true
                            "429 Too Many Requests" to """{"ok":false,"error":"locked_out"}"""
                        } else {
                            "401 Unauthorized" to """{"ok":false,"error":"invalid_handshake"}"""
                        }
                    } else {
                        try {
                            val (senderLabel, plaintextJson) = HouseholdSyncPake.decryptPushV2(parsedPush, challenge)
                            val bundle = HouseholdSyncCodec.decodeFromJson(
                                plaintextJson,
                                allowLoopback = allowLoopbackForTests
                            )
                            payloadAccepted.set(true)
                            isRunning.set(false)
                            acceptedPayload = ReceivedHouseholdSync(
                                sourceIp = sourceIp,
                                senderLabel = bundle.sourceDeviceLabel.ifBlank { senderLabel },
                                bundle = bundle
                            )
                            "200 OK" to """{"ok":true,"homes":${bundle.homes.size},"devices":${bundle.devices.size}}"""
                        } catch (_: SecurityException) {
                            val failures = failedAuthAttempts.incrementAndGet()
                            if (failures >= MAX_FAILED_AUTH_ATTEMPTS) {
                                isRunning.set(false)
                                shouldLockOut = true
                                "429 Too Many Requests" to """{"ok":false,"error":"locked_out"}"""
                            } else {
                                "401 Unauthorized" to """{"ok":false,"error":"invalid_code"}"""
                            }
                        } catch (_: IllegalArgumentException) {
                            "400 Bad Request" to """{"ok":false,"error":"invalid_household_json"}"""
                        }
                    }
                }
            }
        }

        HouseholdSyncHttpFraming.sendJsonResponse(sock, status, body)
        if (shouldLockOut) {
            stopSession()
            onSessionLockedOut()
        } else if (acceptedPayload != null) {
            stopSession()
            onPayloadReceived(acceptedPayload!!)
        }
    }
}
