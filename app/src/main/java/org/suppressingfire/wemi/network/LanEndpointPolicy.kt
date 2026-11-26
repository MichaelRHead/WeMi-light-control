package org.suppressingfire.wemi.network

import java.net.URI
import java.util.Locale

/**
 * Parsed and validated local-LAN HTTP target produced by [LanEndpointPolicy.validateLanUrl].
 */
internal data class ValidatedLanUrl(
    val host: String,
    val port: Int,
    val requestTarget: String
)

/**
 * Centralized local-LAN network policy enforcing:
 * 1. Strict `http://` + literal RFC 1918 IPv4 allowlist (`10.0.0.0/8`, `172.16.0.0/12`,
 *    `192.168.0.0/16` — including the `10.22.22.1` factory Setup AP), allowing `127.0.0.1`
 *    strictly in local JVM Robolectric test runs (or when explicitly enabled by a test).
 *    Rejects DNS hostnames, public IPs, `169.254.0.0/16` link-local/metadata, `0.0.0.0/8`,
 *    IPv6 literals, non-HTTP schemes, and URL userinfo (`user@host`).
 * 2. Strict CR/LF/NUL rejection for HTTP methods, request targets, headers, and SSDP `ST` tokens.
 * 3. Safe hardware identifier sanitization (`serialNumber`, `UDN`, GENA `deviceKey`).
 */
object LanEndpointPolicy {

    private val HTTP_METHOD_REGEX = Regex("^[A-Z]{2,24}$")
    private val HEADER_NAME_REGEX = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$")
    private val SAFE_HW_ID_CHAR_REGEX = Regex("[^A-Za-z0-9._:-]")

    /**
     * Parses a strict dotted-decimal IPv4 literal (`a.b.c.d`) without octal/leading-zero tricks.
     * Returns `null` for hostnames, IPv6, CIDR suffixes, or malformed addresses.
     */
    internal fun parseStrictIpv4Literal(rawHost: String?): IntArray? {
        val host = rawHost?.trim().orEmpty()
        if (host.isEmpty() || host.length > 15) return null
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0 until 4) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3) return null
            if (part.length > 1 && part[0] == '0') return null
            var value = 0
            for (ch in part) {
                if (ch !in '0'..'9') return null
                value = value * 10 + (ch - '0')
            }
            if (value !in 0..255) return null
            octets[i] = value
        }
        return octets
    }

    /**
     * Returns `true` when [host] is a literal RFC 1918 IPv4 address (`10.0.0.0/8`, `172.16.0.0/12`,
     * or `192.168.0.0/16`), or `127.0.0.1` when [allowLoopback] is enabled.
     */
    fun isAllowedLanHost(
        host: String?,
        allowLoopback: Boolean = LanRuntimeUtils.isRunningInRobolectric()
    ): Boolean {
        val octets = parseStrictIpv4Literal(host) ?: return false
        val a = octets[0]
        val b = octets[1]
        val c = octets[2]
        val d = octets[3]
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            allowLoopback && a == 127 && b == 0 && c == 0 && d == 1 -> true
            else -> false
        }
    }

    /**
     * Returns `true` when [url] is a valid `http://` URL targeting an allowed local-LAN IPv4 host
     * with no CR/LF/NUL characters or userinfo.
     */
    fun isAllowedLanUrl(
        url: String?,
        allowLoopback: Boolean = LanRuntimeUtils.isRunningInRobolectric()
    ): Boolean = runCatching {
        validateLanUrl(url.orEmpty(), allowLoopback = allowLoopback)
        true
    }.getOrDefault(false)

    /**
     * Validates [url] against the local-LAN SSRF and CRLF policy and returns its parsed
     * [ValidatedLanUrl], throwing [IllegalArgumentException] on any violation.
     */
    internal fun validateLanUrl(
        url: String,
        allowLoopback: Boolean = LanRuntimeUtils.isRunningInRobolectric()
    ): ValidatedLanUrl {
        require(url.isNotBlank()) { "Blank URL" }
        requireNoCrlfOrNul(url, "URL")
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed URL: ${e.message}", e)
        }
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        require(scheme == "http") { "Only local-LAN http:// URLs are permitted (got '$scheme')" }
        require(uri.rawUserInfo == null) { "URL userinfo is not permitted" }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing host in URL: $url")
        require(isAllowedLanHost(host, allowLoopback = allowLoopback)) {
            "Blocked non-LAN HTTP destination host: $host"
        }

        val port = when {
            uri.port == -1 -> 80
            uri.port in 1..65535 -> uri.port
            else -> throw IllegalArgumentException("Invalid TCP port in URL: ${uri.port}")
        }

        val rawPath = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val requestTarget = if (!uri.rawQuery.isNullOrEmpty()) {
            "$rawPath?${uri.rawQuery}"
        } else {
            rawPath
        }
        requireNoCrlfOrNul(requestTarget, "HTTP request target")
        require(requestTarget.none { it == ' ' || it == '\t' }) {
            "Whitespace is not permitted in HTTP request target"
        }

        return ValidatedLanUrl(host = host, port = port, requestTarget = requestTarget)
    }

    /**
     * Parses `(host, port)` from an HTTP `LOCATION` URL (supporting both explicit ports like
     * `http://192.168.1.50:49153/setup.xml` and default port `80` when omitted).
     */
    fun parseHostAndPortOrNull(
        url: String?,
        defaultPort: Int = 80
    ): Pair<String, Int>? {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty() || containsCrlfOrNul(clean)) return null
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        if (!uri.scheme.equals("http", ignoreCase = true)) return null
        val host = uri.host?.takeIf { parseStrictIpv4Literal(it) != null } ?: return null
        val port = if (uri.port in 1..65535) uri.port else defaultPort
        return host to port
    }

    fun containsCrlfOrNul(value: String): Boolean =
        value.any { it == '\r' || it == '\n' || it == '\u0000' }

    fun requireNoCrlfOrNul(value: String, fieldName: String) {
        require(!containsCrlfOrNul(value)) { "CR/LF/NUL characters are not permitted in $fieldName" }
    }

    fun validateHttpMethod(method: String): String {
        val clean = method.trim().uppercase(Locale.US)
        require(HTTP_METHOD_REGEX.matches(clean)) { "Invalid HTTP method: $method" }
        return clean
    }

    fun validateHeaderName(name: String): String {
        require(name.isNotEmpty() && HEADER_NAME_REGEX.matches(name)) {
            "Invalid HTTP header name"
        }
        return name
    }

    fun validateHeaderValue(value: String): String {
        requireNoCrlfOrNul(value, "HTTP header value")
        return value
    }

    fun sanitizeSingleLineToken(raw: String, maxLen: Int = 160): String =
        raw.substringBefore('\r')
            .substringBefore('\n')
            .substringBefore('\u0000')
            .trim()
            .take(maxLen)

    /**
     * Sanitizes untrusted UPnP hardware identifiers (`serialNumber`, `UDN`, GENA `deviceKey`)
     * to strict URI/path-safe characters (`[A-Za-z0-9._:-]`), blocking path traversal and CRLF.
     */
    fun sanitizeHardwareIdentifier(raw: String?, maxLen: Int = 96): String {
        val cleaned = raw?.trim().orEmpty()
            .replace(SAFE_HW_ID_CHAR_REGEX, "")
            .take(maxLen)
        return if (cleaned == "." || cleaned == "..") "" else cleaned
    }
}
