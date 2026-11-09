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
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    val recommendedAddLengths: Boolean,
    val newAlgo: Boolean = false,
    val setupXmlPath: String = "/setup.xml",
    val macAndSerialSource: String = "GetMetaInfo",
    val rawMetaInfo: String = "",
    val probeTraceSummary: String = ""
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
    val setupDevice: WeMoSetupDeviceDescriptor? = null,
    val debugTraceLines: List<String> = emptyList()
)

/**
 * Zero-cloud Belkin WeMo Wi-Fi setup provisioner (`10.22.22.1`) matching
 * `pywemo/ouimeaux_device/__init__.py` and `pywemo/ouimeaux_device/api/wifi_setup.py`.
 *
 * Key reliability & diagnostic guarantees:
 * - Uses [OkHttpClient] (with `Content-Length` framing) rather than raw socket EOF reads when
 *   fetching `/setup.xml` and `/Lightsetup.xml`, preventing 1000ms read timeouts on embedded
 *   WeMo HTTP servers that delay TCP `FIN`.
 * - Uses an 8.5s HTTP read timeout so active 11-channel 2.4 GHz site surveys (`WiFiSetup1#GetApList`)
 *   and WPA2 handshakes complete without premature socket timeouts.
 * - Requires `NetworkStatus == 1` (or `3` handshake followed by AP close) for success; never falsely
 *   reports `NetworkStatus == 0` as success or calls `CloseSetup` when `NetworkStatus == 0`,
 *   keeping the switch's `10.22.22.1` soft-AP alive for inspection and retry.
 */
