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
                    Text(
                        text = "On-Device Switch Rules",
                        color = WemoTextPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Stored inside your switches • run with no phone or cloud",
                        color = WemoTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp
                    )
                    if (controllable.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$readyCount ready • $unsupportedCount unsupported • ${controllable.size} switches",
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
                    modifier = Modifier.testTag("scan_hardware_rules_button")
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
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${device.normalizedRoomName} • ${device.hardwareModel}",
                        color = WemoTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp
                    )
                }

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
                            fontSize = 12.sp
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
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("hardware_rules_capability_badge_${device.id}")
                ) {
                    Text(
                        text = badgeLabel,
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }

                if (snapshot != null && effectiveCap == HardwareRulesCapability.VERIFIED_SUPPORTED) {
                    Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(999.dp)) {
                        Text(
                            text = "DB v${snapshot.dbVersion} • ${deviceRules.size} rule(s)",
                            color = WemoTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }

                if (snapshot?.deviceTimeInfo?.needsTimeSync == true) {
                    Surface(color = ClockWarningBg, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            text = "Switch clock needs sync",
                            color = ClockWarningText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
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
        color = Color(0xFFF8FAFC),
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
                        color = WemoTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (rule.isEnabled) rule.type.displayLabel else "${rule.type.displayLabel} • Paused",
                        color = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = triggerText,
                    color = WemoTextSecondary,
                    fontSize = 12.sp
                )
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

private fun iconForHardwareRuleType(type: HardwareRuleType): ImageVector = when (type) {
    HardwareRuleType.LONG_PRESS -> Icons.Default.TouchApp
    HardwareRuleType.COUNTDOWN_TIMER -> Icons.Default.Timer
    HardwareRuleType.AWAY_MODE -> Icons.AutoMirrored.Filled.DirectionsWalk
    HardwareRuleType.SENSOR_TRIGGER, HardwareRuleType.MAKER_SENSOR -> Icons.Default.Sensors
    else -> Icons.Default.Schedule
}
