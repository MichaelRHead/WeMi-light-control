package com.example.network

import com.example.data.SoapLogEntry
import com.example.data.WeMoDeviceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
 * Implements Belkin WeMo UPnP GENA (General Event Notification Architecture) local push
 * subscriptions matching pywemo/subscribe.py:
 * - Runs a lightweight embedded HTTP NOTIFY server (port 8989 or ephemeral fallback) on the local LAN.
 * - Subscribes to /upnp/event/basicevent1 with TIMEOUT: Second-300.
 * - Automatically recovers from HTTP 412 Precondition Failed on renewal by issuing a fresh SUBSCRIBE.
 * - Parses <e:propertyset> XML payloads for <BinaryState> and <Brightness>/<brightness> changes
 *   pushed when a physical wall paddle, 3-way traveler, or dimmer bar is flipped.
 */
class WeMoGenaSubscriptionManager(
    private val localIpProvider: () -> String,
    private val preferredListenPort: Int = 8989,
    wifiSocketFactory: SocketFactory = SocketFactory.getDefault(),
    private val onNotification: (GenaEventNotification) -> Unit
) {
    private val httpClient = OkHttpClient.Builder()
        .socketFactory(wifiSocketFactory)
        .connectTimeout(900, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .writeTimeout(900, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

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
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            if (!requestLine.startsWith("NOTIFY", ignoreCase = true)) {
                sendHttp200(sock)
                return
            }

            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            val pathDeviceKey = path.substringAfterLast("/gena/", "").trim()

            var contentLength = 0
            var sidHeader = ""
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim().uppercase(Locale.US)
                    val v = line.substring(colon + 1).trim()
                    when (k) {
                        "CONTENT-LENGTH" -> contentLength = v.toIntOrNull() ?: 0
                        "SID" -> sidHeader = v
                    }
                }
            }

            val xmlBody = if (contentLength > 0) {
                val buf = CharArray(contentLength.coerceAtMost(16384))
                var readTotal = 0
                while (readTotal < buf.size) {
                    val n = reader.read(buf, readTotal, buf.size - readTotal)
                    if (n == -1) break
                    readTotal += n
                }
                String(buf, 0, readTotal)
            } else {
                buildString {
                    while (reader.ready()) {
                        val line = reader.readLine() ?: break
                        appendLine(line)
                    }
                }
            }

            // Always reply HTTP 200 OK immediately so WeMo RTOS socket is freed
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
        val unescaped = rawXml
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")

        val rawState = Regex("<BinaryState>([^<]+)</BinaryState>", RegexOption.IGNORE_CASE)
            .find(unescaped)?.groupValues?.getOrNull(1)?.trim()

        val parts = rawState?.split("|").orEmpty()
        val firstToken = parts.firstOrNull()?.trim()
        val binaryOn: Boolean? = when (firstToken) {
            "1", "8" -> true
            "0" -> false
            else -> null
        }

        val brightness: Int? = parts.getOrNull(1)?.trim()?.toIntOrNull()
            ?: Regex("<Brightness>(\\d+)</Brightness>", RegexOption.IGNORE_CASE)
                .find(unescaped)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("<brightness>(\\d+)</brightness>", RegexOption.IGNORE_CASE)
                .find(unescaped)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return Pair(binaryOn, brightness)
    }

    /**
     * Subscribes or renews a UPnP GENA event subscription on /upnp/event/basicevent1 for [device].
     * Automatically handles HTTP 412 Precondition Failed by falling back to a fresh SUBSCRIBE.
     */
    suspend fun subscribeOrRenew(
        device: WeMoDeviceEntity,
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
                val renewReq = Request.Builder()
                    .url(endpoint)
                    .method("SUBSCRIBE", "".toRequestBody(null))
                    .header("SID", existing.sid)
                    .header("TIMEOUT", "Second-$timeoutSeconds")
                    .header("Connection", "close")
                    .build()
                httpClient.newCall(renewReq).execute().use { resp ->
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
                }
            } catch (_: Exception) {
                // Fall through to fresh SUBSCRIBE
            }
        }

        // Fresh SUBSCRIBE with CALLBACK and NT: upnp:event
        val localIp = if (device.ipAddress == "127.0.0.1") "127.0.0.1" else localIpProvider()
        val callbackUrl = "<http://$localIp:$listenPort/gena/$deviceKey>"
        try {
            val subReq = Request.Builder()
                .url(endpoint)
                .method("SUBSCRIBE", "".toRequestBody(null))
                .header("CALLBACK", callbackUrl)
                .header("NT", "upnp:event")
                .header("TIMEOUT", "Second-$timeoutSeconds")
                .header("Connection", "close")
                .build()
            httpClient.newCall(subReq).execute().use { resp ->
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
            val req = Request.Builder()
                .url("http://${record.ipAddress}:${record.port}/upnp/event/basicevent1")
                .method("UNSUBSCRIBE", "".toRequestBody(null))
                .header("SID", record.sid)
                .header("Connection", "close")
                .build()
            httpClient.newCall(req).execute().close()
        } catch (_: Exception) {
            // Best effort
        }
    }
}