class WeMoProvisionerClient(
    private val appContext: Context? = null,
    private val defaultApHost: String = "10.22.22.1"
) {
    private val wifiSocketFactory = WeMoNetworkMonitor.createWifiBoundSocketFactory(appContext)

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(wifiSocketFactory)
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(8500, TimeUnit.MILLISECONDS)
        .writeTimeout(2500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val xmlMediaType = "text/xml; charset=\"utf-8\"".toMediaType()
    private val setupPorts = listOf(49152, 49153, 49154, 49151, 49155, 49156, 49157, 49158, 49159)

    @Volatile
    var lastProbeDiagnosticSummary: String = "Not probed yet"
        private set

    @Volatile
    var lastApScanDiagnosticSummary: String = "Not scanned yet"
        private set

    companion object {
        const val METHOD_2_RTOS_SALT_SUFFIX = WeMoPasswordCrypto.METHOD_2_RTOS_SALT_SUFFIX
        const val METHOD_3_BINARY_OPTION_EXTRA = WeMoPasswordCrypto.METHOD_3_BINARY_OPTION_EXTRA

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
     * and enriches metadata via `metainfo1#GetMetaInfo` (with `/setup.xml` and `basicevent1#GetMacAddr` fallbacks).
     */
    suspend fun probeSetupDevice(
        apHost: String = defaultApHost,
        preferredPort: Int = 49152
    ): WeMoSetupDeviceDescriptor? = withContext(Dispatchers.IO) {
        val isLoopback = apHost == "127.0.0.1" || apHost.equals("localhost", ignoreCase = true)
        val loopbackExtraPorts = if (isLoopback) WeMoNetworkClient.currentLoopbackCandidatePorts() else emptyList()
        val candidates = (listOf(preferredPort) + loopbackExtraPorts + setupPorts).distinct()
        val paths = listOf("/setup.xml", "/Lightsetup.xml")
        val tcpConnectTimeoutMs = if (isLoopback) 550 else 1500

        var foundPort: Int? = null
        var foundPath = "/setup.xml"
        var setupXml: String? = null
        val portLogs = mutableListOf<String>()

        for (port in candidates) {
            var hostUnreachable = false
            val tcpStartMs = System.currentTimeMillis()
            try {
                wifiSocketFactory.createSocket().use { sock ->
                    sock.connect(InetSocketAddress(apHost, port), tcpConnectTimeoutMs)
                }
            } catch (_: ConnectException) {
                portLogs.add(":$port=REFUSED")
                continue
            } catch (_: SocketTimeoutException) {
                val elapsed = System.currentTimeMillis() - tcpStartMs
                portLogs.add(":$port=TCP_TIMEOUT(${elapsed}ms)")
                hostUnreachable = true
            } catch (_: NoRouteToHostException) {
                portLogs.add(":$port=NO_ROUTE")
                hostUnreachable = true
            } catch (e: Exception) {
                portLogs.add(":$port=${e.javaClass.simpleName}")
                continue
            }

            if (hostUnreachable) {
                break
            }

            for (path in paths) {
                val reqStartMs = System.currentTimeMillis()
                try {
                    val req = Request.Builder()
                        .url("http://$apHost:$port$path")
                        .header("User-Agent", "WeMiSwitchControl/1.0 UPnP/1.1")
                        .header("Connection", "close")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val elapsed = System.currentTimeMillis() - reqStartMs
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && body.isNotBlank() &&
                            (body.contains("<device", ignoreCase = true) ||
                                body.contains("Belkin", ignoreCase = true) ||
                                body.contains("WeMo", ignoreCase = true))
                        ) {
                            foundPort = port
                            foundPath = path
                            setupXml = body
                            portLogs.add(":$port$path=HTTP_200(${elapsed}ms,${body.length}B)")
                            break
                        } else {
                            portLogs.add(":$port$path=HTTP_${resp.code}(${elapsed}ms)")
                        }
                    }
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - reqStartMs
                    portLogs.add(":$port$path=${e.javaClass.simpleName}(${elapsed}ms)")
                }
            }
            if (foundPort != null) break
        }

        val traceSummary = portLogs.joinToString(" ")
        lastProbeDiagnosticSummary = traceSummary.ifBlank { "No ports probed" }

        val resolvedPort = foundPort ?: return@withContext null
        val xml = setupXml.orEmpty()

        val friendlyName = extractXmlTag(xml, "friendlyName")?.ifBlank { null } ?: "WeMo Switch"
        val modelName = extractXmlTag(xml, "modelName")?.ifBlank { null } ?: "LightSwitch"
        val deviceTypeUrn = extractXmlTag(xml, "deviceType").orEmpty()
        val xmlSerial = extractXmlTag(xml, "serialNumber").orEmpty().trim()
        val xmlMac = extractXmlTag(xml, "macAddress").orEmpty()
            .replace(":", "").replace("-", "").trim()
        val udn = extractXmlTag(xml, "UDN").orEmpty().trim()
        val xmlFirmware = extractXmlTag(xml, "firmwareVersion").orEmpty().trim()

        val isRtos = extractXmlTag(xml, "rtos") == "1"
        val isIot = extractXmlTag(xml, "iot") == "1"
        val binaryOption = extractXmlTag(xml, "binaryOption") == "1"
        val newAlgo = extractXmlTag(xml, "new_algo") == "1"

        val (rawMetaString, meta) = fetchSetupMetaInfo(apHost, resolvedPort)
        val metaMac = meta?.macAddress?.replace(":", "")?.replace("-", "")?.trim()?.ifBlank { null }
        val metaSerial = meta?.serialNumber?.trim()?.ifBlank { null }

        val basicMac = if (metaMac == null && xmlMac.isBlank()) {
            fetchBasicEventTag(apHost, resolvedPort, "GetMacAddr", "MacAddr")
                ?.replace(":", "")?.replace("-", "")?.trim()?.ifBlank { null }
        } else {
            null
        }
        val basicSerial = if (metaSerial == null && xmlSerial.isBlank()) {
            fetchBasicEventTag(apHost, resolvedPort, "GetSerialNo", "SerialNo")?.trim()?.ifBlank { null }
        } else {
            null
        }

        val macSource = when {
            metaMac != null && metaSerial != null -> "GetMetaInfo"
            metaMac != null && xmlSerial.isNotBlank() -> "GetMetaInfo+setup.xml"
            xmlMac.isNotBlank() && xmlSerial.isNotBlank() -> "setup.xml"
            basicMac != null || basicSerial != null -> "basicevent1#GetMacAddr"
            metaMac != null || xmlMac.isNotBlank() -> "partial(xml/meta)"
            else -> "MISSING(Fallback!)"
        }

        val mac = metaMac ?: xmlMac.ifBlank { null } ?: basicMac ?: "EC1A59001122"
        val serial = metaSerial ?: xmlSerial.ifBlank { null } ?: basicSerial ?: "221517K01018AB"
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

        val recommendedMethod = when {
            isRtos && !isIot -> 2
            binaryOption -> 3
            newAlgo -> 2
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
            recommendedAddLengths = recommendedAddLengths,
            newAlgo = newAlgo,
            setupXmlPath = foundPath,
            macAndSerialSource = macSource,
            rawMetaInfo = rawMetaString,
            probeTraceSummary = traceSummary
        )
    }

    private fun fetchSetupMetaInfo(apHost: String, port: Int): Pair<String, WeMoMetaInfo?> {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:GetMetaInfo xmlns:u="urn:Belkin:service:metainfo:1">
                </u:GetMetaInfo>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        return try {
            val req = Request.Builder()
                .url("http://$apHost:$port/upnp/control/metainfo1")
                .header("SOAPACTION", "\"urn:Belkin:service:metainfo:1#GetMetaInfo\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return "HTTP_${resp.code}" to null
                }
                val rawTag = extractXmlTag(body, "MetaInfo").orEmpty()
                rawTag to WeMoXmlAndSkuParser.parseMetaInfoSoapResponse(body)
            }
        } catch (e: Exception) {
            "ERR_${e.javaClass.simpleName}" to null
        }
    }

    private fun fetchBasicEventTag(apHost: String, port: Int, action: String, responseTag: String): String? {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$action xmlns:u="urn:Belkin:service:basicevent:1">
                </u:$action>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        return try {
            val req = Request.Builder()
                .url("http://$apHost:$port/upnp/control/basicevent1")
                .header("SOAPACTION", "\"urn:Belkin:service:basicevent:1#$action\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                extractXmlTag(resp.body?.string().orEmpty(), responseTag)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveSetupPort(apHost: String = defaultApHost, preferredPort: Int = 49152): Int {
        return probeSetupDevice(apHost, preferredPort)?.port ?: preferredPort
    }

    suspend fun fetchAvailableApList(
        apHost: String = defaultApHost,
        apPort: Int = 49152,
        knownDescriptor: WeMoSetupDeviceDescriptor? = null
    ): List<WeMoScannedAp> = withContext(Dispatchers.IO) {
        val descriptor = knownDescriptor ?: probeSetupDevice(apHost, apPort)
        val resolvedPort = descriptor?.port ?: apPort
        val actions = listOf("GetApList", "GetNetworkList")
        val actionLogs = mutableListOf<String>()

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

            val startMs = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url("http://$apHost:$resolvedPort/upnp/control/WiFiSetup1")
                    .header("SOAPACTION", "\"urn:Belkin:service:WiFiSetup:1#$action\"")
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("Connection", "close")
                    .post(soapBody.toRequestBody(xmlMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val elapsed = System.currentTimeMillis() - startMs
                    val xml = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val parsed = parseApListXml(xml)
                        actionLogs.add("$action=HTTP_200(${elapsed}ms,count=${parsed.size})")
                        if (parsed.isNotEmpty()) {
                            lastApScanDiagnosticSummary = actionLogs.joinToString(" ")
                            return@withContext parsed
                        }
                    } else {
                        actionLogs.add("$action=HTTP_${resp.code}(${elapsed}ms)")
                    }
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startMs
                actionLogs.add("$action=${e.javaClass.simpleName}(${elapsed}ms)")
                if (descriptor == null) break
            }
        }
        lastApScanDiagnosticSummary = actionLogs.joinToString(" ").ifBlank { "No AP scan response" }
        emptyList()
    }

    fun parseApListXml(xml: String): List<WeMoScannedAp> =
        WeMoXmlAndSkuParser.parseApListXml(xml)

    fun buildKeyData(
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1
    ): String = WeMoPasswordCrypto.buildKeyData(
        macAddress = macAddress,
        serialNumber = serialNumber,
        encryptionMethod = encryptionMethod
    )

    fun encryptWifiPassword(
        plainPassword: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        addPasswordLengths: Boolean? = null
    ): String = WeMoPasswordCrypto.encryptWifiPassword(
        plainPassword = plainPassword,
        macAddress = macAddress,
        serialNumber = serialNumber,
        encryptionMethod = encryptionMethod,
        addPasswordLengths = addPasswordLengths
    )

    fun decryptWifiPassword(
        encryptedPayload: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        hasLengthSuffix: Boolean = (encryptionMethod != 2)
    ): String = WeMoPasswordCrypto.decryptWifiPassword(
        encryptedPayload = encryptedPayload,
        macAddress = macAddress,
        serialNumber = serialNumber,
        encryptionMethod = encryptionMethod,
        hasLengthSuffix = hasLengthSuffix
    )

    /**
     * Provisions a factory-reset Belkin WeMo switch at [apHost]:[apPort] onto the target 2.4 GHz
     * home Wi-Fi network using `pywemo`'s exact sequence and emitting live step-by-step debug telemetry.
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
        maxStatusPolls: Int = 18,
        statusPollDelayMs: Long = 1000L,
        knownDescriptor: WeMoSetupDeviceDescriptor? = null,
        knownScannedAps: List<WeMoScannedAp> = emptyList(),
        onProgress: ((String) -> Unit)? = null
    ): WeMoProvisionResult = withContext(Dispatchers.IO) {
        val traceLines = mutableListOf<String>()
        fun logStep(line: String) {
            traceLines.add(line)
            onProgress?.invoke(line)
        }

        val descriptor = knownDescriptor ?: probeSetupDevice(apHost, apPort)
        val resolvedPort = descriptor?.port ?: apPort
        if (descriptor != null) {
            logStep(
                "[STEP 1 PROBE] OK $apHost:$resolvedPort (${descriptor.setupXmlPath}) model=${descriptor.modelName} sku=${descriptor.hardwareSku} fw=${descriptor.firmwareVersion}"
            )
            logStep(
                "[STEP 2 ID] mac=${descriptor.macAddress} sn=${descriptor.serialNumber} (src=${descriptor.macAndSerialSource}) rtos=${descriptor.isRtos} iot=${descriptor.isIot} binOpt=${descriptor.binaryOption} newAlgo=${descriptor.newAlgo}"
            )
        } else {
            logStep("[STEP 1 PROBE] WARN: Probe failed on $apHost ($lastProbeDiagnosticSummary)")
        }

        val mac = descriptor?.macAddress?.ifBlank { "EC1A59001122" } ?: "EC1A59001122"
        val serial = descriptor?.serialNumber?.ifBlank { "221517K01018AB" } ?: "221517K01018AB"

        val matchedAp = knownScannedAps.firstOrNull { it.ssid.equals(ssid.trim(), ignoreCase = true) }
        val effectiveChannel = matchedAp?.channel ?: channel
        val rawAuthMode = matchedAp?.authMode ?: authMode
        val rawEncryptType = matchedAp?.encryptionType ?: encryptionType

        if (matchedAp != null) {
            logStep(
                "[STEP 3 AP] Matched \"${matchedAp.ssid}\" in ApList: Ch=$effectiveChannel Auth=$rawAuthMode/$rawEncryptType RSSI=${matchedAp.signalStrengthPercent}%"
            )
        } else {
            logStep(
                "[STEP 3 AP] Using manual AP params for \"$ssid\": Ch=$effectiveChannel Auth=$rawAuthMode/$rawEncryptType (scannedCount=${knownScannedAps.size})"
            )
        }

        if (rawAuthMode.equals("Unknown", ignoreCase = true)) {
            logStep("[WARN] Router AuthMode is 'Unknown' (likely WPA3-only; WeMo requires 2.4 GHz WPA2-PSK/AES)!")
        }

        val effectiveMethod = if (encryptionMethod in 1..3) {
            encryptionMethod
        } else {
            descriptor?.recommendedEncryptionMethod ?: 1
        }
        val effectiveAddLengths = addPasswordLengths ?: (effectiveMethod != 2)

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
                httpClient.newCall(renameReq).execute().use { resp ->
                    logStep("[RENAME] ChangeFriendlyName(\"${customFriendlyName.trim()}\") -> HTTP_${resp.code}")
                }
            }.onFailure { e ->
                logStep("[RENAME] ChangeFriendlyName failed: ${e.javaClass.simpleName}")
            }
        }

        val isOpenNetwork = rawEncryptType.equals("NONE", ignoreCase = true) ||
            rawAuthMode.equals("OPEN", ignoreCase = true) ||
            plainPassword.isEmpty()

        val resolvedAuth = if (isOpenNetwork) "OPEN" else rawAuthMode
        val resolvedEncrypt = if (isOpenNetwork) "NONE" else rawEncryptType
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

        val lenSuffixPreview = if (!isOpenNetwork && effectiveAddLengths && encryptedPwd.length >= 4) {
            encryptedPwd.takeLast(4)
        } else {
            "none"
        }
        logStep(
            "[STEP 4 CRYPTO] Method=$effectiveMethod addLengths=$effectiveAddLengths plainLen=${plainPassword.length} encLen=${encryptedPwd.length} suffix=$lenSuffixPreview"
        )

        val connectBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:ConnectHomeNetwork xmlns:u="urn:Belkin:service:WiFiSetup:1">
                  <ssid>${escapeXml(ssid)}</ssid>
                  <auth>${escapeXml(resolvedAuth)}</auth>
                  <password>${escapeXml(encryptedPwd)}</password>
                  <encrypt>${escapeXml(resolvedEncrypt)}</encrypt>
                  <channel>$effectiveChannel</channel>
                </u:ConnectHomeNetwork>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val endpoint = "http://$apHost:$resolvedPort/upnp/control/WiFiSetup1"
        var httpSuccess = false
        var lastPairingStatus = ""

        repeat(2) { attempt ->
            val sendStart = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("SOAPACTION", "\"urn:Belkin:service:WiFiSetup:1#ConnectHomeNetwork\"")
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("Connection", "close")
                    .post(connectBody.toRequestBody(xmlMediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val elapsed = System.currentTimeMillis() - sendStart
                    val respXml = resp.body?.string().orEmpty()
                    val pairing = extractXmlTag(respXml, "PairingStatus").orEmpty()
                    if (pairing.isNotEmpty()) lastPairingStatus = pairing
                    logStep(
                        "[STEP 5 SEND #${attempt + 1}] ConnectHomeNetwork -> HTTP_${resp.code} (${elapsed}ms) PairingStatus=${pairing.ifEmpty { "n/a" }}"
                    )
                    if (resp.isSuccessful) {
                        httpSuccess = true
                    }
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - sendStart
                logStep(
                    "[STEP 5 SEND #${attempt + 1}] ERROR: ${e.javaClass.simpleName}: ${e.message?.take(60)} (${elapsed}ms)"
                )
                if (descriptor == null && attempt == 0) return@repeat
            }
            if (attempt == 0 && httpSuccess) delay(100L)
        }

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

            suspend fun pollOnce(pollIndex: Int): Int? {
                val pollStart = System.currentTimeMillis()
                return try {
                    val statusReq = Request.Builder()
                        .url(endpoint)
                        .header("SOAPACTION", "\"urn:Belkin:service:WiFiSetup:1#GetNetworkStatus\"")
                        .header("Content-Type", "text/xml; charset=\"utf-8\"")
                        .header("Connection", "close")
                        .post(statusBody.toRequestBody(xmlMediaType))
                        .build()
                    httpClient.newCall(statusReq).execute().use { resp ->
                        val elapsed = System.currentTimeMillis() - pollStart
                        val body = resp.body?.string().orEmpty()
                        val code = Regex("<NetworkStatus>(\\d+)</NetworkStatus>", RegexOption.IGNORE_CASE)
                            .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        logStep("[POLL #$pollIndex] GetNetworkStatus -> Status=${code ?: "HTTP_${resp.code}"} (${elapsed}ms)")
                        code
                    }
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - pollStart
                    logStep("[POLL #$pollIndex] GetNetworkStatus -> ${e.javaClass.simpleName} (${elapsed}ms)")
                    null
                }
            }

            delay(minOf(350L, statusPollDelayMs))
            var polls = 0
            var consecutiveNullPolls = 0
            while (polls < maxStatusPolls) {
                val polled = pollOnce(polls + 1)
                if (polled != null) {
                    consecutiveNullPolls = 0
                    netStatus = polled
                    if (netStatus == 1 || netStatus == 2) break
                } else {
                    consecutiveNullPolls++
                    if (polls > 0 && netStatus == 3) {
                        logStep("[POLL INFO] Setup AP closed after Status=3 handshake -> treating as connected (Status=1)")
                        netStatus = 1
                        break
                    } else if (consecutiveNullPolls >= 3) {
                        logStep("[POLL WARN] Setup AP unreachable for 3 consecutive polls")
                        break
                    }
                }
                polls++
                if (polls < maxStatusPolls) {
                    delay(statusPollDelayMs)
                }
            }

            var extraHandshakeLoops = 3
            while (netStatus == 3 && extraHandshakeLoops > 0) {
                delay(statusPollDelayMs)
                polls++
                val polled = pollOnce(polls)
                if (polled != null) {
                    netStatus = polled
                    if (netStatus == 1 || netStatus == 2) break
                } else {
                    logStep("[POLL INFO] Setup AP closed during extra Status=3 loop -> Status=1")
                    netStatus = 1
                    break
                }
                extraHandshakeLoops--
            }

            // CRITICAL: Only call CloseSetup & SetSetupDoneStatus when the switch actually connected
            // (netStatus == 1 or 3). Calling CloseSetup when netStatus == 0 or 2 shuts down the
            // switch's 10.22.22.1 soft-AP on a failed attempt and prevents retrying!
            if (netStatus == 1 || netStatus == 3) {
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
                    logStep("[STEP 6 CLOSE] CloseSetup -> $closeStatus")
                } catch (e: Exception) {
                    closeStatus = "ap_closed"
                    logStep("[STEP 6 CLOSE] CloseSetup -> ap_closed (${e.javaClass.simpleName})")
                }

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
                    httpClient.newCall(doneReq).execute().use { resp ->
                        logStep("[STEP 6 DONE] SetSetupDoneStatus -> HTTP_${resp.code}")
                    }
                }
            } else {
                closeStatus = "kept_open_for_retry"
                logStep(
                    "[STEP 6 HOLD] NetworkStatus=$netStatus (not connected) • Skipping CloseSetup so 10.22.22.1 AP stays open for retry"
                )
            }
        }

        val overallSuccess = httpSuccess && (netStatus == 1 || netStatus == 3)
        val statusDesc = when {
            httpSuccess && netStatus == 2 ->
                "Password rejected by WeMo (Status=2): Wi-Fi password must be at least 8 characters"
            overallSuccess ->
                "Provisioned to '$ssid' (Status=$netStatus, Method $effectiveMethod, lengths=$effectiveAddLengths) • Setup AP closed, switch joining Home Wi-Fi"
            httpSuccess && netStatus == 0 ->
                "Switch did not join '$ssid' (NetworkStatus=0 after polling, Method=$effectiveMethod, lengths=$effectiveAddLengths, Ch=$effectiveChannel). Setup AP left open — screenshot the debug panel below or try another AES Method / Length option."
            else ->
                "WeMo Setup AP ($apHost:$resolvedPort) unreachable — connect phone Wi-Fi to WeMo.Light.xxx / WeMo.Mini.xxx first ($lastProbeDiagnosticSummary)"
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
                soapAction = "WiFiSetup1#ConnectHomeNetwork($ssid, Method $effectiveMethod, lengths=$effectiveAddLengths, Pairing=$lastPairingStatus)",
                statusText = statusDesc,
                xmlSnippet = connectBody.take(260),
                isLiveLanSuccess = overallSuccess
            ),
            closeSetupStatus = closeStatus,
            effectiveEncryptionMethod = effectiveMethod,
            addedLengthSuffix = effectiveAddLengths,
            setupDevice = updatedDescriptor,
            debugTraceLines = traceLines
        )
    }
}
