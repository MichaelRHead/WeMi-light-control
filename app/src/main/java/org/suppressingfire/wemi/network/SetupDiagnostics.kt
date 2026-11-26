package org.suppressingfire.wemi.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.suppressingfire.wemi.BuildConfig
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.widget.WidgetRemoteViewsBuilder
import org.suppressingfire.wemi.widget.WidgetTargetConfig
import org.suppressingfire.wemi.widget.WidgetTargetType

/**
 * Process-scoped, per-`Application` bounded ring buffer for live UPnP/SOAP/GENA and Home Screen
 * widget telemetry logs. Keeps headless `WeMiSwitchWidgetProvider` / `WidgetActionExecutor`
 * traffic visible in `More` -> `UPnP / SOAP Packet Log` and `wemi-telemetry-latest.txt` while
 * preserving strict per-`Application` isolation across Robolectric test runs.
 */
object SharedSoapLogStore {
    private const val MAX_ENTRIES = 50
    private val flowsByAppContext = WeakHashMap<Context, MutableStateFlow<List<SoapLogEntry>>>()

    fun flowFor(context: Context?): MutableStateFlow<List<SoapLogEntry>> {
        val appCtx = context?.applicationContext ?: return MutableStateFlow(emptyList())
        return synchronized(flowsByAppContext) {
            flowsByAppContext.getOrPut(appCtx) { MutableStateFlow(emptyList()) }
        }
    }

    fun append(context: Context?, entry: SoapLogEntry) {
        val appCtx = context?.applicationContext ?: return
        flowFor(appCtx).update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    fun clear(context: Context?) {
        val appCtx = context?.applicationContext ?: return
        synchronized(flowsByAppContext) {
            flowsByAppContext[appCtx]?.value = emptyList()
        }
    }
}

/**
 * Structured diagnostic snapshot and human/screenshot-readable formatter for zero-cloud
 * `10.22.22.1` Belkin WeMo Setup AP probing, 2.4 GHz AP scanning, and `WiFiSetup1` provisioning.
 */
data class SetupDebugSnapshot(
    val timestamp: String = "",
    val phoneLocalIp: String = "",
    val phoneGatewayIp: String = "",
    val phoneInterface: String = "wlan0",
    val isWemoSetupSubnet: Boolean = false,
    val isWifiSocketBound: Boolean = false,
    val isDemoMode: Boolean = false,
    val targetApHost: String = "10.22.22.1",
    val targetSsid: String = "",
    val probeSummary: String = "Not probed yet",
    val detectedDevice: SetupDeviceDescriptor? = null,
    val apScanSummary: String = "Not scanned yet",
    val scannedAps: List<ScannedAp> = emptyList(),
    val traceLines: List<String> = emptyList(),
    val isArcVmNatSubnet: Boolean = false,
    val dnsServersIpv4: List<String> = emptyList()
) {
    /**
     * Formats all setup telemetry into compact, high-signal lines designed to fit inside a single
     * phone screenshot in `ProvisionApDialog` (`provision_debug_panel`) or clipboard paste.
     */
    fun toDisplayLines(selectedSsid: String = targetSsid): List<String> {
        val effectiveSsid = selectedSsid.ifBlank { targetSsid }
        val lines = mutableListOf<String>()
        val modeTag = if (isDemoMode) "DEMO" else "REAL-LAN"
        val effectiveArcVmNat = isArcVmNatSubnet || LanRuntimeUtils.isCarrierGradeNatIpv4(phoneLocalIp)
        val arcVmPart = if (effectiveArcVmNat) " arcVmNat=true" else ""
        val dnsPart = if (dnsServersIpv4.isNotEmpty()) " dns=${dnsServersIpv4.joinToString(",")}" else ""
        lines.add(
            "[NET] mode=$modeTag ip=$phoneLocalIp ($phoneInterface) gw=$phoneGatewayIp setupSubnet=$isWemoSetupSubnet wifiBound=$isWifiSocketBound$arcVmPart$dnsPart"
        )
        lines.add("[PROBE] host=$targetApHost -> $probeSummary")
        if (detectedDevice != null) {
            val d = detectedDevice
            lines.add(
                "[XML] ${d.setupXmlPath} :${d.port} name=\"${d.friendlyName}\" model=${d.modelName} sku=${d.hardwareSku} fw=${d.firmwareVersion}"
            )
            lines.add(
                "[FLAGS] rtos=${if (d.isRtos) 1 else 0} iot=${if (d.isIot) 1 else 0} binaryOption=${if (d.binaryOption) 1 else 0} new_algo=${if (d.newAlgo) 1 else 0} -> autoMethod=${d.recommendedEncryptionMethod} autoLen=${d.recommendedAddLengths}"
            )
            lines.add(
                "[ID] mac=${d.macAddress} sn=${d.serialNumber} (src=${d.macAndSerialSource}) meta=\"${d.rawMetaInfo.ifBlank { "none" }}\""
            )
        } else {
            lines.add("[XML/ID] No setup descriptor read from $targetApHost")
        }
        lines.add("[SCAN] $apScanSummary")
        if (effectiveSsid.isNotBlank() && scannedAps.isNotEmpty()) {
            val match = scannedAps.firstOrNull { it.ssid.equals(effectiveSsid.trim(), ignoreCase = true) }
            if (match != null) {
                lines.add(
                    "[TARGET AP] \"${match.ssid}\" found in ApList: Ch=${match.channel} Auth=${match.authMode}/${match.encryptionType} Signal=${match.signalStrengthPercent}%"
                )
            } else {
                val sample = scannedAps.take(4).joinToString(", ") { "${it.ssid}(Ch${it.channel})" }
                lines.add(
                    "[TARGET AP] WARN: \"$effectiveSsid\" NOT in ApList! Seen: [$sample]"
                )
            }
        }
        traceLines.forEach { line ->
            lines.add(line)
        }
        return lines
    }

    fun toClipboardText(selectedSsid: String = targetSsid): String {
        return buildString {
            appendLine("=== WeMi Setup AP Diagnostic Report (v${BuildConfig.VERSION_NAME} [${BuildConfig.VERSION_CODE}] • $timestamp) ===")
            toDisplayLines(selectedSsid).forEach { appendLine(it) }
        }.trim()
    }
}

/**
 * Builds, persists (`files/wemi-telemetry-latest.txt`), and shares full device, widget & network
 * telemetry reports so users can either take an on-screen screenshot or export a file to share.
 */
object TelemetryFileExporter {
    const val DEFAULT_TELEMETRY_FILENAME = "wemi-telemetry-latest.txt"

