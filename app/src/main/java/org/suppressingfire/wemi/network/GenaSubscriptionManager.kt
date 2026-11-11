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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
    val expiresAtMs: Long
)

/**
 * Implements UPnP GENA (General Event Notification Architecture) local push subscriptions:
 * - Runs a lightweight embedded HTTP NOTIFY server (port 8989 or ephemeral fallback) on the local LAN.
 * - Subscribes to /upnp/event/basicevent1 with TIMEOUT: Second-300.
 * - Automatically recovers from HTTP 412 Precondition Failed on renewal by issuing a fresh SUBSCRIBE.
 * - Parses <e:propertyset> XML payloads for <BinaryState> and <Brightness>/<brightness> changes
 *   pushed when a physical wall paddle, 3-way traveler, or dimmer bar is flipped.
 */
class GenaSubscriptionManager(
    private val localIpProvider: () -> String,
    private val preferredListenPort: Int = 8989,
    wifiSocketFactory: SocketFactory = SocketFactory.getDefault(),
    private val onNotification: (GenaEventNotification) -> Unit
) {
    private val httpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 900,
        readTimeoutMs = 1200
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val subscriptionsByDeviceKey = ConcurrentHashMap<String, GenaSubscriptionRecord>()
    private val deviceKeyBySid = ConcurrentHashMap<String, String>()

    val activeListenPort: Int
        get() = serverSocket?.localPort ?: -1

    val activeSubscriptions: Map<String, GenaSubscriptionRecord>
        get() = subscriptionsByDeviceKey.toMap()

    private fun nowTime(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
    }

    /**
     * Starts the embedded HTTP NOTIFY callback server if not already running.
     * Returns the bound local TCP port, or -1 if binding failed.
     */
    @Synchronized
    fun startServer(bindAddress: InetAddress? = null): Int {
        if (isRunning.get() && serverSocket?.isClosed == false) {
            return serverSocket?.localPort ?: -1
        }
        val socket = try {
            if (preferredListenPort > 0) {
                runCatching {
                    ServerSocket(preferredListenPort, 32, bindAddress)
                }.getOrElse {
                    ServerSocket(0, 32, bindAddress)
                }
            } else {
                ServerSocket(0, 32, bindAddress)
            }
        } catch (_: Exception) {
            return -1
        }

        serverSocket = socket
        isRunning.set(true)
        acceptJob = scope.launch {
            while (isActive && isRunning.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch { handleNotifyClient(client) }
                } catch (_: Exception) {
                    if (!isRunning.get()) break
                }
            }
        }
        return socket.localPort
    }

    /**
     * Stops the embedded HTTP NOTIFY server and clears active subscriptions.
     */
    @Synchronized
    fun stopServer() {
        isRunning.set(false)
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        subscriptionsByDeviceKey.clear()
        deviceKeyBySid.clear()
    }

    @Synchronized
    fun close() {
        stopServer()
        scope.cancel()
    }

    private fun handleNotifyClient(clientSocket: Socket) {
        clientSocket.use { sock ->
            sock.soTimeout = 1500
            val sourceIp = sock.inetAddress?.hostAddress.orEmpty()
            val input = BufferedInputStream(sock.getInputStream())
            val requestLine = readHttpHeaderLine(input) ?: return
            if (!requestLine.startsWith("NOTIFY", ignoreCase = true)) {
                sendHttp200(sock)
                return
            }

            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            val pathDeviceKey = path.substringAfterLast("/gena/", "").trim()

            var contentLength = -1
            var sidHeader = ""
            while (true) {
                val line = readHttpHeaderLine(input) ?: break
                if (line.isEmpty()) break
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

            val xmlBody = when {
                contentLength > 0 -> {
                    val targetBytes = contentLength.coerceAtMost(16384)
                    val buf = ByteArray(targetBytes)
                    var readTotal = 0
                    while (readTotal < targetBytes) {
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
                        while (out.size() < 16384) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                        }
                    }
                    out.toString(Charsets.UTF_8.name())
                }
            }

            // Always reply HTTP 200 OK immediately so the embedded RTOS socket is freed
            sendHttp200(sock)

            val parsed = parseGenaPropertySetXml(xmlBody)
            if (parsed.first != null || parsed.second != null) {
                val resolvedKey = pathDeviceKey.ifEmpty {
                    deviceKeyBySid[sidHeader].orEmpty()
                }
                val record = subscriptionsByDeviceKey[resolvedKey]
                val devName = record?.deviceName ?: resolvedKey.ifEmpty { sourceIp }
                val log = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = devName,
                    endpoint = "NOTIFY http://${localIpProvider()}:${activeListenPort}$path",
                    soapAction = "GENA PropChange (${sidHeader.take(22)})",
                    statusText = "Push State: ${if (parsed.first == true) "ON" else "OFF"}${if (parsed.second != null) " (${parsed.second}%)" else ""}",
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
        }
    }

    private fun readHttpHeaderLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream(128)
        while (out.size() < 8192) {
            val b = input.read()
            if (b == -1) {
                return if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            if (b == '\n'.code) {
                return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            out.write(b)
        }
        return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
    }

    private fun sendHttp200(socket: Socket) {
        runCatching {
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            writer.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            writer.flush()
        }
    }

    /**
     * Parses a Belkin WeMo GENA <e:propertyset> XML notification body for <BinaryState> and <Brightness>.
     * Handles both plain and XML-entity-escaped inner payloads.
     */
    fun parseGenaPropertySetXml(rawXml: String): Pair<Boolean?, Int?> {
        val parsed = XmlAndSkuParser.parseBinaryStatePayload(rawXml, unescapeEntities = true)
        return Pair(parsed.binaryOn, parsed.brightness)
    }

    /**
     * Subscribes or renews a UPnP GENA event subscription on /upnp/event/basicevent1 for [device].
     * Automatically handles HTTP 412 Precondition Failed by falling back to a fresh SUBSCRIBE.
     */
    suspend fun subscribeOrRenew(
        device: DeviceEntity,
        timeoutSeconds: Int = 300
    ): GenaSubscriptionRecord? = withContext(Dispatchers.IO) {
        val listenPort = startServer()
        if (listenPort <= 0) return@withContext null

        val deviceKey = device.serialNumber.takeIf { it.isNotBlank() } ?: "dev_${device.id}"
        val existing = subscriptionsByDeviceKey[deviceKey]
        val now = System.currentTimeMillis()
        val endpoint = "http://${device.ipAddress}:${device.port}/upnp/event/basicevent1"

        // If we have an active SID for the same IP:port, attempt renewal first
        if (existing != null && existing.sid.isNotBlank() && existing.ipAddress == device.ipAddress && existing.port == device.port) {
            try {
                val resp = httpClient.execute(
                    method = "SUBSCRIBE",
                    url = endpoint,
                    headers = mapOf(
                        "SID" to existing.sid,
                        "TIMEOUT" to "Second-$timeoutSeconds"
                    )
                )
                if (resp.isSuccessful) {
                    val sid = resp.header("SID")?.trim().takeUnless { it.isNullOrEmpty() } ?: existing.sid
                    val updated = existing.copy(
                        sid = sid,
                        expiresAtMs = now + timeoutSeconds * 1000L
                    )
                    subscriptionsByDeviceKey[deviceKey] = updated
                    deviceKeyBySid[sid] = deviceKey
                    return@withContext updated
                }
                // HTTP 412 Precondition Failed or error -> fall through to fresh SUBSCRIBE
            } catch (_: Exception) {
                // Fall through to fresh SUBSCRIBE
            }
        }

        // Fresh SUBSCRIBE with CALLBACK and NT: upnp:event
        val localIp = if (device.ipAddress == "127.0.0.1") "127.0.0.1" else localIpProvider()
        val callbackUrl = "<http://$localIp:$listenPort/gena/$deviceKey>"
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
                val record = GenaSubscriptionRecord(
                    deviceKey = deviceKey,
                    deviceName = device.name,
                    ipAddress = device.ipAddress,
                    port = device.port,
                    sid = sid,
                    subscribedAtMs = now,
                    expiresAtMs = now + timeoutSeconds * 1000L
                )
                subscriptionsByDeviceKey[deviceKey] = record
                deviceKeyBySid[sid] = deviceKey
                return@withContext record
            }
        } catch (_: Exception) {
            // Device offline or does not accept GENA on this port
        }
        null
    }

    /**
     * Unsubscribes an active GENA subscription for [deviceKey].
     */
    suspend fun unsubscribe(deviceKey: String) = withContext(Dispatchers.IO) {
        val record = subscriptionsByDeviceKey.remove(deviceKey) ?: return@withContext
        deviceKeyBySid.remove(record.sid)
        try {
            httpClient.execute(
                method = "UNSUBSCRIBE",
                url = "http://${record.ipAddress}:${record.port}/upnp/event/basicevent1",
                headers = mapOf("SID" to record.sid)
            )
        } catch (_: Exception) {
            // Best effort
        }
    }
}
