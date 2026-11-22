package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

private val CapabilityUnsupportedBg = Color(0xFFF3F4F6)
private val CapabilityUnsupportedText = Color(0xFF4B5563)
private val ReadOnlyBadgeBg = Color(0xFFE0F2FE)
private val ReadOnlyBadgeText = Color(0xFF0369A1)
private val ClockWarningBg = Color(0xFFFEF3C7)
private val ClockWarningText = Color(0xFF92400E)

/**
 * Builds a canonical-UDN -> friendly-name lookup map across [devices] so Long Press targets and
 * paired sensors read cleanly as human room/switch names instead of raw UPnP UUID strings.
 */
fun buildUdnToFriendlyNameMap(devices: List<DeviceEntity>): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    for (dev in devices) {
        val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(dev.udn)
        if (cleanUdn.isNotEmpty()) {
            map[cleanUdn.lowercase(Locale.US)] = dev.name
        }
        if (dev.udn.isNotBlank()) {
            map[dev.udn.trim().lowercase(Locale.US)] = dev.name
        }
    }
    return map
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareRulesHeaderCard(
    devices: List<DeviceEntity>,
    snapshotsByDeviceId: Map<Int, DeviceHardwareRulesSnapshot>,
    isScanningAll: Boolean,
    onScanAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controllable = devices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
    val readyCount = controllable.count { dev ->
        val cap = snapshotsByDeviceId[dev.id]?.capability ?: dev.parsedHardwareRulesCapability
        cap.isSupportedOrLikely
    }
    val unsupportedCount = controllable.count { dev ->
        val cap = snapshotsByDeviceId[dev.id]?.capability ?: dev.parsedHardwareRulesCapability
        cap.isUnsupportedOrLikely
    }
    val totalRulesRead = controllable.sumOf { dev ->
        snapshotsByDeviceId[dev.id]?.rules?.count { it.appliesToInspectedSwitch } ?: 0
    }
    val hasSnapshots = controllable.any { it.id in snapshotsByDeviceId }

    Surface(
        color = WemoRowSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("hardware_rules_header_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "On-Device Switch Rules",
                            color = WemoTextPrimary,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        Surface(
                            color = ReadOnlyBadgeBg,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "Read-Only",
                                color = ReadOnlyBadgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Stored in switch memory • runs even when your phone is off",
                        color = WemoTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.5.sp
                    )
                    if (controllable.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val statusSummary = if (hasSnapshots) {
                            "$totalRulesRead rule(s) read • $readyCount ready • $unsupportedCount unsupported"
                        } else {
                            "$readyCount ready • $unsupportedCount unsupported • ${controllable.size} switches"
                        }
                        Text(
                            text = statusSummary,
                            color = WemoGreenDark,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onScanAllClick,
                    enabled = !isScanningAll && controllable.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("scan_hardware_rules_button")
                ) {
                    if (isScanningAll) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanningAll) "Reading..." else "Read Rules",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareRulesDeviceCard(
    device: DeviceEntity,
    snapshot: DeviceHardwareRulesSnapshot?,
    isInspecting: Boolean,
    udnToName: Map<String, String>,
    onReadDeviceRules: (forceRecheck: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveCap = snapshot?.capability ?: device.parsedHardwareRulesCapability
    val longPressCapable = snapshot?.longPressCapable ?: device.hardwareRulesLongPressCapable
    val deviceRules = snapshot?.rules?.filter { it.appliesToInspectedSwitch }.orEmpty()

    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WemoDividerGray),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .testTag("hardware_rules_device_card_${device.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        color = WemoTextPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${device.normalizedRoomName} • ${device.hardwareModel}",
                        color = WemoTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                AssistChip(
                    onClick = { onReadDeviceRules(snapshot != null) },
                    enabled = !isInspecting && device.isReachable,
                    label = {
                        Text(
                            text = when {
                                isInspecting -> "Reading..."
                                snapshot != null -> "Refresh"
                                else -> "Read"
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        if (isInspecting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = WemoGreenDark,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Read rules from ${device.name}",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = WemoGreenDark,
                        labelColor = WemoGreenDark
                    ),
                    modifier = Modifier.testTag("read_hardware_rules_${device.id}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val (badgeBg, badgeTextColor) = when (effectiveCap) {
                    HardwareRulesCapability.VERIFIED_SUPPORTED,
                    HardwareRulesCapability.LIKELY_SUPPORTED -> WemoGreenSoft to WemoGreenDark
                    else -> CapabilityUnsupportedBg to CapabilityUnsupportedText
                }
                val badgeLabel = when {
                    effectiveCap == HardwareRulesCapability.VERIFIED_SUPPORTED && longPressCapable ->
                        "On-Device Ready • Long Press"
                    effectiveCap == HardwareRulesCapability.LIKELY_SUPPORTED && longPressCapable ->
                        "Likely Ready • Long Press"
                    else -> effectiveCap.displayLabel
                }
                HardwareRuleBadgePill(
                    text = badgeLabel,
                    bgColor = badgeBg,
                    textColor = badgeTextColor,
                    modifier = Modifier.testTag("hardware_rules_capability_badge_${device.id}")
                )
                if (snapshot != null && effectiveCap == HardwareRulesCapability.VERIFIED_SUPPORTED) {
                    HardwareRuleBadgePill(
                        text = "DB v${snapshot.dbVersion} • ${deviceRules.size} rule(s)",
                        bgColor = Color(0xFFF1F5F9),
                        textColor = WemoTextSecondary
                    )
                }
                if (!device.isReachable) {
                    HardwareRuleBadgePill(
                        text = "Offline",
                        bgColor = StatusOfflineSoftBg,
                        textColor = StatusOfflineRed
                    )
                }
                if (snapshot?.deviceTimeInfo?.needsTimeSync == true) {
                    HardwareRuleBadgePill(
                        text = "Switch clock needs sync",
                        bgColor = ClockWarningBg,
                        textColor = ClockWarningText
                    )
                }
            }

            when {
                effectiveCap == HardwareRulesCapability.VERIFIED_UNSUPPORTED -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    val dimmerHint = if (device.parsedType == DeviceType.LIGHTBULB_DIMMER) {
                        " Dimmer Night Mode is still stored on-device."
                    } else {
                        ""
                    }
                    Text(
                        text = "Firmware does not expose on-device rules storage.$dimmerHint",
                        color = WemoTextSecondary,
                        fontSize = 12.sp
                    )
                }
                snapshot != null && deviceRules.isEmpty() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = snapshot.diagnosticMessage.ifBlank { "No on-device rules stored on this switch yet." },
                        color = WemoTextSecondary,
                        fontSize = 12.sp
                    )
                }
                deviceRules.isNotEmpty() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        deviceRules.forEach { rule ->
                            HardwareRuleSummaryRow(
                                deviceId = device.id,
                                rule = rule,
                                udnToName = udnToName
                            )
                        }
                    }
                    snapshot?.locationInfo?.formatSummary()?.takeIf { it.isNotBlank() }?.let { loc ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Solar location on switch: $loc",
                            color = WemoTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareRuleSummaryRow(
    deviceId: Int,
    rule: HardwareRule,
    udnToName: Map<String, String>
) {
    val (triggerText, actionText) = rule.formatTriggerAndAction(udnToName)
    Surface(
        color = if (rule.isEnabled) Color(0xFFF8FAFC) else Color(0xFFF3F4F6),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WemoDividerGray),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hardware_rule_item_${deviceId}_${rule.ruleId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (rule.isEnabled) WemoGreenSoft else Color(0xFFE5E7EB),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForHardwareRuleType(rule.type),
                        contentDescription = null,
                        tint = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rule.name,
                        color = if (rule.isEnabled) WemoTextPrimary else WemoTextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HardwareRuleBadgePill(
                        text = if (rule.isEnabled) rule.type.displayLabel else "${rule.type.displayLabel} • Paused",
                        bgColor = if (rule.isEnabled) WemoGreenSoft else Color(0xFFE5E7EB),
                        textColor = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary
                    )
                }
                Text(text = triggerText, color = WemoTextSecondary, fontSize = 12.sp)
                Text(
                    text = actionText,
                    color = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun HardwareRuleBadgePill(
    text: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(color = bgColor, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
        )
    }
}

private fun iconForHardwareRuleType(type: HardwareRuleType): ImageVector = when (type) {
    HardwareRuleType.LONG_PRESS -> Icons.Default.TouchApp
    HardwareRuleType.COUNTDOWN_TIMER -> Icons.Default.Timer
    HardwareRuleType.AWAY_MODE -> Icons.AutoMirrored.Filled.DirectionsWalk
    HardwareRuleType.SENSOR_TRIGGER, HardwareRuleType.MAKER_SENSOR -> Icons.Default.Sensors
    else -> Icons.Default.Schedule
}
