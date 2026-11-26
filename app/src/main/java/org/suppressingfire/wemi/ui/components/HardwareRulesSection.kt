package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareRulesHeaderCard(
    devices: List<DeviceEntity>,
    snapshotsByDeviceId: Map<Int, DeviceHardwareRulesSnapshot>,
    isScanningAll: Boolean,
    onScanAllClick: () -> Unit,
    onAddRuleClick: () -> Unit,
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
    val canCreateRule = controllable.any { dev ->
        val cap = snapshotsByDeviceId[dev.id]?.capability ?: dev.parsedHardwareRulesCapability
        dev.isReachable && cap != HardwareRulesCapability.VERIFIED_UNSUPPORTED
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    HardwareRuleBadgePill(
                        text = "On-Switch",
                        bgColor = WemoGreenSoft,
                        textColor = WemoGreenDark
                    )
                }
                Text(
                    text = "Stored in switch memory • runs even when your phone is off",
                    color = WemoTextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.5.sp
                )
                if (controllable.isNotEmpty()) {
                    val statusSummary = if (hasSnapshots) {
                        "$totalRulesRead rule(s) stored • $readyCount ready • $unsupportedCount unsupported"
                    } else {
                        "$readyCount ready • $unsupportedCount unsupported • ${controllable.size} switch(es)"
                    }
                    Text(
                        text = statusSummary,
                        color = WemoGreenDark,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onAddRuleClick,
                        enabled = canCreateRule,
                        colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_hardware_rule_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "New Rule", maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = onScanAllClick,
                        enabled = !isScanningAll && controllable.isNotEmpty(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("scan_hardware_rules_button")
                    ) {
                        if (isScanningAll) {
                            CircularProgressIndicator(
                                color = WemoGreenDark,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = WemoGreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanningAll) "Reading..." else if (hasSnapshots) "Refresh All" else "Read Rules",
                            color = WemoGreenDark,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
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
    onReadDeviceRules: (forceRecheck: Boolean) -> Unit,
    onAddRuleForDevice: () -> Unit,
    onSyncClock: () -> Unit,
    onEditSolarLocation: () -> Unit,
    onOpenBackups: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveCap = snapshot?.capability ?: device.parsedHardwareRulesCapability
    val longPressCapable = snapshot?.longPressCapable ?: device.hardwareRulesLongPressCapable
    val deviceRules = snapshot?.rules?.filter { it.appliesToInspectedSwitch }.orEmpty()
    val canManage = device.isReachable && effectiveCap != HardwareRulesCapability.VERIFIED_UNSUPPORTED
    val needsClockSync = snapshot?.deviceTimeInfo?.needsTimeSync == true
    val backupCount = snapshot?.availableBackups?.size ?: 0

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
                                isInspecting -> "Syncing..."
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
                if (needsClockSync) {
                    HardwareRuleBadgePill(
                        text = "Switch clock needs sync",
                        bgColor = ClockWarningBg,
                        textColor = ClockWarningText
                    )
                }
            }

            if (canManage) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = onAddRuleForDevice,
                        enabled = !isInspecting,
                        label = { Text("Add Rule", fontSize = 11.5.sp, maxLines = 1, softWrap = false) },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = "Add on-device rule for ${device.name}", modifier = Modifier.size(15.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = WemoGreenDark,
                            labelColor = WemoGreenDark
                        ),
                        modifier = Modifier.testTag("add_device_hardware_rule_${device.id}")
                    )
                    if (needsClockSync || effectiveCap == HardwareRulesCapability.VERIFIED_SUPPORTED) {
                        AssistChip(
                            onClick = onSyncClock,
                            enabled = !isInspecting,
                            label = {
                                Text(
                                    text = if (needsClockSync) "Sync Clock" else "Clock Sync",
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = "Sync clock on ${device.name}", modifier = Modifier.size(15.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (needsClockSync) ClockWarningBg else Color.Transparent,
                                leadingIconContentColor = if (needsClockSync) ClockWarningText else WemoTextSecondary,
                                labelColor = if (needsClockSync) ClockWarningText else WemoTextSecondary
                            ),
                            modifier = Modifier.testTag("sync_device_clock_${device.id}")
                        )
                    }
                    if (effectiveCap == HardwareRulesCapability.VERIFIED_SUPPORTED) {
                        AssistChip(
                            onClick = onEditSolarLocation,
                            enabled = !isInspecting,
                            label = { Text("Solar Location", fontSize = 11.5.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, contentDescription = "Set solar location on ${device.name}", modifier = Modifier.size(15.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = WemoTextSecondary,
                                labelColor = WemoTextSecondary
                            ),
                            modifier = Modifier.testTag("edit_solar_location_${device.id}")
                        )
                    }
                    if (backupCount > 0) {
                        AssistChip(
                            onClick = onOpenBackups,
                            enabled = !isInspecting,
                            label = { Text("Backups ($backupCount)", fontSize = 11.5.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(Icons.Default.History, contentDescription = "Restore backup for ${device.name}", modifier = Modifier.size(15.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = WemoGreenDark,
                                labelColor = WemoGreenDark
                            ),
                            modifier = Modifier.testTag("open_backup_restore_${device.id}")
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
