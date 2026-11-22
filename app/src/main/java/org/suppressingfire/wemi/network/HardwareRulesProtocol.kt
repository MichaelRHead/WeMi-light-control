package org.suppressingfire.wemi.network

import java.io.File
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
    val logEntry: SoapLogEntry
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
    private val RULE_DB_VERSION_REGEX = Regex("<ruleDbVersion>([^<]*)</ruleDbVersion>", RegexOption.IGNORE_CASE)
    private val RULE_DB_PATH_REGEX = Regex("<ruleDbPath>([^<]*)</ruleDbPath>", RegexOption.IGNORE_CASE)
    private val ERROR_INFO_REGEX = Regex("<errorInfo>([^<]*)</errorInfo>", RegexOption.IGNORE_CASE)
    private val DEVICE_TIME_REGEX = Regex("<DeviceTime>(\\d+)</DeviceTime>", RegexOption.IGNORE_CASE)

    fun parseScpdActionNames(scpdXml: String): Set<String> =
        ACTION_NAME_REGEX.findAll(scpdXml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toCollection(LinkedHashSet())

    fun parseFetchRulesResponse(xml: String): FetchRulesSoapResponse? {
        if (xml.contains("<s:Fault", ignoreCase = true) || xml.contains("<faultcode", ignoreCase = true)) {
            return null
        }
        val verStr = RULE_DB_VERSION_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val version = verStr.toIntOrNull() ?: return null
        val path = RULE_DB_PATH_REGEX.find(xml)?.groupValues?.getOrNull(1)?.let(XmlAndSkuParser::unescapeXml)?.trim().orEmpty()
        val errorInfo = ERROR_INFO_REGEX.find(xml)?.groupValues?.getOrNull(1)?.let(XmlAndSkuParser::unescapeXml)?.trim().orEmpty()
        return FetchRulesSoapResponse(ruleDbVersion = version, ruleDbPath = path, errorInfo = errorInfo)
    }

    fun parseStoreRulesErrorInfo(xml: String): String =
        ERROR_INFO_REGEX.find(xml)?.groupValues?.getOrNull(1)?.let(XmlAndSkuParser::unescapeXml)?.trim().orEmpty()

    fun isStoreRulesSuccess(errorInfo: String): Boolean =
        errorInfo.contains("successful", ignoreCase = true)

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

    internal fun resolveRuleDbUrl(ipAddress: String, port: Int, ruleDbPath: String): String =
        if (ruleDbPath.startsWith("http://", ignoreCase = true) || ruleDbPath.startsWith("https://", ignoreCase = true)) {
            ruleDbPath
        } else {
            "http://$ipAddress:$port${ensureLeadingSlash(ruleDbPath)}"
        }

    internal fun nowTime(): String = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    internal fun executeGetDeviceTime(
        httpClient: LanHttpClient,
        ipAddress: String,
        port: Int,
        nowUtcSecondsProvider: () -> Long
    ): HardwareDeviceTimeInfo? {
        val url = "http://$ipAddress:$port/upnp/control/timesync1"
        val envelope = XmlAndSkuParser.buildSoapEnvelope(XmlAndSkuParser.URN_TIMESYNC, "GetDeviceTime")
        return try {
            val resp = httpClient.postXml(
                url = url,
                soapBody = envelope,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_TIMESYNC, "GetDeviceTime")
            )
            if (!resp.isSuccessful) return null
            val deviceUtc = DEVICE_TIME_REGEX.find(resp.body)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
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
        } catch (_: Exception) {
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
        val offsetMin = timeZone.getOffset(nowUtcMillis) / 60_000
        val tzFormatted = formatFirmwareTimezoneOffset(offsetMin)
        val inDst = if (timeZone.inDaylightTime(Date(nowUtcMillis))) 1 else 0
        val dstSupported = if (timeZone.useDaylightTime()) 1 else 0
        val envelope = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_TIMESYNC,
            action = "TimeSync",
            innerXml = """
                <UTC>$utcSec</UTC>
                <TimeZone>$tzFormatted</TimeZone>
                <dst>$inDst</dst>
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
                statusText = if (resp.isSuccessful) "HTTP ${resp.code} OK" else "HTTP ${resp.code}",
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
                statusText = "Failed: ${e.javaClass.simpleName}",
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
        currentDbVersion: Int
    ): StoreRulesResult {
        val (base64Zip, validation) = HardwareRulesSafetyValidator.validateAndPackToBase64Zip(sqliteFile, zipEntryName)
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
                    soapAction = "rules1#StoreRules (Blocked)",
                    statusText = "Blocked by anti-brick validator",
                    xmlSnippet = reason,
                    isLiveLanSuccess = false
                )
            )
        }

        val envelope = buildStoreRulesSoapEnvelope(targetVersion, base64Zip)
        val resp = try {
            httpClient.postXml(
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
                    soapAction = "rules1#StoreRules(v$targetVersion)",
                    statusText = "Transport failed: ${e.javaClass.simpleName}",
                    xmlSnippet = e.message.orEmpty(),
                    isLiveLanSuccess = false
                )
            )
        }

        val errorInfo = parseStoreRulesErrorInfo(resp.body)
        if (!resp.isSuccessful || !isStoreRulesSuccess(errorInfo)) {
            return StoreRulesResult(
                success = false,
                verifiedVersion = currentDbVersion,
                errorInfo = errorInfo.ifBlank { "HTTP ${resp.code}" },
                validation = validation,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = device.name,
                    endpoint = activeUrl,
                    soapAction = "rules1#StoreRules(v$targetVersion)",
                    statusText = "Rejected: ${errorInfo.ifBlank { "HTTP ${resp.code}" }}",
                    xmlSnippet = resp.body.take(220),
                    isLiveLanSuccess = false
                )
            )
        }

        val verifyResp = runCatching {
            httpClient.postXml(
                url = activeUrl,
                soapBody = XmlAndSkuParser.buildSoapEnvelope(XmlAndSkuParser.URN_RULES, "FetchRules"),
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_RULES, "FetchRules")
            )
        }.getOrNull()
        val confirmedVersion = verifyResp?.takeIf { it.isSuccessful }?.let { parseFetchRulesResponse(it.body)?.ruleDbVersion }
        val readBackOk = confirmedVersion != null && confirmedVersion >= targetVersion

        return StoreRulesResult(
            success = readBackOk,
            verifiedVersion = confirmedVersion ?: currentDbVersion,
            errorInfo = if (readBackOk) errorInfo else "Read-back mismatch (expected >= v$targetVersion, got v${confirmedVersion ?: -1})",
            validation = validation,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = activeUrl,
                soapAction = "rules1#StoreRules(v$targetVersion)",
                statusText = if (readBackOk) "Verified DB v$confirmedVersion" else "Read-back verification failed",
                xmlSnippet = errorInfo,
                isLiveLanSuccess = readBackOk
            )
        )
    }
}
