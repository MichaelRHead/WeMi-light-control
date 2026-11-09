package com.example.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.example.data.SoapLogEntry
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoHomeNetworkEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured diagnostic snapshot and human/screenshot-readable formatter for zero-cloud
 * `10.22.22.1` Belkin WeMo Setup AP probing, 2.4 GHz AP scanning, and `WiFiSetup1` provisioning.
 */
data class WeMoSetupDebugSnapshot(
    val timestamp: String = "",
    val phoneLocalIp: String = "",
    val phoneGatewayIp: String = "",
    val phoneInterface: String = "wlan0",
    val isWemoSetupSubnet: Boolean = false,
    val isWifiSocketBound: Boolean = false,
    val isDemoMode: Boolean = false,
    val targetApHost: String = "10.22.22.1",
    val probeSummary: String = "Not probed yet",
    val detectedDevice: WeMoSetupDeviceDescriptor? = null,
    val apScanSummary: String = "Not scanned yet",
    val scannedAps: List<WeMoScannedAp> = emptyList(),
    val traceLines: List<String> = emptyList()
) {
    /**
     * Formats all setup telemetry into compact, high-signal lines designed to fit inside a single
     * phone screenshot in `ProvisionWeMoApDialog` (`provision_debug_panel`) or clipboard paste.
     */
    fun toDisplayLines(selectedSsid: String = ""): List<String> {
        val lines = mutableListOf<String>()
        val modeTag = if (isDemoMode) "DEMO" else "REAL-LAN"
        lines.add(
            "[NET] mode=$modeTag ip=$phoneLocalIp ($phoneInterface) gw=$phoneGatewayIp setupSubnet=$isWemoSetupSubnet wifiBound=$isWifiSocketBound"
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
            lines.add("[XML/ID] No WeMo setup descriptor read from $targetApHost")
        }
        lines.add("[SCAN] $apScanSummary")
        if (selectedSsid.isNotBlank() && scannedAps.isNotEmpty()) {
            val match = scannedAps.firstOrNull { it.ssid.equals(selectedSsid.trim(), ignoreCase = true) }
            if (match != null) {
                lines.add(
                    "[TARGET AP] \"${match.ssid}\" found in ApList: Ch=${match.channel} Auth=${match.authMode}/${match.encryptionType} Signal=${match.signalStrengthPercent}%"
                )
            } else {
                val sample = scannedAps.take(4).joinToString(", ") { "${it.ssid}(Ch${it.channel})" }
                lines.add(
                    "[TARGET AP] WARN: \"$selectedSsid\" NOT in ApList! Seen: [$sample]"
                )
            }
        }
        traceLines.forEach { line ->
            lines.add(line)
        }
        return lines
    }

    fun toClipboardText(selectedSsid: String = ""): String {
        return buildString {
            appendLine("=== WeMi Setup AP Diagnostic Report ($timestamp) ===")
            toDisplayLines(selectedSsid).forEach { appendLine(it) }
        }.trim()
    }
}

/**
 * Builds, persists (`files/wemi-telemetry-latest.txt`), and shares full device & network
 * telemetry reports so users can either take an on-screen screenshot or export a file to share.
 */
object WeMoTelemetryFileExporter {
    const val DEFAULT_TELEMETRY_FILENAME = "wemi-telemetry-latest.txt"

    fun buildFullTelemetryReport(
        setupSnapshot: WeMoSetupDebugSnapshot,
        selectedSsid: String = "",
        homes: List<WeMoHomeNetworkEntity> = emptyList(),
        devices: List<WeMoDeviceEntity> = emptyList(),
        soapLogs: List<SoapLogEntry> = emptyList()
    ): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("=== WeMi Light Control Device & Network Telemetry Report ===")
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
                    appendLine(
                        "[HOME] key=${h.networkKey} name=\"${h.homeName}\" ssid=\"${h.ssidLabel}\" subnet=${h.subnetPrefix}.0/24 gw=${h.gatewayIp}"
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
                    appendLine(
                        "[DEV #${d.id}] name=\"${d.name}\" room=\"${d.roomName}\" addr=${d.ipAddress}:${d.port} state=$stateStr rtt=${d.latencyMs}ms sku=\"${d.hardwareModel}\" fw=\"${d.firmwareVersion}\" sn=${d.serialNumber} mac=${d.macAddress} ssid=\"${d.wifiSsid}\""
                    )
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
                }
            }
        }.trim()
    }

    fun saveTelemetryReportToFile(
        context: Context,
        reportText: String,
        fileName: String = DEFAULT_TELEMETRY_FILENAME
    ): File {
        val outFile = File(context.filesDir, fileName)
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

