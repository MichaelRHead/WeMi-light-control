package org.suppressingfire.wemi.network

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Hardened bounded HTTP/1.1 request reader and JSON responder for [HouseholdSyncLanServer].
 *
 * Enforces strict slowloris wall-clock deadlines (clamping per-read `soTimeout` to remaining ms),
 * rejects duplicate `Content-Length` or `Transfer-Encoding` smuggling headers (`400 Bad Request`),
 * returns `431` / `408` instead of silently truncating headers, and streams request bodies in
 * 8 KiB chunks instead of eagerly pre-allocating untrusted `Content-Length` buffers.
 */
internal object HouseholdSyncHttpFraming {
    private const val STREAM_CHUNK_BYTES = 8 * 1024

    sealed class ReadResult {
        data class Ok(
            val method: String,
            val path: String,
            val bodyBytes: ByteArray
        ) : ReadResult()

        data class Error(
            val httpStatus: String,
            val errorCode: String
        ) : ReadResult()
    }

    private sealed class HeaderLineResult {
        data class Line(val value: String) : HeaderLineResult()
        data object Timeout : HeaderLineResult()
        data object TooLong : HeaderLineResult()
        data object Eof : HeaderLineResult()
    }

    fun readRequest(
        sock: Socket,
        deadlineNs: Long,
        maxHeaders: Int,
        maxHeaderLineBytes: Int,
        maxBodyBytes: Int,
        allowedPaths: Set<String>,
        bodyOptionalPaths: Set<String>
    ): ReadResult {
        val input = BufferedInputStream(sock.getInputStream())
        val requestLine = when (val first = readHeaderLine(sock, input, deadlineNs, maxHeaderLineBytes)) {
            is HeaderLineResult.Line -> first.value
            HeaderLineResult.TooLong -> return ReadResult.Error("431 Request Header Fields Too Large", "header_too_large")
            HeaderLineResult.Timeout -> return ReadResult.Error("408 Request Timeout", "timeout")
            HeaderLineResult.Eof -> return ReadResult.Error("400 Bad Request", "empty_request")
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2) {
            return ReadResult.Error("400 Bad Request", "malformed_request_line")
        }
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        if (!method.equals("POST", ignoreCase = true)) {
            return ReadResult.Error("405 Method Not Allowed", "method_not_allowed")
        }
        if (path !in allowedPaths) {
            return ReadResult.Error("404 Not Found", "not_found")
        }

        var contentLength: Int? = null
        var headerCount = 0
        while (true) {
            when (val lineResult = readHeaderLine(sock, input, deadlineNs, maxHeaderLineBytes)) {
                HeaderLineResult.Timeout -> return ReadResult.Error("408 Request Timeout", "timeout")
                HeaderLineResult.TooLong -> return ReadResult.Error("431 Request Header Fields Too Large", "header_too_large")
                HeaderLineResult.Eof -> return ReadResult.Error("400 Bad Request", "truncated_headers")
                is HeaderLineResult.Line -> {
                    val line = lineResult.value
                    if (line.isEmpty()) break
                    headerCount++
                    if (headerCount > maxHeaders) {
                        return ReadResult.Error("431 Request Header Fields Too Large", "too_many_headers")
                    }
                    val colon = line.indexOf(':')
                    if (colon <= 0) {
                        return ReadResult.Error("400 Bad Request", "malformed_header")
                    }
                    val key = line.substring(0, colon).trim().uppercase(Locale.US)
                    val value = line.substring(colon + 1).trim()
                    when (key) {
                        "TRANSFER-ENCODING" -> {
                            return ReadResult.Error("400 Bad Request", "transfer_encoding_not_supported")
                        }
                        "CONTENT-LENGTH" -> {
                            if (contentLength != null) {
                                return ReadResult.Error("400 Bad Request", "duplicate_content_length")
                            }
                            val parsed = value.toIntOrNull()
                                ?: return ReadResult.Error("400 Bad Request", "invalid_content_length")
                            if (parsed < 0) {
                                return ReadResult.Error("400 Bad Request", "invalid_content_length")
                            }
                            contentLength = parsed
                        }
                    }
                }
            }
        }

        val expectedLen = contentLength ?: if (path in bodyOptionalPaths) 0 else -1
        if (expectedLen < 0 || (expectedLen == 0 && path !in bodyOptionalPaths)) {
            return ReadResult.Error("413 Payload Too Large", "invalid_length")
        }
        if (expectedLen > maxBodyBytes) {
            return ReadResult.Error("413 Payload Too Large", "payload_too_large")
        }
        if (expectedLen == 0) {
            return ReadResult.Ok(method = method, path = path, bodyBytes = ByteArray(0))
        }

        val bodyBytes = readStreamedExactBody(sock, input, expectedLen, deadlineNs)
            ?: return ReadResult.Error("408 Request Timeout", "incomplete_body")
        return ReadResult.Ok(method = method, path = path, bodyBytes = bodyBytes)
    }

    fun sendJsonResponse(sock: Socket, status: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.US_ASCII)
        writer.write("HTTP/1.1 $status\r\n")
        writer.write("Content-Type: application/json; charset=utf-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Cache-Control: no-store\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.flush()
        sock.getOutputStream().write(bytes)
        sock.getOutputStream().flush()
    }

