package com.example.network

import android.content.Context
import com.example.data.SoapLogEntry
import com.example.data.WeMoDeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class WeMoScannedAp(
    val ssid: String,
    val channel: Int,
    val signalStrengthPercent: Int,
    val authMode: String,
    val encryptionType: String
)

data class WeMoSetupDeviceDescriptor(
    val host: String,
    val port: Int,
    val friendlyName: String,
    val modelName: String,
    val hardwareSku: String,
    val deviceType: WeMoDeviceType,
    val serialNumber: String,
    val macAddress: String,
    val udn: String,
    val firmwareVersion: String,
    val isRtos: Boolean,
    val isIot: Boolean,
    val binaryOption: Boolean,
    val recommendedEncryptionMethod: Int,
    val recommendedAddLengths: Boolean
)

data class WeMoProvisionResult(
    val success: Boolean,
    val networkStatusCode: Int,
    val statusMessage: String,
    val encryptedPasswordPayload: String,
    val logEntry: SoapLogEntry,
    val closeSetupStatus: String = "",
    val effectiveEncryptionMethod: Int = 1,
    val addedLengthSuffix: Boolean = true,
    val setupDevice: WeMoSetupDeviceDescriptor? = null
)

/**
 * Zero-cloud Belkin WeMo Wi-Fi setup provisioner (10.22.22.1) matching
 * pywemo/ouimeaux_device/__init__.py and pywemo/ouimeaux_device/api/wifi_setup.py.
 *
 * Supports:
 * - Fast 9-port setup descriptor probing (/setup.xml and /Lightsetup.xml) over Wi-Fi-bound sockets
 * - Auto-detection of encryption method (1, 2, or 3) from <rtos>, <iot>, and <binaryOption> tags
 * - Scanning nearby 2.4 GHz Wi-Fi networks via WiFiSetup1#GetApList (with GetNetworkList fallback)
 * - Exact pywemo AES-128-CBC password encryption (Methods 1, 2, and 3)
 * - Optional hardware FriendlyName configuration during setup (basicevent1#ChangeFriendlyName)
 * - Double-send ConnectHomeNetwork (100ms apart), multi-poll GetNetworkStatus (0/3 -> 1),
 *   CloseSetup, and basicevent1#SetSetupDoneStatus
 */
