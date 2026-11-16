package org.suppressingfire.wemi.network

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.suppressingfire.wemi.data.SoapLogEntry

/**
 * Zero-cloud smart-switch Wi-Fi setup provisioner (`10.22.22.1`).
 *
 * Key reliability & diagnostic guarantees:
 * - Delegates 9-port `/setup.xml` / `/Lightsetup.xml` + `GetMetaInfo` probing and `WiFiSetup1#GetApList`
 *   2.4 GHz site-survey scanning (`8.5s` read timeout) to [SetupApProber].
 * - Requires `NetworkStatus == 1` (or `3` handshake followed by AP close) for success; never falsely
 *   reports `NetworkStatus == 0` as success or calls `CloseSetup` when `NetworkStatus == 0`,
 *   keeping the switch's `10.22.22.1` soft-AP alive for inspection and retry.
 */
class ProvisionerClient(
    private val appContext: Context? = null,
    private val defaultApHost: String = "10.22.22.1"
) {
    private val wifiSocketFactory = NetworkMonitor.createWifiBoundSocketFactory(appContext)

    private val httpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 1500,
        readTimeoutMs = 8500
    )

    private val setupApProber = SetupApProber(
        wifiSocketFactory = wifiSocketFactory,
        httpClient = httpClient
    )

    val lastProbeDiagnosticSummary: String
        get() = setupApProber.lastProbeDiagnosticSummary

    val lastApScanDiagnosticSummary: String
        get() = setupApProber.lastApScanDiagnosticSummary

    companion object {
        const val METHOD_2_RTOS_SALT_SUFFIX = PasswordCrypto.METHOD_2_RTOS_SALT_SUFFIX
        const val METHOD_3_BINARY_OPTION_EXTRA = PasswordCrypto.METHOD_3_BINARY_OPTION_EXTRA
    }

    private fun nowTime(): String =
        SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    private fun extractXmlTag(xml: String, tag: String): String? =
        setupApProber.extractXmlTag(xml, tag)

    suspend fun probeSetupDevice(
        apHost: String = defaultApHost,
        preferredPort: Int = 49152
    ): SetupDeviceDescriptor? = setupApProber.probeSetupDevice(apHost, preferredPort)

    suspend fun resolveSetupPort(apHost: String = defaultApHost, preferredPort: Int = 49152): Int =
        probeSetupDevice(apHost, preferredPort)?.port ?: preferredPort

    suspend fun fetchAvailableApList(
        apHost: String = defaultApHost,
        apPort: Int = 49152,
        knownDescriptor: SetupDeviceDescriptor? = null
    ): List<ScannedAp> = setupApProber.fetchAvailableApList(apHost, apPort, knownDescriptor)

    fun parseApListXml(xml: String): List<ScannedAp> =
        XmlAndSkuParser.parseApListXml(xml)

    fun buildKeyData(
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1
    ): String = PasswordCrypto.buildKeyData(macAddress, serialNumber, encryptionMethod)

    fun encryptWifiPassword(
        plainPassword: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        addPasswordLengths: Boolean? = null
    ): String = PasswordCrypto.encryptWifiPassword(
        plainPassword, macAddress, serialNumber, encryptionMethod, addPasswordLengths
    )

    fun decryptWifiPassword(
        encryptedPayload: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        hasLengthSuffix: Boolean = (encryptionMethod != 2)
    ): String = PasswordCrypto.decryptWifiPassword(
        encryptedPayload, macAddress, serialNumber, encryptionMethod, hasLengthSuffix
    )

    /**
     * Provisions a factory-reset smart switch at [apHost]:[apPort] onto the target 2.4 GHz
     * home Wi-Fi network and emits live step-by-step debug telemetry.
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
        knownDescriptor: SetupDeviceDescriptor? = null,
        knownScannedAps: List<ScannedAp> = emptyList(),
        onProgress: ((String) -> Unit)? = null
    ): ProvisionResult = withContext(Dispatchers.IO) {
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

        val mac = descriptor?.macAddress?.ifBlank { LanRuntimeUtils.DEFAULT_FALLBACK_MAC }
            ?: LanRuntimeUtils.DEFAULT_FALLBACK_MAC
        val serial = descriptor?.serialNumber?.ifBlank { LanRuntimeUtils.DEFAULT_PLACEHOLDER_SERIAL }
            ?: LanRuntimeUtils.DEFAULT_PLACEHOLDER_SERIAL

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
            logStep("[WARN] Router AuthMode is 'Unknown' (likely WPA3-only; switch hardware requires 2.4 GHz WPA2-PSK/AES)!")
        }

        val effectiveMethod = if (encryptionMethod in 1..3) {
            encryptionMethod
        } else {
            descriptor?.recommendedEncryptionMethod ?: 1
        }
        val effectiveAddLengths = addPasswordLengths ?: (effectiveMethod != 2)

        if (!customFriendlyName.isNullOrBlank() && descriptor != null) {
            runCatching {
                val cleanFriendly = customFriendlyName.trim()
                val renameBody = XmlAndSkuParser.buildSoapEnvelope(
                    serviceUrn = XmlAndSkuParser.URN_BASIC_EVENT,
                    action = "ChangeFriendlyName",
                    innerXml = "<FriendlyName>${XmlAndSkuParser.escapeXml(cleanFriendly)}</FriendlyName>"
                )
                val resp = httpClient.postXml(
                    url = "http://$apHost:$resolvedPort/upnp/control/basicevent1",
                    soapBody = renameBody,
                    soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_BASIC_EVENT, "ChangeFriendlyName")
                )
                logStep("[RENAME] ChangeFriendlyName(\"$cleanFriendly\") -> HTTP_${resp.code}")
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

        val connectBody = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_WIFI_SETUP,
            action = "ConnectHomeNetwork",
            innerXml = """
                <ssid>${XmlAndSkuParser.escapeXml(ssid)}</ssid>
                <auth>${XmlAndSkuParser.escapeXml(resolvedAuth)}</auth>
                <password>${XmlAndSkuParser.escapeXml(encryptedPwd)}</password>
                <encrypt>${XmlAndSkuParser.escapeXml(resolvedEncrypt)}</encrypt>
                <channel>$effectiveChannel</channel>
            """.trimIndent()
        )

        val endpoint = "http://$apHost:$resolvedPort/upnp/control/WiFiSetup1"
        var httpSuccess = false
        var lastPairingStatus = ""

        val isLoopbackHost = apHost == "127.0.0.1" || apHost.equals("localhost", ignoreCase = true)
        val effectivePollDelayMs = if (isLoopbackHost && statusPollDelayMs == 1000L) 35L else statusPollDelayMs

        repeat(2) { attempt ->
            val sendStart = System.currentTimeMillis()
            try {
                val resp = httpClient.postXml(
                    url = endpoint,
                    soapBody = connectBody,
                    soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_WIFI_SETUP, "ConnectHomeNetwork")
                )
                val elapsed = System.currentTimeMillis() - sendStart
                val pairing = extractXmlTag(resp.body, "PairingStatus").orEmpty()
                if (pairing.isNotEmpty()) lastPairingStatus = pairing
                logStep(
                    "[STEP 5 SEND #${attempt + 1}] ConnectHomeNetwork -> HTTP_${resp.code} (${elapsed}ms) PairingStatus=${pairing.ifEmpty { "n/a" }}"
                )
                if (resp.isSuccessful) {
                    httpSuccess = true
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - sendStart
                logStep(
                    "[STEP 5 SEND #${attempt + 1}] ERROR: ${e.javaClass.simpleName}: ${e.message?.take(60)} (${elapsed}ms)"
                )
                if (descriptor == null && attempt == 0) return@repeat
            }
            if (attempt == 0 && httpSuccess) delay(if (isLoopbackHost) 15L else 100L)
        }

        var netStatus = if (httpSuccess) 0 else -1
        var closeStatus = ""

        if (httpSuccess) {
            val statusBody = XmlAndSkuParser.buildSoapEnvelope(
                serviceUrn = XmlAndSkuParser.URN_WIFI_SETUP,
                action = "GetNetworkStatus"
            )
            val statusAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_WIFI_SETUP, "GetNetworkStatus")

            suspend fun pollOnce(pollIndex: Int): Int? {
                val pollStart = System.currentTimeMillis()
                return try {
                    val resp = httpClient.postXml(endpoint, statusBody, statusAction)
                    val elapsed = System.currentTimeMillis() - pollStart
                    val code = Regex("<NetworkStatus>(\\d+)</NetworkStatus>", RegexOption.IGNORE_CASE)
                        .find(resp.body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    logStep("[POLL #$pollIndex] GetNetworkStatus -> Status=${code ?: "HTTP_${resp.code}"} (${elapsed}ms)")
                    code
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - pollStart
                    logStep("[POLL #$pollIndex] GetNetworkStatus -> ${e.javaClass.simpleName} (${elapsed}ms)")
                    null
                }
            }

            delay(minOf(350L, effectivePollDelayMs))
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
                if (polls < maxStatusPolls) delay(effectivePollDelayMs)
            }

            var extraHandshakeLoops = 3
            while (netStatus == 3 && extraHandshakeLoops > 0) {
                delay(effectivePollDelayMs)
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
                    val closeBody = XmlAndSkuParser.buildSoapEnvelope(
                        serviceUrn = XmlAndSkuParser.URN_WIFI_SETUP,
                        action = "CloseSetup"
                    )
                    val resp = httpClient.postXml(
                        url = endpoint,
                        soapBody = closeBody,
                        soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_WIFI_SETUP, "CloseSetup")
                    )
                    closeStatus = extractXmlTag(resp.body, "status").orEmpty().ifBlank {
                        if (resp.isSuccessful) "success" else "HTTP ${resp.code}"
                    }
                    logStep("[STEP 6 CLOSE] CloseSetup -> $closeStatus")
                } catch (e: Exception) {
                    closeStatus = "ap_closed"
                    logStep("[STEP 6 CLOSE] CloseSetup -> ap_closed (${e.javaClass.simpleName})")
                }

                runCatching {
                    val doneBody = XmlAndSkuParser.buildSoapEnvelope(
                        serviceUrn = XmlAndSkuParser.URN_BASIC_EVENT,
                        action = "SetSetupDoneStatus"
                    )
                    val resp = httpClient.postXml(
                        url = "http://$apHost:$resolvedPort/upnp/control/basicevent1",
                        soapBody = doneBody,
                        soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_BASIC_EVENT, "SetSetupDoneStatus")
                    )
                    logStep("[STEP 6 DONE] SetSetupDoneStatus -> HTTP_${resp.code}")
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
                "Password rejected by switch (Status=2): Wi-Fi password must be at least 8 characters"
            overallSuccess ->
                "Provisioned to '$ssid' (Status=$netStatus, Method $effectiveMethod, lengths=$effectiveAddLengths) • Setup AP closed, switch joining Home Wi-Fi"
            httpSuccess && netStatus == 0 ->
                "Switch did not join '$ssid' (NetworkStatus=0 after polling, Method=$effectiveMethod, lengths=$effectiveAddLengths, Ch=$effectiveChannel). Setup AP left open — screenshot the debug panel below or try another AES Method / Length option."
            else ->
                "Setup AP ($apHost:$resolvedPort) unreachable — connect phone Wi-Fi to the switch's 10.22.22.1 setup network first ($lastProbeDiagnosticSummary)"
        }

        val updatedDescriptor = if (descriptor != null && !customFriendlyName.isNullOrBlank()) {
            descriptor.copy(friendlyName = customFriendlyName.trim())
        } else {
            descriptor
        }

        ProvisionResult(
            success = overallSuccess,
            networkStatusCode = netStatus.coerceAtLeast(0),
            statusMessage = statusDesc,
            encryptedPasswordPayload = encryptedPwd,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = updatedDescriptor?.friendlyName ?: "Setup AP ($apHost)",
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