    private fun readHeaderLine(
        sock: Socket,
        input: BufferedInputStream,
        deadlineNs: Long,
        maxHeaderLineBytes: Int
    ): HeaderLineResult {
        val baos = ByteArrayOutputStream()
        while (true) {
            val remainingMs = remainingDeadlineMs(deadlineNs)
            if (remainingMs <= 0L) return HeaderLineResult.Timeout
            sock.soTimeout = remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val b = try {
                input.read()
            } catch (_: java.net.SocketTimeoutException) {
                return HeaderLineResult.Timeout
            }
            if (b == -1) return HeaderLineResult.Eof
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                if (baos.size() >= maxHeaderLineBytes) return HeaderLineResult.TooLong
                baos.write(b)
            }
        }
        return HeaderLineResult.Line(baos.toString("ISO-8859-1"))
    }

    private fun readStreamedExactBody(
        sock: Socket,
        input: BufferedInputStream,
        expectedLength: Int,
        deadlineNs: Long
    ): ByteArray? {
        val out = ByteArrayOutputStream(expectedLength.coerceAtMost(STREAM_CHUNK_BYTES))
        val chunk = ByteArray(STREAM_CHUNK_BYTES)
        var remaining = expectedLength
        while (remaining > 0) {
            val remainingMs = remainingDeadlineMs(deadlineNs)
            if (remainingMs <= 0L) return null
            sock.soTimeout = remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val toRead = remaining.coerceAtMost(chunk.size)
            val n = try {
                input.read(chunk, 0, toRead)
            } catch (_: java.net.SocketTimeoutException) {
                return null
            }
            if (n <= 0) return null
            out.write(chunk, 0, n)
            remaining -= n
        }
        return out.toByteArray()
    }

    private fun remainingDeadlineMs(deadlineNs: Long): Long {
        val remainingNs = deadlineNs - System.nanoTime()
        return if (remainingNs <= 0L) 0L else ((remainingNs + 999_999L) / 1_000_000L)
    }

    data class UdpBeaconHandle(
        val localPort: Int,
        val socket: java.net.DatagramSocket,
        val multicastLock: android.net.wifi.WifiManager.MulticastLock?,
        val job: kotlinx.coroutines.Job
    ) {
        fun close() {
            job.cancel()
            runCatching { socket.close() }
            runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
        }
    }

    fun startUdpDiscoveryBeacon(
        scope: kotlinx.coroutines.CoroutineScope,
        appContext: android.content.Context?,
        discoveryUdpPort: Int,
        tcpPort: Int,
        label: String,
        isTv: Boolean,
        localIp: String,
        allowLoopbackForTests: Boolean,
        isStillAccepting: () -> Boolean
    ): UdpBeaconHandle? {
        if (discoveryUdpPort < 0) return null
        var createdSocket: java.net.DatagramSocket? = null
        var createdLock: android.net.wifi.WifiManager.MulticastLock? = null
        return try {
            val socket: java.net.DatagramSocket = if (discoveryUdpPort == 0) {
                java.net.DatagramSocket(0)
            } else {
                val ms = java.net.MulticastSocket(null)
                createdSocket = ms
                ms.reuseAddress = true
                ms.bind(java.net.InetSocketAddress(discoveryUdpPort))
                val activeBinding = if (allowLoopbackForTests) null else NetworkMonitor.resolveActiveLanBinding(appContext)
                if (!allowLoopbackForTests) {
                    activeBinding?.network?.let { net -> runCatching { net.bindSocket(ms) } }
                }
                runCatching {
                    val wifiMgr = appContext?.applicationContext?.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    createdLock = wifiMgr?.createMulticastLock("WeMiHouseholdSyncLock")?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
                val targetIf = activeBinding?.interfaceName
                    ?.let { name -> runCatching { java.net.NetworkInterface.getByName(name) }.getOrNull() }
                    ?: runCatching {
                        if (LanRuntimeUtils.isPrivateRfc1918Ipv4(localIp)) {
                            java.net.NetworkInterface.getByInetAddress(java.net.InetAddress.getByName(localIp))
                        } else {
                            null
                        }
                    }.getOrNull()
                val groupAddr = java.net.InetSocketAddress(
                    java.net.InetAddress.getByName(HouseholdSyncLanServer.DISCOVERY_MULTICAST_GROUP),
                    discoveryUdpPort
                )
                runCatching { ms.joinGroup(groupAddr, targetIf) }
                ms
            }
            createdSocket = socket
            socket.soTimeout = 600
            val replyPayload = buildString {
                append(HouseholdSyncLanServer.DISCOVERY_REPLY_PREFIX)
                append(" port=").append(tcpPort)
                append(" role=").append(if (isTv) "TV" else "DEVICE")
                append(" name=").append(label)
            }.toByteArray(Charsets.UTF_8)

            val job = scope.launch {
                val buf = ByteArray(512)
                var windowStartMs = System.currentTimeMillis()
                var repliesInWindow = 0
                var totalReplies = 0
                while (isStillAccepting() && !socket.isClosed) {
                    try {
                        val packet = java.net.DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val senderIp = packet.address?.hostAddress.orEmpty()
                        if (packet.port < 1024 ||
                            !LanEndpointPolicy.isAllowedLanHost(senderIp, allowLoopback = allowLoopbackForTests)
                        ) {
                            continue
                        }
                        val msg = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                        if (!msg.startsWith(HouseholdSyncLanServer.DISCOVERY_PROBE_PREFIX)) continue
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - windowStartMs >= 1_000L) {
                            windowStartMs = nowMs
                            repliesInWindow = 0
                        }
                        if (repliesInWindow >= 10 || totalReplies >= 120) continue
                        repliesInWindow++
                        totalReplies++
                        val reply = java.net.DatagramPacket(replyPayload, replyPayload.size, packet.address, packet.port)
                        runCatching { socket.send(reply) }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Poll loop tick so cancellation stops promptly
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (!isStillAccepting()) break
                    }
                }
            }
            UdpBeaconHandle(socket.localPort, socket, createdLock, job)
        } catch (_: Exception) {
            runCatching { createdSocket?.close() }
            runCatching { if (createdLock?.isHeld == true) createdLock?.release() }
            null
        }
    }
}
