package org.suppressingfire.wemi.network

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
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

/**
 * Lightweight zero-dependency local-LAN HTTP/1.1 & UPnP GENA client built directly on [SocketFactory].
 *
 * Designed specifically for local smart-switch UPnP / SOAP / GENA traffic:
 * - Uses zero-argument `socketFactory.createSocket()` + `connect(InetSocketAddress, timeout)` so
 *   Android Wi-Fi bound [SocketFactory] instances bypass mobile data while keeping `127.0.0.1`
 *   loopback tests unbound.
 * - Supports standard HTTP (`GET`, `POST`) and UPnP GENA (`SUBSCRIBE`, `UNSUBSCRIBE`) methods.
 * - Frames responses strictly by byte-exact `Content-Length` (or `Transfer-Encoding: chunked`)
 *   before falling back to EOF, avoiding 1000ms+ hangs on embedded switch HTTP daemons that delay TCP `FIN`.
 */
internal class LanHttpClient(
    private val socketFactory: SocketFactory = SocketFactory.getDefault(),
    private val connectTimeoutMs: Int = 750,
    private val readTimeoutMs: Int = 1200
) {
    companion object {
        private const val MAX_RESPONSE_BODY_BYTES = 512 * 1024
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
        val uri = URI(url)
        val host = requireNotNull(uri.host?.takeIf { it.isNotBlank() }) {
            "Missing host in URL: $url"
        }
        val port = if (uri.port > 0) uri.port else 80
        val rawPath = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val requestTarget = if (!uri.rawQuery.isNullOrEmpty()) {
            "$rawPath?${uri.rawQuery}"
        } else {
            rawPath
        }

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val requestHeaderText = buildString {
            append("$method $requestTarget HTTP/1.1\r\n")
            if (headers.keys.none { it.equals("Host", ignoreCase = true) }) {
                append("Host: $host:$port\r\n")
            }
            for ((k, v) in headers) {
                append("$k: $v\r\n")
            }
            if (bodyBytes != null && headers.keys.none { it.equals("Content-Length", ignoreCase = true) }) {
                append("Content-Length: ${bodyBytes.size}\r\n")
            }
            if (headers.keys.none { it.equals("Connection", ignoreCase = true) }) {
                append("Connection: close\r\n")
            }
            append("\r\n")
        }

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

            val input = BufferedInputStream(socket.getInputStream(), 8192)
            val statusLine = readHeaderLine(input)
                ?: throw EOFException("Empty HTTP response from $host:$port")
            val statusParts = statusLine.split(' ', limit = 3)
            val statusCode = statusParts.getOrNull(1)?.toIntOrNull()
                ?: throw EOFException("Malformed HTTP status line: $statusLine")
            val statusMessage = statusParts.getOrNull(2).orEmpty()

            val responseHeaders = LinkedHashMap<String, String>()
            while (true) {
                val line = readHeaderLine(input) ?: break
                if (line.isEmpty()) break
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
                    readChunkedBody(input)
                responseHeaders.containsKey("content-length") -> {
                    val contentLength = responseHeaders["content-length"]
                        ?.toIntOrNull()
                        ?.coerceIn(0, MAX_RESPONSE_BODY_BYTES) ?: 0
                    readExactBytes(input, contentLength)
                }
                else -> readUntilEof(input)
            }

            LanHttpResponse(
                code = statusCode,
                statusMessage = statusMessage,
                headers = responseHeaders,
                body = String(responseBodyBytes, Charsets.UTF_8)
            )
        }
    }

    private fun readHeaderLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream(128)
        var sawAnyByte = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sawAnyByte) buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r') else null
            }
            sawAnyByte = true
            if (b == '\n'.code) {
                break
            }
            if (buf.size() < 8192) {
                buf.write(b)
            }
        }
        return buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(out, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) out else out.copyOf(offset)
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readHeaderLine(input)?.trim() ?: break
            if (sizeLine.isEmpty()) continue
            val chunkSize = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (chunkSize <= 0) {
                while (true) {
                    val trailer = readHeaderLine(input) ?: break
                    if (trailer.isEmpty()) break
                }
                break
            }
            val cappedSize = chunkSize.coerceAtMost(MAX_RESPONSE_BODY_BYTES - out.size())
            if (cappedSize > 0) {
                val chunk = readExactBytes(input, cappedSize)
                out.write(chunk)
            }
            if (chunkSize > cappedSize) {
                break
            }
            readHeaderLine(input)
        }
        return out.toByteArray()
    }

    private fun readUntilEof(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (out.size() < MAX_RESPONSE_BODY_BYTES) {
            val n = try {
                input.read(buf, 0, minOf(buf.size, MAX_RESPONSE_BODY_BYTES - out.size()))
            } catch (timeout: java.net.SocketTimeoutException) {
                if (out.size() > 0) break else throw timeout
            }
            if (n == -1) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
