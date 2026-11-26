package org.suppressingfire.wemi.network

import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry

data class FetchRulesSoapResponse(
    val ruleDbVersion: Int,
    val ruleDbPath: String,
    val errorInfo: String
)

data class RulesCapabilityProbeResult(
    val capability: HardwareRulesCapability,
    val resolvedPort: Int,
    val scpdActions: Set<String>,
    val fetchRulesResponse: FetchRulesSoapResponse?,
    val reason: String,
    val logEntry: SoapLogEntry,
    val servedLightSetupXml: Boolean = false
)

data class StoreRulesResult(
    val success: Boolean,
    val verifiedVersion: Int,
    val errorInfo: String,
    val validation: HardwareRulesValidationResult,
    val logEntry: SoapLogEntry
)

/**
 * Pure UPnP XML parsers/builders and armored `timesync1` / `StoreRules` transaction helpers for
 * Belkin on-device rules management.
 */
object HardwareRulesProtocol {

    private val ACTION_NAME_REGEX = Regex("<action>\\s*<name>\\s*([^<\\s]+)\\s*</name>", RegexOption.IGNORE_CASE)
    private val RULE_DB_VERSION_REGEX = Regex("<ruleDbVersion(?:\\s*/\\s*>|>([^<]*)</ruleDbVersion>)", RegexOption.IGNORE_CASE)
    private val RULE_DB_PATH_REGEX = Regex("<ruleDbPath(?:\\s*/\\s*>|>([^<]*)</ruleDbPath>)", RegexOption.IGNORE_CASE)
    private val ERROR_INFO_REGEX = Regex("<errorInfo(?:\\s*/\\s*>|>([^<]*)</errorInfo>)", RegexOption.IGNORE_CASE)
    private val DEVICE_TIME_REGEX = Regex("<DeviceTime>(\\d+)</DeviceTime>", RegexOption.IGNORE_CASE)
    private val SOAP_FAULT_REGEX = Regex("<(?:[A-Za-z0-9_.-]+:)?Fault\\b|<faultcode\\b", RegexOption.IGNORE_CASE)
    private val STORE_REJECT_REGEX = Regex("unsuccessful|fail|error", RegexOption.IGNORE_CASE)
    private val SUCCESS_WORD_REGEX = Regex("\\bsuccess(?:ful)?\\b", RegexOption.IGNORE_CASE)

    internal fun isSoapFault(xml: String): Boolean = SOAP_FAULT_REGEX.containsMatchIn(xml)

    fun parseScpdActionNames(scpdXml: String): Set<String> =
        ACTION_NAME_REGEX.findAll(scpdXml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toCollection(LinkedHashSet())

    fun parseFetchRulesResponse(xml: String): FetchRulesSoapResponse? {
        if (isSoapFault(xml)) return null
        val verMatch = RULE_DB_VERSION_REGEX.find(xml)
        val pathMatch = RULE_DB_PATH_REGEX.find(xml)
        if (verMatch == null && pathMatch == null && !xml.contains("FetchRulesResponse", ignoreCase = true)) {
            return null
        }
        val verStr = verMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val version = if (verStr.isEmpty()) 0 else (verStr.toIntOrNull() ?: return null)
        val path = pathMatch?.groupValues?.getOrNull(1)?.let(XmlAndSkuParser::unescapeXml)?.trim().orEmpty()
        val errorInfo = ERROR_INFO_REGEX.find(xml)?.groupValues?.getOrNull(1)?.let(XmlAndSkuParser::unescapeXml)?.trim().orEmpty()
        return FetchRulesSoapResponse(ruleDbVersion = version, ruleDbPath = path, errorInfo = errorInfo)
    }

    fun parseStoreRulesErrorInfo(xml: String): String =
        ERROR_INFO_REGEX.find(xml)?.groupValues?.getOrNull(1)?.let(XmlAndSkuParser::unescapeXml)?.trim().orEmpty()

    fun isStoreRulesSuccess(errorInfo: String): Boolean {
        val trimmed = errorInfo.trim()
        if (trimmed.isEmpty()) return true
        if (isSoapFault(trimmed) || STORE_REJECT_REGEX.containsMatchIn(trimmed)) return false
        return SUCCESS_WORD_REGEX.containsMatchIn(trimmed)
    }

    fun buildStoreRulesSoapEnvelope(newVersion: Int, base64Zip: String): String =
        XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_RULES,
            action = "StoreRules",
            innerXml = """
                <ruleDbVersion>$newVersion</ruleDbVersion>
                <processDb>1</processDb>
                <ruleDbBody>&lt;![CDATA[$base64Zip]]&gt;</ruleDbBody>
            """.trimIndent()
        )