    private val PASSWORD_TAG_REGEX = Regex(
        "(<(?:[a-z0-9_.-]+:)?password\\b[^>]*>)([\\s\\S]*?)(</(?:[a-z0-9_.-]+:)?password>)",
        RegexOption.IGNORE_CASE
    )
    private val ESCAPED_PASSWORD_TAG_REGEX = Regex(
        "(&lt;(?:[a-z0-9_.-]+:)?password\\b[^&]*?&gt;)([\\s\\S]*?)(&lt;/(?:[a-z0-9_.-]+:)?password&gt;)",
        RegexOption.IGNORE_CASE
    )
    private val UNCLOSED_PASSWORD_TAG_REGEX = Regex(
        "(<(?:[a-z0-9_.-]+:)?password\\b[^>]*>)([^<]*)$",
        RegexOption.IGNORE_CASE
    )
    private val UNCLOSED_ESCAPED_PASSWORD_TAG_REGEX = Regex(
        "(&lt;(?:[a-z0-9_.-]+:)?password\\b[^&]*?&gt;)((?:(?!&lt;/)[\\s\\S])*)$",
        RegexOption.IGNORE_CASE
    )
    private val RULE_DB_BODY_TAG_REGEX = Regex(
        "(<(?:[a-z0-9_.-]+:)?ruleDbBody\\b[^>]*>)([\\s\\S]*?)(</(?:[a-z0-9_.-]+:)?ruleDbBody>)",
        RegexOption.IGNORE_CASE
    )
    private val ESCAPED_RULE_DB_BODY_TAG_REGEX = Regex(
        "(&lt;(?:[a-z0-9_.-]+:)?ruleDbBody\\b[^&]*?&gt;)([\\s\\S]*?)(&lt;/(?:[a-z0-9_.-]+:)?ruleDbBody&gt;)",
        RegexOption.IGNORE_CASE
    )
    private val UNCLOSED_RULE_DB_BODY_TAG_REGEX = Regex(
        "(<(?:[a-z0-9_.-]+:)?ruleDbBody\\b[^>]*>)((?:(?!</(?:[a-z0-9_.-]+:)?ruleDbBody>)[\\s\\S])*)$",
        RegexOption.IGNORE_CASE
    )
    private val UNCLOSED_ESCAPED_RULE_DB_BODY_TAG_REGEX = Regex(
        "(&lt;(?:[a-z0-9_.-]+:)?ruleDbBody\\b[^&]*?&gt;)((?:(?!&lt;/(?:[a-z0-9_.-]+:)?ruleDbBody&gt;)[\\s\\S])*)$",
        RegexOption.IGNORE_CASE
    )

