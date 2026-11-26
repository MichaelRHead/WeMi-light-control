package org.suppressingfire.wemi.network

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.Locale
import javax.net.SocketFactory

internal data class LanHttpResponse(
    val code: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val body: String
) {
    val isSuccessful: Boolean
        get() = code in 200..299

    fun header(name: String): String? =
        headers[name.lowercase(Locale.US)]
}

internal class LanHttpBinaryResponse(
    val code: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val bodyBytes: ByteArray
) {
    val isSuccessful: Boolean
        get() = code in 200..299

    fun header(name: String): String? =
        headers[name.lowercase(Locale.US)]
}

/**
 * Lightweight zero-dependency local-LAN HTTP/1.1 & UPnP GENA client built directly on [SocketFactory].
 *
 * Enforces [LanEndpointPolicy] (`http://` + literal RFC 1918 IPv4 allowlist, strict CR/LF/NUL
 * rejection on methods, targets, and headers) alongside bounded header counts, total header byte
 * budgets, wall-clock response deadlines, and strict response-size caps.
 */
internal class LanHttpClient(
    private val socketFactory: SocketFactory = SocketFactory.getDefault(),
    private val connectTimeoutMs: Int = 750,
    private val readTimeoutMs: Int = 1200,
    private val allowLoopbackForTests: Boolean = LanRuntimeUtils.isRunningInRobolectric()
) {
    companion object {
        private const val MAX_TEXT_RESPONSE_BODY_BYTES = 512 * 1024
        internal const val MAX_BINARY_RESPONSE_BODY_BYTES = 2 * 1024 * 1024
        private const val MAX_RESPONSE_HEADERS = 64
        private const val MAX_HEADER_LINE_BYTES = 8192
        private const val MAX_TOTAL_HEADER_BYTES = 16 * 1024
    }

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): LanHttpResponse = execute(
        method = "GET",
        url = url,
        headers = headers,
        body = null
    )

    fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = MAX_BINARY_RESPONSE_BODY_BYTES
    ): LanHttpBinaryResponse = executeRaw(
        method = "GET",
        url = url,
        headers = headers,
        body = null,
        maxResponseBytes = maxBytes.coerceIn(1024, MAX_BINARY_RESPONSE_BODY_BYTES)
    )

    fun postXml(
        url: String,
        soapBody: String,
        soapAction: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): LanHttpResponse {
        val merged = LinkedHashMap<String, String>()
        if (soapAction != null) {
            merged["SOAPACTION"] = soapAction
        }
        merged["Content-Type"] = "text/xml; charset=\"utf-8\""
        merged.putAll(extraHeaders)
        return execute(
            method = "POST",
            url = url,
            headers = merged,
            body = soapBody
        )
    }

    fun execute(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): LanHttpResponse {
        val raw = executeRaw(
            method = method,
            url = url,
            headers = headers,
            body = body,
            maxResponseBytes = MAX_TEXT_RESPONSE_BODY_BYTES
        )
        return LanHttpResponse(
            code = raw.code,
            statusMessage = raw.statusMessage,
            headers = raw.headers,
            body = String(raw.bodyBytes, Charsets.UTF_8)
        )
    }

    private fun checkDeadline(deadlineNs: Long) {
        if (System.nanoTime() > deadlineNs) {
            throw SocketTimeoutException("HTTP response exceeded overall wall-clock budget")
        }
    }

    private fun executeRaw(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        maxResponseBytes: Int
    ): LanHttpBinaryResponse {
        val safeMethod = LanEndpointPolicy.validateHttpMethod(method)
        val validated = LanEndpointPolicy.validateLanUrl(url, allowLoopback = allowLoopbackForTests)
        val host = validated.host
        val port = validated.port
        val requestTarget = validated.requestTarget

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val requestHeaderText = buildString {
            append("$safeMethod $requestTarget HTTP/1.1\r\n")
            if (headers.keys.none { it.equals("Host", ignoreCase = true) }) {
                append("Host: $host:$port\r\n")
            }
            for ((rawName, rawValue) in headers) {
                val safeName = LanEndpointPolicy.validateHeaderName(rawName)
                val safeValue = LanEndpointPolicy.validateHeaderValue(rawValue)
                append("$safeName: $safeValue\r\n")
            }
            if (bodyBytes != null && headers.keys.none { it.equals("Content-Length", ignoreCase = true) }) {
                append("Content-Length: ${bodyBytes.size}\r\n")
            }
            if (headers.keys.none { it.equals("Connection", ignoreCase = true) }) {
                append("Connection: close\r\n")
            }
            append("\r\n")
        }

        val totalBudgetMs = maxOf(readTimeoutMs * 4L, 5_000L)
        return socketFactory.createSocket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs

            val out = socket.getOutputStream()
            out.write(requestHeaderText.toByteArray(Charsets.UTF_8))
            if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                out.write(bodyBytes)
            }
            out.flush()

            val deadlineNs = System.nanoTime() + totalBudgetMs * 1_000_000L
            val input = BufferedInputStream(socket.getInputStream(), 8192)
            val statusLine = readHeaderLine(input, deadlineNs)
                ?: throw EOFException("Empty HTTP response from $host:$port")
            val statusParts = statusLine.split(' ', limit = 3)
            val statusCode = statusParts.getOrNull(1)?.toIntOrNull()
                ?: throw EOFException("Malformed HTTP status line: $statusLine")
            val statusMessage = statusParts.getOrNull(2).orEmpty()

            val responseHeaders = LinkedHashMap<String, String>()
            var headerCount = 0
            var totalHeaderBytes = statusLine.length
            while (true) {
                checkDeadline(deadlineNs)
                val line = readHeaderLine(input, deadlineNs) ?: break
                if (line.isEmpty()) break
                headerCount++
                totalHeaderBytes += line.length + 2
                if (headerCount > MAX_RESPONSE_HEADERS || totalHeaderBytes > MAX_TOTAL_HEADER_BYTES) {
                    throw IOException("HTTP response headers exceeded safe limits ($headerCount headers, $totalHeaderBytes bytes)")
                }
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase(Locale.US)
                    val value = line.substring(colonIdx + 1).trim()
                    responseHeaders[key] = value
                }
            }

            val responseBodyBytes = when {
                statusCode in 100..199 || statusCode == 204 || statusCode == 304 -> ByteArray(0)
                responseHeaders["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true ->
                    readChunkedBody(input, maxResponseBytes, deadlineNs)
                responseHeaders.containsKey("content-length") -> {
                    val rawLen = responseHeaders["content-length"]?.toLongOrNull()
                        ?: throw IOException("Malformed HTTP Content-Length header")
                    if (rawLen < 0L || rawLen > maxResponseBytes.toLong()) {
                        throw IOException("HTTP response Content-Length ($rawLen) exceeds safe limit ($maxResponseBytes)")
                    }
                    readExactBytes(input, rawLen.toInt(), deadlineNs)
                }
                else -> readUntilEof(input, maxResponseBytes, deadlineNs)
            }

            LanHttpBinaryResponse(
                code = statusCode,
                statusMessage = statusMessage,
                headers = responseHeaders,
                bodyBytes = responseBodyBytes
            )
        }
    }

    private fun readHeaderLine(input: InputStream, deadlineNs: Long): String? {
        val buf = ByteArrayOutputStream(128)
        var sawAnyByte = false
        while (true) {
            checkDeadline(deadlineNs)
            val b = input.read()
            if (b == -1) {
                return if (sawAnyByte) buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r') else null
            }
            sawAnyByte = true
            if (b == '\n'.code) {
                break
            }
            if (buf.size() >= MAX_HEADER_LINE_BYTES) {
                throw IOException("HTTP header line exceeded $MAX_HEADER_LINE_BYTES byte limit")
            }
            buf.write(b)
        }
        return buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
    }

    private fun readExactBytes(input: InputStream, length: Int, deadlineNs: Long): ByteArray {
        if (length <= 0) return ByteArray(0)
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            checkDeadline(deadlineNs)
            val read = input.read(out, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        if (offset < length) {
            throw EOFException("Truncated HTTP response body: expected $length bytes, received $offset")
        }
        return out
    }

    private fun readChunkedBody(input: InputStream, maxResponseBytes: Int, deadlineNs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var trailerCount = 0
        while (true) {
            checkDeadline(deadlineNs)
            val sizeLine = readHeaderLine(input, deadlineNs)?.trim() ?: break
            if (sizeLine.isEmpty()) continue
            val chunkSize = sizeLine.substringBefore(';').trim().toIntOrNull(16)
                ?: throw IOException("Malformed HTTP chunk size: $sizeLine")
            if (chunkSize < 0) throw IOException("Negative HTTP chunk size")
            if (chunkSize == 0) {
                while (true) {
                    val trailer = readHeaderLine(input, deadlineNs) ?: break
                    if (trailer.isEmpty()) break
                    trailerCount++
                    if (trailerCount > MAX_RESPONSE_HEADERS) {
                        throw IOException("HTTP chunk trailers exceeded safe limit")
                    }
                }
                break
            }
            if (chunkSize > maxResponseBytes - out.size()) {
                throw IOException("Chunked HTTP response exceeded $maxResponseBytes byte limit")
            }
            val chunk = readExactBytes(input, chunkSize, deadlineNs)
            out.write(chunk)
            readHeaderLine(input, deadlineNs)
        }
        return out.toByteArray()
    }

    private fun readUntilEof(input: InputStream, maxResponseBytes: Int, deadlineNs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            checkDeadline(deadlineNs)
            val n = try {
                input.read(buf, 0, buf.size)
            } catch (timeout: SocketTimeoutException) {
                if (out.size() > 0) break else throw timeout
            }
            if (n == -1) break
            if (out.size() + n > maxResponseBytes) {
                throw IOException("HTTP response exceeded $maxResponseBytes byte limit")
            }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