class WeMoProvisionerClient(
    private val appContext: Context? = null,
    private val defaultApHost: String = "10.22.22.1"
) {
    private val wifiSocketFactory = WeMoNetworkMonitor.createWifiBoundSocketFactory(appContext)

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(wifiSocketFactory)
        .connectTimeout(900, TimeUnit.MILLISECONDS)
        .readTimeout(2200, TimeUnit.MILLISECONDS)
        .writeTimeout(1200, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val xmlMediaType = "text/xml; charset=\"utf-8\"".toMediaType()
    private val setupPorts = listOf(49152, 49153, 49154, 49151, 49155, 49156, 49157, 49158, 49159)

    companion object {
        const val METHOD_2_RTOS_SALT_SUFFIX = "b3{8t;80dIN{ra83eC1s?M70?683@2Yf"
        const val METHOD_3_BINARY_OPTION_EXTRA = "bVduaWFyZkllaWVAb3RjbHAkcm9uT2Jh"

        private fun escapeXml(input: String): String {
            return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }
    }

    private fun nowTime(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        return Regex("<$tag>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Probes the Belkin WeMo Setup AP at [apHost] across all 9 UPnP ports for `/setup.xml` or `/Lightsetup.xml`,
     * and enriches metadata via `metainfo1#GetMetaInfo` when available.
     * Aborts immediately if TCP `connect()` times out so an unreachable IP fails in ~650ms.
     */
    suspend fun probeSetupDevice(
        apHost: String = defaultApHost,
        preferredPort: Int = 49152
    ): WeMoSetupDeviceDescriptor? = withContext(Dispatchers.IO) {
        val candidates = (listOf(preferredPort) + setupPorts).distinct()
        val paths = listOf("/setup.xml", "/Lightsetup.xml")

        var foundPort: Int? = null
        var setupXml: String? = null

        for (port in candidates) {
            var hostUnreachable = false
            for (path in paths) {
                val sock = wifiSocketFactory.createSocket()
                try {
                    try {
                        sock.connect(InetSocketAddress(apHost, port), 650)
                    } catch (_: ConnectException) {
                        // Port closed (ECONNREFUSED) — host is alive, try next port immediately
                        break
                    } catch (_: SocketTimeoutException) {
                        // Host IP itself timed out on TCP SYN — abort port sweep immediately
                        hostUnreachable = true
                        break
                    }

                    sock.soTimeout = 1000
                    val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                    writer.write(
                        "GET $path HTTP/1.1\r\n" +
                            "Host: $apHost:$port\r\n" +
                            "User-Agent: WeMiSwitchControl/1.0 UPnP/1.1\r\n" +
                            "Connection: close\r\n\r\n"
                    )
                    writer.flush()

                    val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                    val statusLine = reader.readLine() ?: continue
                    if (!statusLine.contains("200")) continue
                    var line = reader.readLine()
                    while (line != null && line.isNotEmpty()) {
                        line = reader.readLine()
                    }
                    val body = reader.readText()
                    if (body.isNotBlank() && (body.contains("<device", ignoreCase = true) || body.contains("Belkin", ignoreCase = true))) {
                        foundPort = port
                        setupXml = body
                        break
                    }
                } catch (_: Exception) {
                    continue
                } finally {
                    runCatching { sock.close() }
                }
            }
            if (foundPort != null || hostUnreachable) break
        }

        val resolvedPort = foundPort ?: return@withContext null
        val xml = setupXml.orEmpty()

        val friendlyName = extractXmlTag(xml, "friendlyName")?.ifBlank { null } ?: "WeMo Switch"
        val modelName = extractXmlTag(xml, "modelName")?.ifBlank { null } ?: "LightSwitch"
        val deviceTypeUrn = extractXmlTag(xml, "deviceType").orEmpty()
        val xmlSerial = extractXmlTag(xml, "serialNumber").orEmpty()
        val xmlMac = extractXmlTag(xml, "macAddress").orEmpty()
        val udn = extractXmlTag(xml, "UDN").orEmpty()
        val xmlFirmware = extractXmlTag(xml, "firmwareVersion").orEmpty()

        val isRtos = extractXmlTag(xml, "rtos") == "1"
        val isIot = extractXmlTag(xml, "iot") == "1"
        val binaryOption = extractXmlTag(xml, "binaryOption") == "1"

        val metaClient = WeMoNetworkClient(appContext)
        val meta = runCatching { metaClient.getMetaInfo(apHost, resolvedPort) }.getOrNull()

        val mac = meta?.macAddress?.ifBlank { null }
            ?: xmlMac.replace(":", "").replace("-", "").ifBlank { "EC1A59001122" }
        val serial = meta?.serialNumber?.ifBlank { null }
            ?: xmlSerial.ifBlank { "221517K01018AB" }
        val sku = meta?.deviceSku?.ifBlank { null } ?: modelName
        val firmware = meta?.firmwareVersion?.ifBlank { null } ?: xmlFirmware

        val parsedType = when {
            modelName.contains("Dimmer", ignoreCase = true) ||
                deviceTypeUrn.contains("dimmer", ignoreCase = true) ||
                sku.contains("F7C059", ignoreCase = true) ||
                sku.contains("WDS060", ignoreCase = true) -> WeMoDeviceType.LIGHTBULB_DIMMER
            modelName.contains("Sensor", ignoreCase = true) ||
                modelName.contains("Motion", ignoreCase = true) ||
                deviceTypeUrn.contains("sensor", ignoreCase = true) -> WeMoDeviceType.MOTION_SENSOR
            else -> WeMoDeviceType.SWITCH_PLUG
        }

        // pywemo auto-detection rule from setup.xml:
        // - rtos == 1 and iot != 1 -> Method 2 (addLengths = false)
        // - binaryOption == 1 -> Method 3 (addLengths = true)
        // - default -> Method 1 (addLengths = true)
        val recommendedMethod = when {
            isRtos && !isIot -> 2
            binaryOption -> 3
            sku.contains("WLS040", ignoreCase = true) ||
                sku.contains("WDS060", ignoreCase = true) ||
                sku.contains("WSP080", ignoreCase = true) -> 2
            else -> 1
        }
        val recommendedAddLengths = recommendedMethod != 2

        WeMoSetupDeviceDescriptor(
            host = apHost,
            port = resolvedPort,
            friendlyName = friendlyName,
            modelName = modelName,
            hardwareSku = sku,
            deviceType = parsedType,
            serialNumber = serial,
            macAddress = mac,
            udn = udn,
            firmwareVersion = firmware,
            isRtos = isRtos,
            isIot = isIot,
            binaryOption = binaryOption,
            recommendedEncryptionMethod = recommendedMethod,
            recommendedAddLengths = recommendedAddLengths
        )
    }

    /**
     * Resolves the active setup port on the WeMo AP (10.22.22.1:49152 or :49153).
     */
    suspend fun resolveSetupPort(apHost: String = defaultApHost, preferredPort: Int = 49152): Int {
        return probeSetupDevice(apHost, preferredPort)?.port ?: preferredPort
    }

    /**
     * Queries urn:Belkin:service:WiFiSetup:1#GetApList (with GetNetworkList fallback) from the
     * WeMo setup AP (10.22.22.1) and parses the pipe-delimited AP list:
     * SSID|Channel|RSSI|AuthMode/EncrypType.
     */
    suspend fun fetchAvailableApList(
        apHost: String = defaultApHost,
        apPort: Int = 49152
    ): List<WeMoScannedAp> = withContext(Dispatchers.IO) {
        val descriptor = probeSetupDevice(apHost, apPort)
        val resolvedPort = descriptor?.port ?: apPort
        val actions = listOf("GetApList", "GetNetworkList")

        for (action in actions) {
            val soapBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:$action xmlns:u="urn:Belkin:service:WiFiSetup:1">
                    </u:$action>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            try {
                val req = Request.Builder()
                    .url("http://$apHost:$resolvedPort/upnp/control/WiFiSetup1")
                    .header("SOAPACTION", "\"urn:Belkin:service:WiFiSetup:1#$action\"")
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("Connection", "close")
                    .post(soapBody.toRequestBody(xmlMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val xml = resp.body?.string().orEmpty()
                        val parsed = parseApListXml(xml)
                        if (parsed.isNotEmpty()) {
                            return@withContext parsed
                        }
                    }
                }
            } catch (_: Exception) {
                if (descriptor == null) break
            }
        }
        emptyList()
    }

    fun parseApListXml(xml: String): List<WeMoScannedAp> {
        val unescaped = xml
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
        val rawList = Regex("<(?:ApList|NetworkList)>([\\s\\S]*?)</(?:ApList|NetworkList)>", RegexOption.IGNORE_CASE)
            .find(unescaped)?.groupValues?.getOrNull(1)?.trim() ?: return emptyList()

        val results = mutableListOf<WeMoScannedAp>()
        val normalizedList = rawList.replace(Regex("Page:[^$\\n]*\\$?"), "\n")
        for (rawLine in normalizedList.lines()) {
            val clean = rawLine.trim().removeSuffix(",").trim()
            if (clean.isEmpty() || clean.startsWith("Page:") || !clean.contains("|")) continue
            val parts = clean.split("|")
            if (parts.size >= 4) {
                val ssid = parts[0].trim()
                if (ssid.isEmpty()) continue
                val channel = parts[1].trim().toIntOrNull() ?: 6
                val rssi = parts[2].trim().toIntOrNull()?.coerceIn(1, 100) ?: 75
                val secString = parts.last().trim().removeSuffix(",")
                val secParts = secString.split("/")
                val auth = secParts.getOrNull(0)?.trim().orEmpty().ifEmpty { "WPA2PSK" }
                val enc = secParts.getOrNull(1)?.trim()?.removeSuffix(",").orEmpty().ifEmpty { "AES" }
                if (results.none { it.ssid == ssid }) {
                    results.add(
                        WeMoScannedAp(
                            ssid = ssid,
                            channel = channel,
                            signalStrengthPercent = rssi,
                            authMode = auth,
                            encryptionType = enc
                        )
                    )
                }
            }
        }
        return results
    }

    /**
     * Builds the raw `keydata` string for Belkin WeMo's AES-128-CBC key derivation (Methods 1, 2, or 3)
     * matching `pywemo/ouimeaux_device/__init__.py` (`encrypt_aes128`):
     * - Method 1 (Gen 1 OpenWrt F7C030/F7C059/F7C063): `mac[0..6] + serial + mac[6..12]`
     * - Method 2 (Gen 2 RTOS WLS040/WLS0403/WDS060/WSP080): `mac[0..6] + serial + mac[6..12] + "b3{8t;80dIN{ra83eC1s?M70?683@2Yf"`
     * - Method 3 (`binaryOption = 1`): `mac[0..3] + mac[9..12] + serial + "bVduaWFyZkllaWVAb3RjbHAkcm9uT2Jh" + mac[6..9] + mac[3..6]`
     */
    fun buildKeyData(
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1
    ): String {
        val cleanMac = macAddress.replace(":", "").replace("-", "").trim()
            .padEnd(12, '0').take(12)
        val cleanSerial = serialNumber.trim()
        return when (encryptionMethod) {
            2 -> cleanMac.substring(0, 6) + cleanSerial + cleanMac.substring(6, 12) + METHOD_2_RTOS_SALT_SUFFIX
            3 -> cleanMac.substring(0, 3) + cleanMac.substring(9, 12) + cleanSerial +
                METHOD_3_BINARY_OPTION_EXTRA + cleanMac.substring(6, 9) + cleanMac.substring(3, 6)
            else -> cleanMac.substring(0, 6) + cleanSerial + cleanMac.substring(6, 12)
        }
    }

    /**
     * Encrypts the Wi-Fi password using Belkin WeMo's exact AES-128-CBC key derivation
     * (`pywemo/ouimeaux_device/api/wifi_setup.py` + `pywemo/ouimeaux_device/__init__.py`):
     * - `salt = keydata[0..8]`
     * - `iv = keydata[0..16]` (UTF-8 bytes)
     * - `derivedKey = MD5(keydata + salt)[0..16]`
     * - `AES/CBC/PKCS5Padding` encrypted and Base64-encoded
     * - By default (`addPasswordLengths == null`), appends the 4-hex-digit suffix (`xxyy`) for
     *   Methods 1 and 3, and omits it for Method 2 (matching `pywemo`: `add_lengths = method in (1, 3)`).
     */
    fun encryptWifiPassword(
        plainPassword: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        addPasswordLengths: Boolean? = null
    ): String {
        val keydata = buildKeyData(macAddress, serialNumber, encryptionMethod).padEnd(16, '0')
        val keyBytes = keydata.toByteArray(Charsets.UTF_8)
        val saltBytes = keydata.substring(0, 8).toByteArray(Charsets.UTF_8)
        val ivBytes = keydata.substring(0, 16).toByteArray(Charsets.UTF_8)

        val md5 = MessageDigest.getInstance("MD5")
        md5.update(keyBytes)
        md5.update(saltBytes)
        val derivedKey = md5.digest().copyOfRange(0, 16)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(ivBytes))
        val plainBytes = plainPassword.toByteArray(Charsets.UTF_8)
        val encryptedBytes = cipher.doFinal(plainBytes)
        val base64Encoded = Base64.getEncoder().encodeToString(encryptedBytes)

        val shouldAddLengths = addPasswordLengths ?: (encryptionMethod != 2)
        return if (shouldAddLengths) {
            val encLenHex = String.format(Locale.US, "%02x", base64Encoded.length and 0xFF)
            val origLenHex = String.format(Locale.US, "%02x", plainBytes.size and 0xFF)
            base64Encoded + encLenHex + origLenHex
        } else {
            base64Encoded
        }
    }

    /**
     * Decrypts a WeMo encrypted Wi-Fi password payload (for unit test verification).
     */
    fun decryptWifiPassword(
        encryptedPayload: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        hasLengthSuffix: Boolean = (encryptionMethod != 2)
    ): String {
        val keydata = buildKeyData(macAddress, serialNumber, encryptionMethod).padEnd(16, '0')
        val keyBytes = keydata.toByteArray(Charsets.UTF_8)
        val saltBytes = keydata.substring(0, 8).toByteArray(Charsets.UTF_8)
        val ivBytes = keydata.substring(0, 16).toByteArray(Charsets.UTF_8)

        val md5 = MessageDigest.getInstance("MD5")
        md5.update(keyBytes)
        md5.update(saltBytes)
        val derivedKey = md5.digest().copyOfRange(0, 16)

        val base64Part = if (hasLengthSuffix && encryptedPayload.length > 4) {
            encryptedPayload.substring(0, encryptedPayload.length - 4)
        } else {
            encryptedPayload
        }
        val cipherBytes = Base64.getDecoder().decode(base64Part)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(ivBytes))
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    /**
     * Provisions a factory-reset Belkin WeMo switch at [apHost]:[apPort] onto the target 2.4 GHz
     * home Wi-Fi network using pywemo's exact sequence:
     * 1. Probe setup descriptor (`/setup.xml` / `/Lightsetup.xml` + `metainfo1#GetMetaInfo`) and auto-detect
     *    encryption method (`0` = auto-detect from `<rtos>` / `<iot>` / `<binaryOption>`, or `1` / `2` / `3`).
     * 2. Optionally set `<FriendlyName>` via `basicevent1#ChangeFriendlyName` if [customFriendlyName] is provided.
     * 3. Send `WiFiSetup1#ConnectHomeNetwork` twice (`100ms` apart).
     * 4. Poll `WiFiSetup1#GetNetworkStatus` until status is `1` (connected) or `2` (short password),
     *    waiting extra cycles if status is `3` (handshake in progress).
     * 5. Send `WiFiSetup1#CloseSetup` and `basicevent1#SetSetupDoneStatus`.
     */
    suspend fun provisionDeviceToHomeWifi(
        apHost: String = defaultApHost,
        apPort: Int = 49152,
        ssid: String,
        plainPassword: String,
        channel: Int = 6,
        authMode: String = "WPA2PSK",
        encryptionType: String = "AES",
        encryptionMethod: Int = 0,
        addPasswordLengths: Boolean? = null,
        customFriendlyName: String? = null,
        maxStatusPolls: Int = 10,
        statusPollDelayMs: Long = 750L
    ): WeMoProvisionResult = withContext(Dispatchers.IO) {
        val descriptor = probeSetupDevice(apHost, apPort)
        val resolvedPort = descriptor?.port ?: apPort
        val mac = descriptor?.macAddress?.ifBlank { "EC1A59001122" } ?: "EC1A59001122"
        val serial = descriptor?.serialNumber?.ifBlank { "221517K01018AB" } ?: "221517K01018AB"

        val effectiveMethod = if (encryptionMethod in 1..3) {
            encryptionMethod
        } else {
            descriptor?.recommendedEncryptionMethod ?: 1
        }
        val effectiveAddLengths = addPasswordLengths ?: (effectiveMethod != 2)

        // Optional: push custom FriendlyName while connected to the setup AP before closing setup
        if (!customFriendlyName.isNullOrBlank() && descriptor != null) {
            runCatching {
                val renameBody = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                      <s:Body>
                        <u:ChangeFriendlyName xmlns:u="urn:Belkin:service:basicevent:1">
                          <FriendlyName>${escapeXml(customFriendlyName.trim())}</FriendlyName>
                        </u:ChangeFriendlyName>
                      </s:Body>
                    </s:Envelope>
                """.trimIndent()
                val renameReq = Request.Builder()
                    .url("http://$apHost:$resolvedPort/upnp/control/basicevent1")
                    .header("SOAPACTION", "\"urn:Belkin:service:basicevent:1#ChangeFriendlyName\"")
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("Connection", "close")
                    .post(renameBody.toRequestBody(xmlMediaType))
                    .build()
                httpClient.newCall(renameReq).execute().close()
            }
        }

        val isOpenNetwork = encryptionType.equals("NONE", ignoreCase = true) ||
            authMode.equals("OPEN", ignoreCase = true) ||
            plainPassword.isEmpty()

        val resolvedAuth = if (isOpenNetwork) "OPEN" else authMode
        val resolvedEncrypt = if (isOpenNetwork) "NONE" else encryptionType
        val encryptedPwd = if (isOpenNetwork) {
            ""
        } else {
            encryptWifiPassword(
                plainPassword = plainPassword,
                macAddress = mac,
                serialNumber = serial,
                encryptionMethod = effectiveMethod,
                addPasswordLengths = effectiveAddLengths
            )
        }

        val connectBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:ConnectHomeNetwork xmlns:u="urn:Belkin:service:WiFiSetup:1">
                  <ssid>${escapeXml(ssid)}</ssid>
                  <auth>${escapeXml(resolvedAuth)}</auth>
                  <password>${escapeXml(encryptedPwd)}</password>
                  <encrypt>${escapeXml(resolvedEncrypt)}</encrypt>
                  <channel>$channel</channel>
                </u:ConnectHomeNetwork>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val endpoint = "http://$apHost:$resolvedPort/upnp/control/WiFiSetup1"
        var httpSuccess = false

        // pywemo requirement: ConnectHomeNetwork must be sent twice with a ~100ms pause
        repeat(2) { attempt ->
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("SOAPACTION", "\"urn:Belkin:service:WiFiSetup:1#ConnectHomeNetwork\"")
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("Connection", "close")
                    .post(connectBody.toRequestBody(xmlMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        httpSuccess = true
                    }
                }
            } catch (_: Exception) {
                // If descriptor was null and first ConnectHomeNetwork failed, setup AP is unreachable
                if (descriptor == null && attempt == 0) return@repeat
            }
            if (attempt == 0 && httpSuccess) delay(100L)
        }

        // Poll GetNetworkStatus until status is 1 (connected) or 2 (short password),
        // waiting extra cycles if status is 3 (WPA2 handshake in progress).
        var netStatus = if (httpSuccess) 0 else -1
        var closeStatus = ""

        if (httpSuccess) {
            val statusBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:GetNetworkStatus xmlns:u="urn:Belkin:service:WiFiSetup:1">
                    </u:GetNetworkStatus>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            suspend fun pollOnce(): Int? {
                return try {
                    val statusReq = Request.Builder()
                        .url(endpoint)
                        .header("SOAPACTION", "\"urn:Belkin:service:WiFiSetup:1#GetNetworkStatus\"")
                        .header("Content-Type", "text/xml; charset=\"utf-8\"")
                        .header("Connection", "close")
                        .post(statusBody.toRequestBody(xmlMediaType))
                        .build()
                    httpClient.newCall(statusReq).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        Regex("<NetworkStatus>(\\d+)</NetworkStatus>", RegexOption.IGNORE_CASE)
                            .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    }
                } catch (_: Exception) {
                    null
                }
            }

            delay(minOf(200L, statusPollDelayMs))
            var polls = 0
            while (polls < maxStatusPolls) {
                val polled = pollOnce()
                if (polled != null) {
                    netStatus = polled
                    if (netStatus == 1 || netStatus == 2) break
                } else if (polls > 0 && netStatus == 3) {
                    // AP dropped after handshake status 3 -> switch joined home Wi-Fi
                    netStatus = 1
                    break
                } else {
                    break
                }
                polls++
                if (polls < maxStatusPolls) {
                    delay(statusPollDelayMs)
                }
            }

            // If still in status 3 (handshake), poll up to 3 extra cycles per pywemo
            var extraHandshakeLoops = 3
            while (netStatus == 3 && extraHandshakeLoops > 0) {
                delay(statusPollDelayMs)
                val polled = pollOnce()
                if (polled != null) {
                    netStatus = polled
                    if (netStatus == 1 || netStatus == 2) break
                } else {
                    netStatus = 1
                    break
                }
                extraHandshakeLoops--
            }

            // Always send CloseSetup per pywemo (even if status is 0 or 3)
            try {
                val closeBody = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                      <s:Body>
                        <u:CloseSetup xmlns:u="urn:Belkin:service:WiFiSetup:1">
                        </u:CloseSetup>
                      </s:Body>
                    </s:Envelope>
                """.trimIndent()
                val closeReq = Request.Builder()
                    .url(endpoint)
                    .header("SOAPACTION", "\"urn:Belkin:service:WiFiSetup:1#CloseSetup\"")
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("Connection", "close")
                    .post(closeBody.toRequestBody(xmlMediaType))
                    .build()
                httpClient.newCall(closeReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    closeStatus = extractXmlTag(body, "status").orEmpty().ifBlank {
                        if (resp.isSuccessful) "success" else "HTTP ${resp.code}"
                    }
                }
            } catch (_: Exception) {
                closeStatus = "ap_closed"
            }

            // Finalize with basicevent1#SetSetupDoneStatus per pywemo
            if (netStatus == 1 || netStatus == 3) {
                runCatching {
                    val doneBody = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                          <s:Body>
                            <u:SetSetupDoneStatus xmlns:u="urn:Belkin:service:basicevent:1">
                            </u:SetSetupDoneStatus>
                          </s:Body>
                        </s:Envelope>
                    """.trimIndent()
                    val doneReq = Request.Builder()
                        .url("http://$apHost:$resolvedPort/upnp/control/basicevent1")
                        .header("SOAPACTION", "\"urn:Belkin:service:basicevent:1#SetSetupDoneStatus\"")
                        .header("Content-Type", "text/xml; charset=\"utf-8\"")
                        .header("Connection", "close")
                        .post(doneBody.toRequestBody(xmlMediaType))
                        .build()
                    httpClient.newCall(doneReq).execute().close()
                }
            }
        }

        val overallSuccess = httpSuccess && netStatus != 2
        val statusDesc = when {
            httpSuccess && netStatus == 2 ->
                "Password rejected by WeMo (Status=2): Wi-Fi password must be at least 8 characters"
            httpSuccess && (netStatus == 1 || netStatus == 3) ->
                "Provisioned to '$ssid' (Status=$netStatus, Method $effectiveMethod) • Setup AP closed, switch joining Home Wi-Fi"
            httpSuccess ->
                "Dispatched ConnectHomeNetwork + CloseSetup to '$ssid' at $apHost:$resolvedPort (Method $effectiveMethod)"
            else ->
                "WeMo Setup AP ($apHost:$resolvedPort) unreachable — connect phone Wi-Fi to WeMo.Light.xxx / WeMo.Mini.xxx first"
        }

        val updatedDescriptor = if (descriptor != null && !customFriendlyName.isNullOrBlank()) {
            descriptor.copy(friendlyName = customFriendlyName.trim())
        } else {
            descriptor
        }

        WeMoProvisionResult(
            success = overallSuccess,
            networkStatusCode = netStatus.coerceAtLeast(0),
            statusMessage = statusDesc,
            encryptedPasswordPayload = encryptedPwd,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = updatedDescriptor?.friendlyName ?: "WeMo Setup AP ($apHost)",
                endpoint = endpoint,
                soapAction = "WiFiSetup1#ConnectHomeNetwork($ssid, Method $effectiveMethod, lengths=$effectiveAddLengths)",
                statusText = statusDesc,
                xmlSnippet = connectBody.take(260),
                isLiveLanSuccess = overallSuccess
            ),
            closeSetupStatus = closeStatus,
            effectiveEncryptionMethod = effectiveMethod,
            addedLengthSuffix = effectiveAddLengths,
            setupDevice = updatedDescriptor
        )
    }
}