    fun formatFirmwareTimezoneOffset(offsetMinutes: Int): String {
        val sign = if (offsetMinutes < 0) "-" else ""
        val absMin = abs(offsetMinutes)
        val hours = absMin / 60
        val mins = absMin % 60
        val frac = (mins * 100) / 60
        return String.format(Locale.US, "%s%02d.%02d", sign, hours, frac)
    }

    internal fun ensureLeadingSlash(path: String): String =
        if (path.startsWith('/')) path else "/$path"

    internal fun resolveRuleDbUrl(ipAddress: String, port: Int, ruleDbPath: String): String {
        val trimmed = ruleDbPath.trim()
        val extractedPath = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            runCatching {
                val uri = URI(trimmed)
                val rawPath = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/rules.db"
                if (!uri.rawQuery.isNullOrBlank()) "$rawPath?${uri.rawQuery}" else rawPath
            }.getOrDefault("/rules.db")
        } else {
            trimmed.ifBlank { "/rules.db" }
        }
        return "http://$ipAddress:$port${ensureLeadingSlash(extractedPath)}"
    }

    internal fun nowTime(): String = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    internal fun executeGetDeviceTime(
        httpClient: LanHttpClient,
        ipAddress: String,
        port: Int,
        nowUtcSecondsProvider: () -> Long,
        deviceName: String = "",
        onFailureLog: ((SoapLogEntry) -> Unit)? = null
    ): HardwareDeviceTimeInfo? {
        val url = "http://$ipAddress:$port/upnp/control/timesync1"
        val envelope = XmlAndSkuParser.buildSoapEnvelope(XmlAndSkuParser.URN_TIMESYNC, "GetDeviceTime")
        return try {
            val resp = httpClient.postXml(
                url = url,
                soapBody = envelope,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_TIMESYNC, "GetDeviceTime")
            )
            if (!resp.isSuccessful) {
                onFailureLog?.invoke(
                    SoapLogEntry(
                        timestamp = nowTime(),
                        deviceName = deviceName,
                        endpoint = url,
                        soapAction = "timesync1#GetDeviceTime",
                        statusText = "[ERR] HTTP ${resp.code}",
                        xmlSnippet = resp.body.take(180),
                        isLiveLanSuccess = false
                    )
                )
                return null
            }
            val deviceUtc = DEVICE_TIME_REGEX.find(resp.body)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (deviceUtc == null) {
                onFailureLog?.invoke(
                    SoapLogEntry(
                        timestamp = nowTime(),
                        deviceName = deviceName,
                        endpoint = url,
                        soapAction = "timesync1#GetDeviceTime",
                        statusText = "[ERR] Missing <DeviceTime>",
                        xmlSnippet = resp.body.take(180),
                        isLiveLanSuccess = false
                    )
                )
                return null
            }
            val nowUtc = nowUtcSecondsProvider()
            val drift = deviceUtc - nowUtc
            val year2020Epoch = 1577836800L
            HardwareDeviceTimeInfo(
                deviceUtcSeconds = deviceUtc,
                driftSeconds = drift,
                needsTimeSync = deviceUtc < year2020Epoch || abs(drift) > 120L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailureLog?.invoke(
                SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = deviceName,
                    endpoint = url,
                    soapAction = "timesync1#GetDeviceTime",
                    statusText = "[ERR] ${e.javaClass.simpleName}",
                    xmlSnippet = e.message.orEmpty().take(180),
                    isLiveLanSuccess = false
                )
            )
            null
        }
    }

    internal fun executeSyncDeviceTime(
        httpClient: LanHttpClient,
        device: DeviceEntity,
        activePort: Int,
        nowUtcMillis: Long,
        timeZone: TimeZone
    ): SoapLogEntry {
        val url = "http://${device.ipAddress}:$activePort/upnp/control/timesync1"
        val utcSec = nowUtcMillis / 1000L
        val standardOffsetMin = timeZone.rawOffset / 60_000
        val tzFormatted = formatFirmwareTimezoneOffset(standardOffsetMin)
        // Belkin firmware expects inverted <dst>: 0 when currently observing DST, 1 when in standard time.
        val belkinDstFlag = if (timeZone.inDaylightTime(Date(nowUtcMillis))) 0 else 1
        val dstSupported = if (timeZone.useDaylightTime()) 1 else 0
        val envelope = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_TIMESYNC,
            action = "TimeSync",
            innerXml = """
                <UTC>$utcSec</UTC>
                <TimeZone>$tzFormatted</TimeZone>
                <dst>$belkinDstFlag</dst>
                <DstSupported>$dstSupported</DstSupported>
            """.trimIndent()
        )
        return try {
            val resp = httpClient.postXml(
                url = url,
                soapBody = envelope,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_TIMESYNC, "TimeSync")
            )
            SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = url,
                soapAction = "timesync1#TimeSync($tzFormatted)",
                statusText = if (resp.isSuccessful) "HTTP ${resp.code} OK" else "[ERR] HTTP ${resp.code}",
                xmlSnippet = resp.body.take(200),
                isLiveLanSuccess = resp.isSuccessful
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = url,
                soapAction = "timesync1#TimeSync",
                statusText = "[ERR] ${e.javaClass.simpleName}",
                xmlSnippet = e.message.orEmpty(),
                isLiveLanSuccess = false
            )
        }
    }

    internal fun executeStoreValidatedRulesDb(
        httpClient: LanHttpClient,
        device: DeviceEntity,
        activePort: Int,
        sqliteFile: File,
        zipEntryName: String,
        currentDbVersion: Int,
        storeHttpClient: LanHttpClient = httpClient,
        isRawBackupRestore: Boolean = false,
        mutatedRuleIds: Set<Int> = emptySet(),
        inspectedOwnerUdn: String? = null,
        actionLabelPrefix: String = "rules1#StoreRules"
    ): StoreRulesResult {
        val (base64Zip, validation) = HardwareRulesSafetyValidator.validateAndPackToBase64Zip(
            sqliteFile = sqliteFile,
            zipEntryName = zipEntryName,
            isRawBackupRestore = isRawBackupRestore,
            mutatedRuleIds = mutatedRuleIds,
            inspectedOwnerUdn = inspectedOwnerUdn
        )
        val targetVersion = currentDbVersion + 1
        val activeUrl = "http://${device.ipAddress}:$activePort/upnp/control/rules1"
        if (base64Zip == null || !validation.isSafeToStore) {
            val reason = validation.issues.joinToString("; ").ifBlank { "Validation rejected DB" }
            return StoreRulesResult(
                success = false,
                verifiedVersion = currentDbVersion,
                errorInfo = reason,
                validation = validation,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = device.name,
                    endpoint = activeUrl,
                    soapAction = "$actionLabelPrefix (Blocked)",
                    statusText = "[ERR] Blocked by anti-brick validator",
                    xmlSnippet = reason,
                    isLiveLanSuccess = false
                )
            )
        }

        val envelope = buildStoreRulesSoapEnvelope(targetVersion, base64Zip)
        val resp = try {
            storeHttpClient.postXml(
                url = activeUrl,
                soapBody = envelope,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_RULES, "StoreRules")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return StoreRulesResult(
                success = false,
                verifiedVersion = currentDbVersion,
                errorInfo = e.message ?: e.javaClass.simpleName,
                validation = validation,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = device.name,
                    endpoint = activeUrl,
                    soapAction = "$actionLabelPrefix(v$targetVersion)",
                    statusText = "[ERR] Transport failed: ${e.javaClass.simpleName}",
                    xmlSnippet = e.message.orEmpty(),
                    isLiveLanSuccess = false
                )
            )
        }

        val hasSoapFault = isSoapFault(resp.body)
        val errorInfo = parseStoreRulesErrorInfo(resp.body)
        if (!resp.isSuccessful || hasSoapFault || !isStoreRulesSuccess(errorInfo)) {
            val rejectMsg = errorInfo.ifBlank { if (hasSoapFault) "SOAP Fault" else "HTTP ${resp.code}" }
            return StoreRulesResult(
                success = false,
                verifiedVersion = currentDbVersion,
                errorInfo = rejectMsg,
                validation = validation,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = device.name,
                    endpoint = activeUrl,
                    soapAction = "$actionLabelPrefix(v$targetVersion)",
                    statusText = "[ERR] Rejected: $rejectMsg",
                    xmlSnippet = resp.body.take(220),
                    isLiveLanSuccess = false
                )
            )
        }

        val verifyResp = try {
            httpClient.postXml(
                url = activeUrl,
                soapBody = XmlAndSkuParser.buildSoapEnvelope(XmlAndSkuParser.URN_RULES, "FetchRules"),
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_RULES, "FetchRules")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val confirmedVersion = verifyResp?.takeIf { it.isSuccessful }?.let { parseFetchRulesResponse(it.body)?.ruleDbVersion }
        val readBackOk = confirmedVersion != null && confirmedVersion >= targetVersion
        val mismatchDetail = "expected >= v$targetVersion, got v${confirmedVersion ?: -1}"

        return StoreRulesResult(
            success = readBackOk,
            verifiedVersion = confirmedVersion ?: currentDbVersion,
            errorInfo = if (readBackOk) errorInfo else "Read-back mismatch ($mismatchDetail)",
            validation = validation,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = activeUrl,
                soapAction = "$actionLabelPrefix(v$targetVersion)",
                statusText = if (readBackOk) "Verified DB v$confirmedVersion" else "[ERR] Read-back mismatch ($mismatchDetail)",
                xmlSnippet = if (readBackOk) errorInfo else mismatchDetail,
                isLiveLanSuccess = readBackOk
            )
        )
    }

    internal data class ResolvedSwitchEndpoint(
        val port: Int,
        val servedLightSetupXml: Boolean = false,
        val setupXmlUdn: String = ""
    )

    private sealed class DirectPortResult {
        data class Matched(val endpoint: ResolvedSwitchEndpoint) : DirectPortResult()
        object IdentityMismatch : DirectPortResult()
        object HostOffline : DirectPortResult()
        object PortFailed : DirectPortResult()
    }

    private val SERIAL_TAG_REGEX = Regex("<serialNumber>([^<]+)</serialNumber>", RegexOption.IGNORE_CASE)
    private val UDN_TAG_REGEX = Regex("<UDN>([^<]+)</UDN>", RegexOption.IGNORE_CASE)

    internal suspend fun resolveWorkingEndpoint(
        httpClient: LanHttpClient,
        device: DeviceEntity,
        portProber: (suspend (ipAddress: String, preferredPort: Int, expectedIdentity: String?) -> Int?)?
    ): ResolvedSwitchEndpoint? {
        val expectedId = LanRuntimeUtils.expectedHardwareIdentity(device.serialNumber, device.udn).ifEmpty { null }
        val isLoopback = device.ipAddress == "127.0.0.1" || device.ipAddress.equals("localhost", ignoreCase = true)
        if (device.port in 1..65535) {
            when (val direct = verifyDirectPort(httpClient, device.ipAddress, device.port, expectedId)) {
                is DirectPortResult.Matched -> return direct.endpoint
                DirectPortResult.HostOffline -> return null
                DirectPortResult.IdentityMismatch -> if (!isLoopback) return null
                DirectPortResult.PortFailed -> Unit
            }
        }
        val probedPort = portProber?.invoke(device.ipAddress, device.port, expectedId) ?: return null
        return ResolvedSwitchEndpoint(port = probedPort)
    }

    internal fun fetchCanonicalUdnOnPort(httpClient: LanHttpClient, ipAddress: String, port: Int): String? {
        for (path in listOf("/setup.xml", "/Lightsetup.xml")) {
            try {
                val resp = httpClient.get("http://$ipAddress:$port$path")
                if (resp.isSuccessful) {
                    val udn = LanRuntimeUtils.canonicalHardwareUdn(
                        UDN_TAG_REGEX.find(resp.body)?.groupValues?.getOrNull(1)?.trim()
                    )
                    if (udn.isNotBlank()) return udn
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Try fallback path
            }
        }
        return null
    }

    private fun verifyDirectPort(
        httpClient: LanHttpClient,
        ipAddress: String,
        port: Int,
        expectedId: String?
    ): DirectPortResult {
        val cleanExpected = expectedId?.trim().orEmpty()
        val cleanExpectedUdn = LanRuntimeUtils.canonicalHardwareUdn(cleanExpected)
        val isExpectedUdn = cleanExpected.startsWith("uuid:", ignoreCase = true)
        val verifyIdentity = if (isExpectedUdn) cleanExpectedUdn.isNotEmpty() else !LanRuntimeUtils.isPlaceholderSerial(cleanExpected)

        for (path in listOf("/setup.xml", "/Lightsetup.xml")) {
            try {
                val resp = httpClient.get("http://$ipAddress:$port$path")
                if (!resp.isSuccessful) continue
                val xml = resp.body
                val isTarget = xml.contains("Belkin", ignoreCase = true) ||
                    xml.contains("WeMo", ignoreCase = true) ||
                    xml.contains("WeMi", ignoreCase = true) ||
                    xml.contains("basicevent", ignoreCase = true)
                if (!isTarget) continue
                val realXmlSerial = SERIAL_TAG_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim()
                    .takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty()
                val cleanXmlUdn = LanRuntimeUtils.canonicalHardwareUdn(UDN_TAG_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim())
                if (verifyIdentity && (realXmlSerial.isNotEmpty() || cleanXmlUdn.isNotEmpty())) {
                    val matches = (realXmlSerial.isNotEmpty() && (
                        realXmlSerial.equals(cleanExpected, ignoreCase = true) ||
                            (isExpectedUdn && cleanExpectedUdn.endsWith("-$realXmlSerial", ignoreCase = true))
                        )) || (cleanXmlUdn.isNotEmpty() && (
                        cleanXmlUdn.equals(cleanExpectedUdn, ignoreCase = true) ||
                            (!isExpectedUdn && cleanXmlUdn.contains(cleanExpected, ignoreCase = true))
                        ))
                    if (!matches) return DirectPortResult.IdentityMismatch
                }
                return DirectPortResult.Matched(
                    ResolvedSwitchEndpoint(port, servedLightSetupXml = path == "/Lightsetup.xml", setupXmlUdn = cleanXmlUdn)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: java.net.NoRouteToHostException) {
                return DirectPortResult.HostOffline
            } catch (_: Exception) {
                return DirectPortResult.PortFailed
            }
        }
        return DirectPortResult.PortFailed
    }
}
