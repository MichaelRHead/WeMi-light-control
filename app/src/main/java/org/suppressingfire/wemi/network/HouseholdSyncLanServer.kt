package org.suppressingfire.wemi.network

import android.content.Context
import android.net.wifi.WifiManager
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
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
 * Ephemeral, PIN-authenticated local-LAN HTTP receiver for one-tap Phone-to-TV and Phone-to-Device
 * household configuration transfers.
 *
 * Security & lifecycle invariants:
 * - Lives only while the user keeps the Receive/Pair dialog open (auto-expires after 120s).
 * - Binds directly to the active local LAN IPv4 address (never `0.0.0.0` unless interface bind fails).
 * - Enforces RFC 1918 source-IP validation (`127.0.0.1` test-only), `Semaphore(4)` connection cap,
 *   bounded headers (`<= 24`), bounded body (`<= 384 KiB`), and `2.5s` socket read deadlines.
 * - Requires AES-256-GCM authentication with the one-time 6-digit pairing code and locks/closes the
 *   session after `5` failed authentication attempts.
 * - Accepts at most one valid push per session before closing the listener for user confirmation.
 * - Answers best-effort UDP peer discovery beacons (`UDP 49190`) with IP/port/name only (never PIN).
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
        const val PUSH_PATH = "/wemi-sync/v1/push"
        const val DEFAULT_LISTEN_PORT = 8990
        const val DEFAULT_DISCOVERY_UDP_PORT = 49190
        const val DISCOVERY_MULTICAST_GROUP = "239.255.255.250"
        const val DISCOVERY_PROBE_PREFIX = "WEMI-SYNC-DISCOVER v1"
        const val DISCOVERY_REPLY_PREFIX = "WEMI-SYNC-PEER v1"
        const val DEFAULT_SESSION_TTL_MS = 120_000L
        const val MAX_FAILED_AUTH_ATTEMPTS = 5
        private const val MAX_CONCURRENT_CONNECTIONS = 4
        private const val MAX_HEADERS = 24
        private const val MAX_HEADER_LINE_BYTES = 4096
        private const val MAX_WIRE_BODY_BYTES = 384 * 1024
        private const val REQUEST_DEADLINE_MS = 2_500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)
    private val payloadAccepted = AtomicBoolean(false)
    private val failedAuthAttempts = AtomicInteger(0)
    private val connectionPermits = Semaphore(MAX_CONCURRENT_CONNECTIONS)

    @Volatile private var activePairingCode: String = ""
    @Volatile private var activeDeviceLabel: String = "WeMi Device"
    @Volatile private var activeIsTv: Boolean = false
    @Volatile private var activeSession: HouseholdSyncServerSession? = null

    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var tcpAcceptJob: Job? = null
    private var udpBeaconJob: Job? = null
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

        val resolvedIp = bindAddress?.hostAddress ?: localIpProvider().trim()
        val preferredAddr = bindAddress ?: runCatching {
            if (LanEndpointPolicy.parseStrictIpv4Literal(resolvedIp) != null) {
                InetAddress.getByName(resolvedIp)
            } else {
                null
            }
        }.getOrNull()

        val tcpSocket = try {
            runCatching { bindTcpSocket(preferredAddr) }
                .getOrElse { if (preferredAddr != null) bindTcpSocket(null) else throw it }
        } catch (_: Exception) {
            return null
        }

        val pin = HouseholdSyncCrypto.generatePairingCode()
        val expiresAt = System.currentTimeMillis() + sessionTtlMs
        val cleanLabel = HouseholdSyncCodec.sanitizeLabel(deviceLabel, maxLen = 48).ifEmpty {
            if (isTv) "WeMi TV" else "WeMi Device"
        }

        serverSocket = tcpSocket
        activePairingCode = pin
        activeDeviceLabel = cleanLabel
        activeIsTv = isTv
        payloadAccepted.set(false)
        failedAuthAttempts.set(0)
        isRunning.set(true)

        val actualUdpPort = startUdpDiscoveryResponder(tcpSocket.localPort, cleanLabel, isTv)
        val displayIp = preferredAddr?.hostAddress
            ?: resolvedIp.takeIf { LanEndpointPolicy.parseStrictIpv4Literal(it) != null }
            ?: "127.0.0.1"
        val session = HouseholdSyncServerSession(
            ipAddress = displayIp,
            port = tcpSocket.localPort,
            pairingCode = pin,
            expiresAtEpochMs = expiresAt,
            discoveryUdpPort = actualUdpPort
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
            if (isRunning.get() && !payloadAccepted.get()) {
                stopSession()
                onSessionExpired()
            }
        }

        return session
    }

    @Synchronized
    fun stopSession() {
        isRunning.set(false)
        activePairingCode = ""
        activeSession = null
        expiryJob?.cancel()
        expiryJob = null
        tcpAcceptJob?.cancel()
        tcpAcceptJob = null
        udpBeaconJob?.cancel()
        udpBeaconJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { udpSocket?.close() }
        udpSocket = null
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
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

    private fun startUdpDiscoveryResponder(tcpPort: Int, label: String, isTv: Boolean): Int {
        if (discoveryUdpPort < 0) return -1
        return try {
            val socket: DatagramSocket = if (discoveryUdpPort == 0) {
                DatagramSocket(0)
            } else {
                val ms = MulticastSocket(null)
                ms.reuseAddress = true
                ms.bind(InetSocketAddress(discoveryUdpPort))
                runCatching {
                    val wifiMgr = appContext?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    multicastLock = wifiMgr?.createMulticastLock("WeMiHouseholdSyncLock")?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
                runCatching {
                    @Suppress("DEPRECATION")
                    ms.joinGroup(InetAddress.getByName(DISCOVERY_MULTICAST_GROUP))
                }
                ms
            }
            socket.soTimeout = 600
            udpSocket = socket
            val replyPayload = buildString {
                append(DISCOVERY_REPLY_PREFIX)
                append(" port=").append(tcpPort)
                append(" role=").append(if (isTv) "TV" else "DEVICE")
                append(" name=").append(label)
            }.toByteArray(Charsets.UTF_8)

            udpBeaconJob = scope.launch {
                val buf = ByteArray(512)
                while (isActive && isRunning.get() && !payloadAccepted.get() && !socket.isClosed) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val senderIp = packet.address?.hostAddress.orEmpty()
                        if (!LanEndpointPolicy.isAllowedLanHost(senderIp, allowLoopback = allowLoopbackForTests)) {
                            continue
                        }
                        val msg = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                        if (msg.startsWith(DISCOVERY_PROBE_PREFIX)) {
                            val reply = DatagramPacket(replyPayload, replyPayload.size, packet.address, packet.port)
                            runCatching { socket.send(reply) }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Poll loop tick so cancellation stops promptly
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }
            socket.localPort
        } catch (_: Exception) {
            -1
        }
    }

    private fun handleClient(
        clientSocket: Socket,
        onPayloadReceived: (ReceivedHouseholdSync) -> Unit,
        onSessionLockedOut: () -> Unit
    ) {
        clientSocket.use { sock ->
            try {
                sock.soTimeout = 2_000
                val deadlineNs = System.nanoTime() + REQUEST_DEADLINE_MS * 1_000_000L
                val sourceIp = sock.inetAddress?.hostAddress.orEmpty()
                if (!LanEndpointPolicy.isAllowedLanHost(sourceIp, allowLoopback = allowLoopbackForTests)) {
                    sendJsonResponse(sock, "403 Forbidden", """{"ok":false,"error":"non_lan_source"}""")
                    return
                }
                val input = BufferedInputStream(sock.getInputStream())
                val requestLine = readHeaderLine(input, deadlineNs) ?: return
                val parts = requestLine.split(' ')
                val method = parts.getOrNull(0).orEmpty()
                val rawTarget = parts.getOrNull(1).orEmpty().substringBefore('?')
                if (!method.equals("POST", ignoreCase = true)) {
                    sendJsonResponse(sock, "405 Method Not Allowed", """{"ok":false,"error":"method_not_allowed"}""")
                    return
                }
                if (rawTarget != PUSH_PATH) {
                    sendJsonResponse(sock, "404 Not Found", """{"ok":false,"error":"not_found"}""")
                    return
                }

                var contentLength = -1
                var headerCount = 0
                while (true) {
                    if (System.nanoTime() > deadlineNs) {
                        sendJsonResponse(sock, "408 Request Timeout", """{"ok":false,"error":"timeout"}""")
                        return
                    }
                    val line = readHeaderLine(input, deadlineNs) ?: break
                    if (line.isEmpty()) break
                    headerCount++
                    if (headerCount > MAX_HEADERS) {
                        sendJsonResponse(sock, "431 Request Header Fields Too Large", """{"ok":false,"error":"too_many_headers"}""")
                        return
                    }
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().uppercase(Locale.US)
                        val value = line.substring(colon + 1).trim()
                        if (key == "CONTENT-LENGTH") contentLength = value.toIntOrNull() ?: -1
                    }
                }

                if (contentLength <= 0 || contentLength > MAX_WIRE_BODY_BYTES) {
                    sendJsonResponse(sock, "413 Payload Too Large", """{"ok":false,"error":"invalid_length"}""")
                    return
                }
                if (payloadAccepted.get()) {
                    sendJsonResponse(sock, "409 Conflict", """{"ok":false,"error":"already_received"}""")
                    return
                }
                val pinSnapshot = activePairingCode
                if (!isRunning.get() || pinSnapshot.length != 6) {
                    sendJsonResponse(sock, "410 Gone", """{"ok":false,"error":"session_closed"}""")
                    return
                }

                val bodyBytes = readExactBody(input, contentLength, deadlineNs) ?: run {
                    sendJsonResponse(sock, "408 Request Timeout", """{"ok":false,"error":"incomplete_body"}""")
                    return
                }
                val envelopeText = String(bodyBytes, Charsets.UTF_8)
                val (senderLabel, plaintextJson) = try {
                    HouseholdSyncCrypto.decryptEnvelope(envelopeText, pinSnapshot)
                } catch (_: SecurityException) {
                    val failures = failedAuthAttempts.incrementAndGet()
                    if (failures >= MAX_FAILED_AUTH_ATTEMPTS) {
                        sendJsonResponse(sock, "429 Too Many Requests", """{"ok":false,"error":"locked_out"}""")
                        stopSession()
                        onSessionLockedOut()
                    } else {
                        sendJsonResponse(sock, "401 Unauthorized", """{"ok":false,"error":"invalid_code"}""")
                    }
                    return
                } catch (_: IllegalArgumentException) {
                    sendJsonResponse(sock, "400 Bad Request", """{"ok":false,"error":"malformed_envelope"}""")
                    return
                }

                val bundle = try {
                    HouseholdSyncCodec.decodeFromJson(plaintextJson, allowLoopback = allowLoopbackForTests)
                } catch (_: IllegalArgumentException) {
                    sendJsonResponse(sock, "400 Bad Request", """{"ok":false,"error":"invalid_household_json"}""")
                    return
                }

                if (!payloadAccepted.compareAndSet(false, true)) {
                    sendJsonResponse(sock, "409 Conflict", """{"ok":false,"error":"already_received"}""")
                    return
                }

                sendJsonResponse(
                    sock,
                    "200 OK",
                    """{"ok":true,"homes":${bundle.homes.size},"devices":${bundle.devices.size}}"""
                )
                stopSession()
                onPayloadReceived(
                    ReceivedHouseholdSync(
                        sourceIp = sourceIp,
                        senderLabel = senderLabel.ifBlank { bundle.sourceDeviceLabel },
                        bundle = bundle
                    )
                )
            } catch (_: Exception) {
                // Close malformed/aborted client quietly
            }
        }
    }

    private fun readHeaderLine(input: BufferedInputStream, deadlineNs: Long): String? {
        val baos = ByteArrayOutputStream()
        while (true) {
            if (System.nanoTime() > deadlineNs) return null
            val b = input.read()
            if (b == -1) return if (baos.size() == 0) null else baos.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                if (baos.size() >= MAX_HEADER_LINE_BYTES) return null
                baos.write(b)
            }
        }
        return baos.toString("ISO-8859-1")
    }

    private fun readExactBody(input: BufferedInputStream, length: Int, deadlineNs: Long): ByteArray? {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            if (System.nanoTime() > deadlineNs) return null
            val n = input.read(out, offset, length - offset)
            if (n <= 0) return null
            offset += n
        }
        return out
    }

    private fun sendJsonResponse(sock: Socket, status: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.US_ASCII)
        writer.write("HTTP/1.1 $status\r\n")
        writer.write("Content-Type: application/json; charset=utf-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.flush()
        sock.getOutputStream().write(bytes)
        sock.getOutputStream().flush()
    }
}
