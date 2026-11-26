package org.suppressingfire.wemi.network

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import java.util.Locale
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.widget.WidgetTargetConfig

/**
 * Privacy-by-default redactor and sensitive-clipboard helper for diagnostic telemetry exports.
 *
 * When `includeSensitiveIdentifiers` is `false` (default), masks home/neighbor Wi-Fi SSIDs with
 * consistent per-report aliases (`SSID#1`, `SSID#2`, ...), truncates MAC addresses, serial numbers,
 * and UDN serial suffixes to their last 4 characters, and hides DNS server IP addresses while
 * keeping structural diagnostics (signal strength, channel, hardware SKU, firmware, rule counts,
 * and error codes) intact for support troubleshooting.
 */
internal class TelemetryRedactionSession(
    val includeSensitiveIdentifiers: Boolean
) {
    private val ssidAliases = LinkedHashMap<String, String>()
    private val macReplacements = LinkedHashMap<String, String>()
    private val serialReplacements = LinkedHashMap<String, String>()
    private val udnReplacements = LinkedHashMap<String, String>()

    companion object {
        private val GENERIC_SSID_LABELS = setOf(
            "",
            "local lan",
            "unknown",
            "none",
            "n/a"
        )
        private val MAC_COLON_REGEX = Regex("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")
        private val MAC_HEX12_REGEX = Regex("\\b[0-9A-Fa-f]{12}\\b")
        private val EXTRA_IS_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

        /**
         * Writes diagnostic text to the Android clipboard and marks it sensitive on Android 13+
         * (`ClipDescription.EXTRA_IS_SENSITIVE = true`) so the system clipboard preview masks
         * network and device identifiers.
         */
        fun copyToClipboard(
            context: Context,
            label: String,
            text: String,
            sensitive: Boolean = true
        ) {
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@runCatching
                val clip = ClipData.newPlainText(label, text)
                if (sensitive) {
                    val extras = PersistableBundle().apply {
                        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ClipDescription.EXTRA_IS_SENSITIVE
                        } else {
                            EXTRA_IS_SENSITIVE_KEY
                        }
                        putBoolean(key, true)
                    }
                    clip.description.extras = extras
                }
                clipboard.setPrimaryClip(clip)
            }
        }
    }

    fun registerContext(
        setupSnapshot: SetupDebugSnapshot,
        selectedSsid: String,
        homes: List<HomeNetworkEntity>,
        devices: List<DeviceEntity>,
        widgets: List<WidgetTargetConfig>
    ) {
        if (includeSensitiveIdentifiers) return
        redactSsid(selectedSsid)
        redactSsid(setupSnapshot.targetSsid)
        setupSnapshot.scannedAps.forEach { redactSsid(it.ssid) }
        setupSnapshot.detectedDevice?.let { d ->
            redactMac(d.macAddress)
            redactSerial(d.serialNumber)
            redactUdn(d.udn)
        }
        homes.forEach { redactSsid(it.ssidLabel) }
        devices.forEach { d ->
            redactSsid(d.wifiSsid)
            redactMac(d.macAddress)
            redactSerial(d.serialNumber)
            redactUdn(d.udn)
        }
        widgets.forEach { w ->
            redactSerial(w.serialNumber)
            redactUdn(w.udn)
        }
    }

    fun redactSsid(raw: String): String {
        val clean = raw.trim()
        if (includeSensitiveIdentifiers || clean.lowercase(Locale.US) in GENERIC_SSID_LABELS) {
            return clean
        }
        val key = clean.lowercase(Locale.US)
        return ssidAliases.getOrPut(key) { "SSID#${ssidAliases.size + 1}" }
    }

    fun redactMac(raw: String): String {
        val clean = raw.trim()
        if (includeSensitiveIdentifiers || clean.isEmpty() || clean.equals("none", ignoreCase = true)) {
            return clean
        }
        val hex = clean.replace(":", "").replace("-", "")
        val redacted = if (hex.length >= 4) "****${hex.takeLast(4).uppercase(Locale.US)}" else "****"
        macReplacements[clean] = redacted
        if (hex.length == 12) macReplacements[hex] = redacted
        return redacted
    }

    fun redactSerial(raw: String): String {
        val clean = raw.trim()
        if (includeSensitiveIdentifiers || clean.isEmpty() || clean.equals("none", ignoreCase = true)) {
            return clean
        }
        val redacted = if (clean.length > 4) "***${clean.takeLast(4)}" else "****"
        serialReplacements[clean] = redacted
        return redacted
    }

    fun redactUdn(raw: String): String {
        val clean = raw.trim()
        if (includeSensitiveIdentifiers || clean.isEmpty() || clean.equals("none", ignoreCase = true)) {
            return clean
        }
        val lastDash = clean.lastIndexOf('-')
        val redacted = if (lastDash > 0 && lastDash < clean.length - 4) {
            val prefix = clean.substring(0, lastDash + 1)
            val suffix = clean.substring(lastDash + 1)
            "$prefix***${suffix.takeLast(4)}"
        } else if (clean.length > 4) {
            "***${clean.takeLast(4)}"
        } else {
            "****"
        }
        udnReplacements[clean] = redacted
        return redacted
    }

    fun redactDnsList(dnsServersIpv4: List<String>): String {
        if (dnsServersIpv4.isEmpty()) return ""
        return if (includeSensitiveIdentifiers) {
            " dns=${dnsServersIpv4.joinToString(",")}"
        } else {
            " dns=<redacted:${dnsServersIpv4.size}>"
        }
    }

    fun redactMetaInfo(rawMeta: String): String {
        val clean = rawMeta.trim()
        if (clean.isEmpty()) return "none"
        if (includeSensitiveIdentifiers) return clean
        val parts = clean.split('|')
        if (parts.size < 2) return scrubFreeText(clean)
        return parts.mapIndexed { idx, part ->
            when (idx) {
                0 -> redactMac(part)
                1 -> redactSerial(part)
                4 -> redactSsid(part)
                else -> part
            }
        }.joinToString("|")
    }

    /**
     * Replaces any known SSIDs, UDNs, serial numbers, and MAC addresses inside free-form trace
     * lines or SOAP packet snippets when `includeSensitiveIdentifiers` is `false`.
     */
    fun scrubFreeText(text: String): String {
        if (includeSensitiveIdentifiers || text.isEmpty()) return text
        var result = text
        // Replace longest tokens first so full UDNs match before bare serials
        udnReplacements.entries
            .sortedByDescending { it.key.length }
            .forEach { (raw, masked) ->
                if (raw.length >= 6) result = result.replace(raw, masked, ignoreCase = true)
            }
        serialReplacements.entries
            .sortedByDescending { it.key.length }
            .forEach { (raw, masked) ->
                if (raw.length >= 5) result = result.replace(raw, masked, ignoreCase = true)
            }
        macReplacements.entries
            .sortedByDescending { it.key.length }
            .forEach { (raw, masked) ->
                if (raw.length >= 8) result = result.replace(raw, masked, ignoreCase = true)
            }
        ssidAliases.entries
            .sortedByDescending { it.key.length }
            .forEach { (rawKey, alias) ->
                if (rawKey.length >= 3) {
                    result = Regex(Regex.escape(rawKey), RegexOption.IGNORE_CASE).replace(result, alias)
                }
            }
        result = MAC_COLON_REGEX.replace(result) { m ->
            val hex = m.value.replace(":", "").replace("-", "")
            "****${hex.takeLast(4).uppercase(Locale.US)}"
        }
        result = MAC_HEX12_REGEX.replace(result) { m ->
            "****${m.value.takeLast(4).uppercase(Locale.US)}"
        }
        return result
    }
}
