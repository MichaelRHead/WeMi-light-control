package org.suppressingfire.wemi.network

import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.DeviceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory

data class GenaEventNotification(
    val deviceKey: String,
    val sid: String,
    val sourceIp: String,
    val binaryStateOn: Boolean?,
    val brightness: Int?,
    val rawXml: String,
    val logEntry: SoapLogEntry
)

data class GenaSubscriptionRecord(
    val deviceKey: String,
    val deviceName: String,
    val ipAddress: String,
    val port: Int,
    val sid: String,
    val subscribedAtMs: Long,
    val expiresAtMs: Long,
    val callbackToken: String = ""
)

/**
 * Implements UPnP GENA (General Event Notification Architecture) local push subscriptions:
 * - Binds a lightweight embedded HTTP NOTIFY server (port 8989 or ephemeral fallback) to the
 *   active local LAN IPv4 address with bounded concurrency (`Semaphore(8)`), header limits,
 *   and wall-clock budgets.
 * - Subscribes to `/upnp/event/basicevent1` using a per-subscription `SecureRandom` callback
 *   token and verifies source IP, constant-time token match, and active `SID` on every `NOTIFY`.
 */
class GenaSubscriptionManager(
    private val localIpProvider: () -> String,
    private val preferredListenPort: Int = 8989,
    wifiSocketFactory: SocketFactory = SocketFactory.getDefault(),
    private val onNotification: (GenaEventNotification) -> Unit
) {
    companion object {
        private const val MAX_CONCURRENT_NOTIFY_CONNECTIONS = 8
        private const val MAX_NOTIFY_HEADERS = 32
        private const val MAX_NOTIFY_HEADER_LINE_BYTES = 4096
        private const val NOTIFY_DEADLINE_MS = 2_000L
    }

    private val httpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 900,
        readTimeoutMs = 1200
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)
    private val secureRandom = SecureRandom()
    private val notifyConcurrency = Semaphore(MAX_CONCURRENT_NOTIFY_CONNECTIONS)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val subscriptionsByDeviceKey = ConcurrentHashMap<String, GenaSubscriptionRecord>()
    private val deviceKeyBySid = ConcurrentHashMap<String, String>()
    private val pendingTokensByDeviceKey = ConcurrentHashMap<String, Triple<String, String, String>>()

    val activeListenPort: Int
        get() = serverSocket?.localPort ?: -1

    val activeSubscriptions: Map<String, GenaSubscriptionRecord>
        get() = subscriptionsByDeviceKey.toMap()

    internal val activeSidCount: Int
        get() = deviceKeyBySid.size

    private fun nowTime(): String = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    private fun generateCallbackToken(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return buildString(24) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append("0123456789abcdef"[v ushr 4])
                append("0123456789abcdef"[v and 0x0F])
            }
        }
    }

    private fun bindServerSocket(targetAddr: InetAddress?): ServerSocket {
        return if (preferredListenPort > 0) {
            runCatching { ServerSocket(preferredListenPort, 32, targetAddr) }
                .getOrElse { ServerSocket(0, 32, targetAddr) }
        } else {
            ServerSocket(0, 32, targetAddr)
        }
    }

    /**
     * Starts the embedded HTTP NOTIFY callback server if not already running, preferring to bind
     * directly to the active LAN IPv4 interface rather than `0.0.0.0`.
     */
    @Synchronized
    fun startServer(bindAddress: InetAddress? = null): Int {
        if (isRunning.get() && serverSocket?.isClosed == false && acceptJob?.isActive == true) {
            return serverSocket?.localPort ?: -1
        }
        if (serverSocket?.isClosed == false) {
            runCatching { serverSocket?.close() }
            serverSocket = null
        }
        val preferredAddr = bindAddress ?: runCatching {
            val ip = localIpProvider().trim()
            if (LanEndpointPolicy.parseStrictIpv4Literal(ip) != null) InetAddress.getByName(ip) else null
        }.getOrNull()
        val socket = try {
            runCatching { bindServerSocket(preferredAddr) }
                .getOrElse { if (preferredAddr != null) bindServerSocket(null) else throw it }
        } catch (_: Exception) {
            return -1
        }

        serverSocket = socket
        isRunning.set(true)
        acceptJob = scope.launch {
            while (isActive && isRunning.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    if (!notifyConcurrency.tryAcquire()) {
                        runCatching { client.close() }
                        continue
                    }
                    try {
                        scope.launch {
                            try {
                                handleNotifyClient(client)
                            } finally {
                                notifyConcurrency.release()
                            }
                        }
                    } catch (t: Throwable) {
                        notifyConcurrency.release()
                        runCatching { client.close() }
                        if (t is kotlinx.coroutines.CancellationException) throw t
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (!isRunning.get()) break
                }
            }
        }
        return socket.localPort
    }

    /** Stops the embedded HTTP NOTIFY server and clears active subscriptions. */
    @Synchronized
    fun stopServer() {
        isRunning.set(false)
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        subscriptionsByDeviceKey.clear()
        deviceKeyBySid.clear()
        pendingTokensByDeviceKey.clear()
    }

    @Synchronized
    fun close() {
        stopServer()
        scope.cancel()
    }

    private fun handleNotifyClient(clientSocket: Socket) {
        clientSocket.use { sock ->
            try {
                sock.soTimeout = 1500
                val deadlineNs = System.nanoTime() + NOTIFY_DEADLINE_MS * 1_000_000L
                val sourceIp = sock.inetAddress?.hostAddress.orEmpty()
                val input = BufferedInputStream(sock.getInputStream())
                val requestLine = readHttpHeaderLine(input, deadlineNs) ?: return
                if (!requestLine.startsWith("NOTIFY ", ignoreCase = true)) {
                    sendHttpStatus(sock, "405 Method Not Allowed")
                    return
                }

                val path = requestLine.split(" ").getOrNull(1).orEmpty()
                val pathSuffix = path.substringAfter("/gena/", "").substringBefore('?').trim('/')
                val pathSegments = pathSuffix.split('/').filter { it.isNotEmpty() }
                val pathDeviceKey = pathSegments.getOrNull(0).orEmpty()
                val pathToken = pathSegments.getOrNull(1).orEmpty()

                var contentLength = -1
                var sidHeader = ""
                var headerCount = 0
                while (true) {
                    if (System.nanoTime() > deadlineNs) return
                    val line = readHttpHeaderLine(input, deadlineNs) ?: break
                    if (line.isEmpty()) break
                    headerCount++
                    if (headerCount > MAX_NOTIFY_HEADERS) {
                        sendHttpStatus(sock, "431 Request Header Fields Too Large")
                        return
                    }
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val k = line.substring(0, colon).trim().uppercase(Locale.US)
                        val v = line.substring(colon + 1).trim()
                        when (k) {
                            "CONTENT-LENGTH" -> contentLength = v.toIntOrNull() ?: -1
                            "SID" -> sidHeader = v
                        }
                    }
                }

                val record = subscriptionsByDeviceKey[pathDeviceKey]
                    ?: deviceKeyBySid[sidHeader]?.let { subscriptionsByDeviceKey[it] }
                val pending = pendingTokensByDeviceKey[pathDeviceKey]
                val matchesRecord = record != null &&
                    record.callbackToken.isNotEmpty() && pathToken.isNotEmpty() &&
                    MessageDigest.isEqual(pathToken.toByteArray(Charsets.US_ASCII), record.callbackToken.toByteArray(Charsets.US_ASCII)) &&
                    sourceIp == record.ipAddress && sidHeader.isNotBlank() && sidHeader == record.sid
                val matchesPending = !matchesRecord && pending != null &&
                    pathToken.isNotEmpty() &&
                    MessageDigest.isEqual(pathToken.toByteArray(Charsets.US_ASCII), pending.first.toByteArray(Charsets.US_ASCII)) &&
                    sourceIp == pending.second && sidHeader.isNotBlank()
                if (!matchesRecord && !matchesPending) {
                    sendHttpStatus(sock, "412 Precondition Failed")
                    return
                }

                val xmlBody = readNotifyBody(sock, input, contentLength, deadlineNs)
                sendHttpStatus(sock, "200 OK")

                val parsed = parseGenaPropertySetXml(xmlBody)
                if (parsed.first != null || parsed.second != null) {
                    val resolvedKey = if (matchesRecord) record!!.deviceKey else pathDeviceKey
                    val resolvedName = if (matchesRecord) record!!.deviceName else pending!!.third
                    val binaryOn = parsed.first
                    val pushedBrightness = parsed.second
                    val pushStatusText = when {
                        binaryOn != null ->
                            "Push State: ${if (binaryOn) "ON" else "OFF"}${if (pushedBrightness != null) " ($pushedBrightness%)" else ""}"
                        else -> "Push Brightness: $pushedBrightness%"
                    }
                    val log = SoapLogEntry(
                        timestamp = nowTime(),
                        deviceName = resolvedName,
                        endpoint = "NOTIFY http://${localIpProvider()}:${activeListenPort}/gena/$resolvedKey",
                        soapAction = "GENA PropChange (${sidHeader.take(22)})",
                        statusText = pushStatusText,
                        xmlSnippet = xmlBody.take(260),
                        isLiveLanSuccess = true
                    )
                    onNotification(
                        GenaEventNotification(
                            deviceKey = resolvedKey,
                            sid = sidHeader,
                            sourceIp = sourceIp,
                            binaryStateOn = parsed.first,
                            brightness = parsed.second,
                            rawXml = xmlBody,
                            logEntry = log
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore stalled, truncated, or malformed peer TCP connections without tearing down acceptJob
            }
        }
    }

    private fun readNotifyBody(
        sock: Socket,
        input: BufferedInputStream,
        contentLength: Int,
        deadlineNs: Long
    ): String = when {
        contentLength > 0 -> {
            val targetBytes = contentLength.coerceAtMost(16384)
            val buf = ByteArray(targetBytes)
            var readTotal = 0
            while (readTotal < targetBytes && System.nanoTime() <= deadlineNs) {
                val n = input.read(buf, readTotal, targetBytes - readTotal)
                if (n == -1) break
                readTotal += n
            }
            String(buf, 0, readTotal, Charsets.UTF_8)
        }
        contentLength == 0 -> ""
        else -> {
            sock.soTimeout = 150
            val out = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            runCatching {
                while (out.size() < 16384 && System.nanoTime() <= deadlineNs) {
                    val n = input.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                }
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private fun readHttpHeaderLine(input: BufferedInputStream, deadlineNs: Long): String? {
        val out = ByteArrayOutputStream(128)
        var sawAnyByte = false
        while (System.nanoTime() <= deadlineNs) {
            val b = input.read()
            if (b == -1) {
                return if (sawAnyByte) out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r') else null
            }
            sawAnyByte = true
            if (b == '\n'.code) {
                return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            if (out.size() < MAX_NOTIFY_HEADER_LINE_BYTES) {
                out.write(b)
            }
        }
        return null
    }

    private fun sendHttpStatus(socket: Socket, status: String) {
        runCatching {
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            writer.write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            writer.flush()
        }
    }

    fun parseGenaPropertySetXml(rawXml: String): Pair<Boolean?, Int?> {
        val parsed = XmlAndSkuParser.parseBinaryStatePayload(rawXml, unescapeEntities = true)
        return Pair(parsed.binaryOn, parsed.brightness)
    }

    fun deviceKeyFor(device: DeviceEntity): String {
        val raw = device.serialNumber.takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) } ?: "dev_${device.id}"
        return LanEndpointPolicy.sanitizeHardwareIdentifier(raw).ifBlank { "dev_${device.id}" }
    }

    private fun evictStalePlaceholderSubscription(device: DeviceEntity, activeDeviceKey: String) {
        if (device.id <= 0) return
        val placeholderKey = "dev_${device.id}"
        if (activeDeviceKey != placeholderKey) {
            val stale = subscriptionsByDeviceKey.remove(placeholderKey)
            if (stale != null) {
                deviceKeyBySid.remove(stale.sid, placeholderKey)
            }
        }
    }

    suspend fun subscribeOrRenew(
        device: DeviceEntity,
        timeoutSeconds: Int = 300
    ): GenaSubscriptionRecord? = withContext(Dispatchers.IO) {
        val localIp = if (device.ipAddress == "127.0.0.1") "127.0.0.1" else localIpProvider()
        if (device.ipAddress != "127.0.0.1" && LanRuntimeUtils.isCarrierGradeNatIpv4(localIp)) {
            return@withContext null
        }
        val listenPort = startServer(
            bindAddress = if (localIp == "127.0.0.1") InetAddress.getByName("127.0.0.1") else null
        )
        if (listenPort <= 0) return@withContext null

        val deviceKey = deviceKeyFor(device)
        val existing = subscriptionsByDeviceKey[deviceKey]
        val now = System.currentTimeMillis()
        val endpoint = "http://${device.ipAddress}:${device.port}/upnp/event/basicevent1"

        if (existing != null && existing.sid.isNotBlank() && existing.ipAddress == device.ipAddress && existing.port == device.port) {
            try {
                val resp = httpClient.execute(
                    method = "SUBSCRIBE",
                    url = endpoint,
                    headers = mapOf("SID" to existing.sid, "TIMEOUT" to "Second-$timeoutSeconds")
                )
                if (resp.isSuccessful) {
                    val sid = resp.header("SID")?.trim().takeUnless { it.isNullOrEmpty() } ?: existing.sid
                    if (sid != existing.sid) deviceKeyBySid.remove(existing.sid, deviceKey)
                    val updated = existing.copy(sid = sid, expiresAtMs = now + timeoutSeconds * 1000L)
                    subscriptionsByDeviceKey[deviceKey] = updated
                    deviceKeyBySid[sid] = deviceKey
                    evictStalePlaceholderSubscription(device, deviceKey)
                    return@withContext updated
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through to fresh SUBSCRIBE
            }
        }

        val callbackToken = generateCallbackToken()
        val callbackUrl = "<http://$localIp:$listenPort/gena/$deviceKey/$callbackToken>"
        val pendingEntry = Triple(callbackToken, device.ipAddress, device.name)
        pendingTokensByDeviceKey[deviceKey] = pendingEntry
        try {
            val resp = httpClient.execute(
                method = "SUBSCRIBE",
                url = endpoint,
                headers = mapOf(
                    "CALLBACK" to callbackUrl,
                    "NT" to "upnp:event",
                    "TIMEOUT" to "Second-$timeoutSeconds"
                )
            )
            if (resp.isSuccessful) {
                val sid = resp.header("SID")?.trim().orEmpty().ifEmpty { "uuid:gena-$deviceKey" }
                if (existing != null && existing.sid != sid) deviceKeyBySid.remove(existing.sid, deviceKey)
                val record = GenaSubscriptionRecord(
                    deviceKey = deviceKey,
                    deviceName = device.name,
                    ipAddress = device.ipAddress,
                    port = device.port,
                    sid = sid,
                    subscribedAtMs = now,
                    expiresAtMs = now + timeoutSeconds * 1000L,
                    callbackToken = callbackToken
                )
                subscriptionsByDeviceKey[deviceKey] = record
                deviceKeyBySid[sid] = deviceKey
                evictStalePlaceholderSubscription(device, deviceKey)
                return@withContext record
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Device offline or does not accept GENA on this port
        } finally {
            pendingTokensByDeviceKey.remove(deviceKey, pendingEntry)
        }
        null
    }

    suspend fun unsubscribeDevice(device: DeviceEntity) {
        val activeKey = deviceKeyFor(device)
        unsubscribe(activeKey)
        if (device.id > 0 && activeKey != "dev_${device.id}") {
            unsubscribe("dev_${device.id}")
        }
    }

    suspend fun unsubscribe(deviceKey: String) = withContext(Dispatchers.IO) {
        val record = subscriptionsByDeviceKey.remove(deviceKey) ?: return@withContext
        deviceKeyBySid.remove(record.sid)
        try {
            httpClient.execute(
                method = "UNSUBSCRIBE",
                url = "http://${record.ipAddress}:${record.port}/upnp/event/basicevent1",
                headers = mapOf("SID" to record.sid)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort
        }
    }
}