    private fun redactTagMatch(
        openTag: String,
        rawInner: String,
        closeTag: String = "",
        lengthLabel: String = "encLen"
    ): String {
        val inner = rawInner.trim()
        return if (inner.startsWith("[REDACTED", ignoreCase = true)) {
            "$openTag$rawInner$closeTag"
        } else {
            "$openTag[REDACTED $lengthLabel=${inner.length}]$closeTag"
        }
    }

    fun sanitizeXmlSnippet(raw: String, maxChars: Int = 220): String {
        val step1 = PASSWORD_TAG_REGEX.replace(raw) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        val step2 = ESCAPED_PASSWORD_TAG_REGEX.replace(step1) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        val step3 = UNCLOSED_PASSWORD_TAG_REGEX.replace(step2) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2])
        }
        val step4 = UNCLOSED_ESCAPED_PASSWORD_TAG_REGEX.replace(step3) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2])
        }
        val step5 = RULE_DB_BODY_TAG_REGEX.replace(step4) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2], m.groupValues[3], lengthLabel = "base64Len")
        }
        val step6 = ESCAPED_RULE_DB_BODY_TAG_REGEX.replace(step5) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2], m.groupValues[3], lengthLabel = "base64Len")
        }
        val step7 = UNCLOSED_RULE_DB_BODY_TAG_REGEX.replace(step6) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2], lengthLabel = "base64Len")
        }
        val redacted = UNCLOSED_ESCAPED_RULE_DB_BODY_TAG_REGEX.replace(step7) { m ->
            redactTagMatch(m.groupValues[1], m.groupValues[2], lengthLabel = "base64Len")
        }
        val compact = redacted.replace(Regex("\\s+"), " ").trim()
        return if (compact.length > maxChars) "${compact.take(maxChars - 1)}…" else compact
    }

    fun resolveExportSetupSnapshot(
        setupSnapshot: SetupDebugSnapshot,
        lanSnapshot: LanNetworkSnapshot = LanNetworkSnapshot(),
        isDemoMode: Boolean = setupSnapshot.isDemoMode
    ): SetupDebugSnapshot {
        val hasCapturedSession = setupSnapshot.timestamp.isNotBlank() &&
            setupSnapshot.phoneLocalIp.isNotBlank()
        return setupSnapshot.copy(
            timestamp = setupSnapshot.timestamp.ifBlank {
                SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
            },
            phoneLocalIp = setupSnapshot.phoneLocalIp.ifBlank { lanSnapshot.localIpv4 },
            phoneGatewayIp = setupSnapshot.phoneGatewayIp.ifBlank { lanSnapshot.gatewayIp },
            phoneInterface = if (hasCapturedSession) {
                setupSnapshot.phoneInterface.ifBlank { lanSnapshot.interfaceName }
            } else {
                lanSnapshot.interfaceName.ifBlank { setupSnapshot.phoneInterface }
            },
            isWemoSetupSubnet = if (hasCapturedSession) {
                setupSnapshot.isWemoSetupSubnet
            } else {
                lanSnapshot.isWemoSetupApSubnet
            },
            isWifiSocketBound = if (hasCapturedSession) {
                setupSnapshot.isWifiSocketBound
            } else {
                lanSnapshot.isWifiSocketBound
            },
            isDemoMode = if (hasCapturedSession) setupSnapshot.isDemoMode else isDemoMode,
            isArcVmNatSubnet = if (hasCapturedSession) {
                setupSnapshot.isArcVmNatSubnet || LanRuntimeUtils.isCarrierGradeNatIpv4(setupSnapshot.phoneLocalIp)
            } else {
                lanSnapshot.isArcVmNatSubnet || LanRuntimeUtils.isCarrierGradeNatIpv4(lanSnapshot.localIpv4)
            },
            dnsServersIpv4 = if (hasCapturedSession) {
                setupSnapshot.dnsServersIpv4
            } else {
                lanSnapshot.dnsServersIpv4.ifEmpty { setupSnapshot.dnsServersIpv4 }
            }
        )
    }

    fun buildFullTelemetryReport(
        setupSnapshot: SetupDebugSnapshot,
        selectedSsid: String = setupSnapshot.targetSsid,
        homes: List<HomeNetworkEntity> = emptyList(),
        devices: List<DeviceEntity> = emptyList(),
        soapLogs: List<SoapLogEntry> = emptyList(),
        widgets: List<WidgetTargetConfig> = emptyList(),
        hardwareRulesByDeviceId: Map<Int, DeviceHardwareRulesSnapshot> = emptyMap()
    ): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("=== WeMi Light Control Device & Network Telemetry Report ===")
            appendLine("App Version: v${BuildConfig.VERSION_NAME} (versionCode=${BuildConfig.VERSION_CODE}, ${BuildConfig.APPLICATION_ID})")
            appendLine("Generated: $now")
            appendLine()
            appendLine("--- [1. SETUP AP & PROVISIONING SNAPSHOT] ---")
            setupSnapshot.toDisplayLines(selectedSsid).forEach { appendLine(it) }
            appendLine()
            appendLine("--- [2. REMEMBERED WI-FI HOMES (${homes.size})] ---")
            if (homes.isEmpty()) {
                appendLine("(none)")
            } else {
                homes.forEach { h ->
                    val subnetLabel = if (h.subnetPrefix.isBlank()) "unset" else "${h.subnetPrefix}.0/24"
                    val gatewayLabel = h.gatewayIp.ifBlank { "none" }
                    appendLine(
                        "[HOME] key=${h.networkKey} name=\"${h.homeName}\" ssid=\"${h.ssidLabel}\" subnet=$subnetLabel gw=$gatewayLabel"
                    )
                }
            }
            appendLine()
            appendLine("--- [3. REGISTERED SWITCHES & DEVICES (${devices.size})] ---")
            if (devices.isEmpty()) {
                appendLine("(none)")
            } else {
                devices.forEach { d ->
                    val stateStr = when {
                        !d.isReachable -> "OFFLINE"
                        d.isOn -> "ON"
                        else -> "OFF_STANDBY"
                    }
                    val dimmerPart = if (d.parsedType == DeviceType.LIGHTBULB_DIMMER) {
                        " dim=${d.brightness}%${if (d.nightModeEnabled) " night=1" else ""}"
                    } else {
                        ""
                    }
                    val udnPart = if (d.udn.isNotBlank()) " udn=${d.udn}" else ""
                    val rulesMetaPart = buildString {
                        append(" rulesCap=${d.hardwareRulesCapability}")
                        if (d.hardwareRulesLongPressCapable) append(" lp=1")
                        if (d.hardwareRulesVerifiedFirmware.isNotBlank()) {
                            append(" rulesFw=${d.hardwareRulesVerifiedFirmware}")
                        }
                    }
                    appendLine(
                        "[DEV #${d.id}] name=\"${d.name}\" room=\"${d.normalizedRoomName}\" home=${d.networkKey} addr=${d.ipAddress}:${d.port} state=$stateStr$dimmerPart rtt=${d.latencyMs}ms sku=\"${d.hardwareModel}\" fw=\"${d.firmwareVersion}\" sn=${d.serialNumber} mac=${d.macAddress}$udnPart ssid=\"${d.wifiSsid}\"$rulesMetaPart"
                    )
                    val rulesSnap = hardwareRulesByDeviceId[d.id]
                    if (rulesSnap != null) {
                        val driftLabel = rulesSnap.deviceTimeInfo?.driftSeconds?.toString() ?: "n/a"
                        val needsSyncLabel = rulesSnap.deviceTimeInfo?.needsTimeSync?.toString() ?: "unknown"
                        val hasSolarLoc = rulesSnap.locationInfo?.let { loc ->
                            loc.latitude.isNotBlank() || loc.longitude.isNotBlank() || loc.cityName.isNotBlank()
                        } == true
                        val solarLocLabel = if (hasSolarLoc) "set" else "unset"
                        appendLine(
                            "  rules: dbV=${rulesSnap.dbVersion} rules=${rulesSnap.rules.size} integrity=${rulesSnap.integrityOk} driftSec=$driftLabel needsSync=$needsSyncLabel backups=${rulesSnap.availableBackups.size} solarLoc=$solarLocLabel"
                        )
                    }
                }
            }
            appendLine()
            appendLine("--- [4. UPNP / SOAP PACKET LOG (${soapLogs.size})] ---")
            if (soapLogs.isEmpty()) {
                appendLine("(none)")
            } else {
                soapLogs.forEach { entry ->
                    val okTag = if (entry.isLiveLanSuccess) "OK" else "ERR"
                    appendLine(
                        "[${entry.timestamp}] [$okTag] ${entry.deviceName} | ${entry.soapAction} | ${entry.endpoint} -> ${entry.statusText}"
                    )
                    val snippet = sanitizeXmlSnippet(entry.xmlSnippet)
                    if (snippet.isNotEmpty()) {
                        appendLine("  xml: $snippet")
                    }
                }
            }
            appendLine()
            appendLine("--- [5. HOME SCREEN WIDGETS (${widgets.size})] ---")
            if (widgets.isEmpty()) {
                appendLine("(none)")
            } else {
                widgets.forEach { w ->
                    when (w.targetType) {
                        WidgetTargetType.DEVICE -> {
                            val resolved = WidgetRemoteViewsBuilder.resolveTargetDevice(w, devices)
                            val status = if (resolved != null) {
                                val stateStr = when {
                                    !resolved.isReachable -> "OFFLINE"
                                    resolved.isOn -> "ON"
                                    else -> "OFF_STANDBY"
                                }
                                "resolved=#${resolved.id} \"${resolved.name}\" ($stateStr)"
                            } else {
                                "ORPHANED (no matching switch)"
                            }
                            val udnPart = if (w.udn.isNotBlank()) " udn=${w.udn}" else ""
                            appendLine(
                                "[WIDGET #${w.appWidgetId}] type=DEVICE name=\"${w.displayName}\" devId=${w.deviceId} sn=${w.serialNumber.ifBlank { "none" }}$udnPart room=\"${w.normalizedRoomName()}\" home=${w.networkKey.ifBlank { "none" }} -> $status"
                            )
                        }
                        WidgetTargetType.ROOM -> {
                            val roomDevs = WidgetRemoteViewsBuilder.resolveRoomDevices(w, devices)
                            val status = if (roomDevs.isNotEmpty()) {
                                val reachable = roomDevs.count { it.isReachable }
                                val onCount = roomDevs.count { it.isReachable && it.isOn }
                                "resolved=$onCount/${roomDevs.size} ON ($reachable/${roomDevs.size} reachable)"
                            } else {
                                "ORPHANED (0 matching switches)"
                            }
                            appendLine(
                                "[WIDGET #${w.appWidgetId}] type=ROOM room=\"${w.normalizedRoomName()}\" home=${w.networkKey.ifBlank { "none" }} -> $status"
                            )
                        }
                    }
                }
            }
        }.trim()
    }

    fun latestTelemetryFile(
        context: Context,
        fileName: String = DEFAULT_TELEMETRY_FILENAME
    ): File = File(context.filesDir, fileName)

    fun saveTelemetryReportToFile(
        context: Context,
        reportText: String,
        fileName: String = DEFAULT_TELEMETRY_FILENAME
    ): File {
        val outFile = latestTelemetryFile(context, fileName)
        outFile.writeText(reportText)
        return outFile
    }

    fun copyAndShareTelemetryReport(
        context: Context,
        reportText: String,
        subject: String = "WeMi Light Control Telemetry Report"
    ): File {
        val savedFile = saveTelemetryReportToFile(context, reportText)
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(subject, reportText))
        }
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "$subject (${savedFile.name})")
                putExtra(Intent.EXTRA_TEXT, reportText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Save or Share WeMi Telemetry").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
        return savedFile
    }
}


