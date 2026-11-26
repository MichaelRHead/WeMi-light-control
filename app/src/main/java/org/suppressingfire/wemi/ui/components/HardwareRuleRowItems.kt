package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

private val NeutralPillText = Color(0xFF475569)

/**
 * Interactive row for a single native rule stored inside a switch's `rules.db` archive, providing
 * live enable/disable toggling, editing (for supported types), and backup-protected deletion.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HardwareRuleSummaryRow(
    deviceId: Int,
    rule: HardwareRule,
    udnToName: Map<String, String>,
    controlsEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (triggerText, actionText) = rule.formatTriggerAndAction(udnToName)
    Surface(
        color = if (rule.isEnabled) Color(0xFFF8FAFC) else Color(0xFFF3F4F6),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WemoDividerGray),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hardware_rule_item_${deviceId}_${rule.ruleId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            tint = if (rule.isEnabled) WemoGreenDark else NeutralPillText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        color = if (rule.isEnabled) WemoTextPrimary else WemoTextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = triggerText, color = WemoTextSecondary, fontSize = 12.sp)
                    Text(
                        text = actionText,
                        color = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    HardwareRuleBadgePill(
                        text = if (rule.isEnabled) rule.type.displayLabel else "${rule.type.displayLabel} • Paused",
                        bgColor = if (rule.isEnabled) WemoGreenSoft else Color(0xFFE5E7EB),
                        textColor = if (rule.isEnabled) WemoGreenDark else NeutralPillText
                    )
                    if (!rule.isEditableInApp) {
                        val readOnlyTag = when {
                            rule.isSharedMultiSwitch -> "Shared rule"
                            !rule.isOwnedByInspectedSwitch -> "Target"
                            else -> "View only"
                        }
                        HardwareRuleBadgePill(
                            text = readOnlyTag,
                            bgColor = Color(0xFFE2E8F0),
                            textColor = NeutralPillText
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))

                val canMutateRule = controlsEnabled && rule.isOwnedByInspectedSwitch && !rule.isSharedMultiSwitch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rule.isEditableInApp) {
                        IconButton(
                            onClick = onEditClick,
                            enabled = controlsEnabled,
                            modifier = Modifier.testTag("edit_hardware_rule_${deviceId}_${rule.ruleId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit ${rule.name}",
                                tint = if (controlsEnabled) WemoGreenDark else WemoTextSecondary.copy(alpha = 0.38f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = canMutateRule,
                        modifier = Modifier.testTag("delete_hardware_rule_${deviceId}_${rule.ruleId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${rule.name}",
                            tint = if (canMutateRule) WemoTextSecondary else WemoTextSecondary.copy(alpha = 0.38f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        enabled = canMutateRule,
                        colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary),
                        modifier = Modifier
                            .semantics {
                                contentDescription = "Toggle on-device rule ${rule.name}"
                            }
                            .testTag("toggle_hardware_rule_${deviceId}_${rule.ruleId}")
                    )
                }
            }
        }
    }
}

/**
 * Compact per-switch on-device rules readout inside [ExpandedDrawer] (placed below Dimmer
 * Brightness and Connection Telemetry so Layout Invariant 4 stays intact).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedDrawerHardwareRulesSection(
    device: DeviceEntity,
    snapshot: DeviceHardwareRulesSnapshot?,
    isInspecting: Boolean,
    udnToName: Map<String, String>,
    onInspectHardwareRules: () -> Unit,
    onAddHardwareRule: (() -> Unit)? = null,
    onSyncDeviceClock: (() -> Unit)? = null,
    onOpenRulesTab: (() -> Unit)? = null
) {
    val cap = snapshot?.capability ?: device.parsedHardwareRulesCapability
    val lp = snapshot?.longPressCapable ?: device.hardwareRulesLongPressCapable
    val rules = snapshot?.rules?.filter { it.appliesToInspectedSwitch }.orEmpty()
    val canAdd = device.isReachable && cap != HardwareRulesCapability.VERIFIED_UNSUPPORTED && onAddHardwareRule != null
    val needsClockSync = snapshot?.deviceTimeInfo?.needsTimeSync == true
    val showManageRules = cap != HardwareRulesCapability.VERIFIED_UNSUPPORTED && onOpenRulesTab != null
    val hasInspectionError = snapshot != null && (snapshot.hasInspectionError || !snapshot.integrityOk)

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WemoDividerGray),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("hardware_rules_drawer_section_${device.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val statusLine = buildString {
                        append("On-Device Rules: ${cap.displayLabel}")
                        if (lp && cap.isSupportedOrLikely) append(" • Long Press")
                    }
                    Text(
                        text = statusLine,
                        color = if (cap.isSupportedOrLikely) WemoGreenDark else WemoTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    val subtitle = when {
                        snapshot != null && hasInspectionError -> snapshot.diagnosticMessage.ifBlank {
                            "Could not inspect on-device rules — tap Refresh to retry"
                        }
                        snapshot != null && cap == HardwareRulesCapability.VERIFIED_SUPPORTED ->
                            "${rules.size} rule(s) stored on switch (DB v${snapshot.dbVersion})"
                        snapshot != null -> snapshot.diagnosticMessage
                        else -> "Manage stored schedules, auto-off delays & long-press targets"
                    }
                    Text(
                        text = subtitle,
                        color = if (hasInspectionError) StatusOfflineRed else WemoTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AssistChip(
                    onClick = onInspectHardwareRules,
                    enabled = !isInspecting && device.isReachable,
                    label = {
                        Text(
                            text = when {
                                isInspecting -> "Syncing..."
                                snapshot != null -> "Refresh"
                                else -> "Inspect Rules"
                            },
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = WemoGreenDark,
                        labelColor = WemoGreenDark
                    ),
                    modifier = Modifier.testTag("inspect_device_rules_${device.id}")
                )
            }

            if (canAdd || (needsClockSync && onSyncDeviceClock != null) || showManageRules) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canAdd) {
                        AssistChip(
                            onClick = { onAddHardwareRule?.invoke() },
                            enabled = !isInspecting,
                            label = { Text("Add Rule", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = WemoGreenDark,
                                labelColor = WemoGreenDark
                            ),
                            modifier = Modifier.testTag("drawer_add_hardware_rule_${device.id}")
                        )
                    }
                    if (needsClockSync && onSyncDeviceClock != null) {
                        AssistChip(
                            onClick = onSyncDeviceClock,
                            enabled = !isInspecting,
                            label = { Text("Sync Clock", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFFEF3C7),
                                leadingIconContentColor = Color(0xFF92400E),
                                labelColor = Color(0xFF92400E)
                            ),
                            modifier = Modifier.testTag("drawer_sync_clock_${device.id}")
                        )
                    }
                    if (showManageRules) {
                        AssistChip(
                            onClick = { onOpenRulesTab?.invoke() },
                            label = { Text("Manage Rules", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = NeutralPillText,
                                labelColor = NeutralPillText
                            ),
                            modifier = Modifier.testTag("drawer_open_rules_tab_${device.id}")
                        )
                    }
                }
            }

            if (needsClockSync) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Switch clock needs sync — schedules may run off-time",
                    color = Color(0xFF92400E),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            }

            if (rules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rules.take(4).forEach { rule ->
                        val (triggerText, actionText) = rule.formatTriggerAndAction(udnToName)
                        Surface(
                            color = if (rule.isEnabled) Color(0xFFF8FAFC) else Color(0xFFF3F4F6),
                            shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, WemoDividerGray),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rule.name,
                                        color = if (rule.isEnabled) WemoTextPrimary else WemoTextSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (rule.isEnabled) rule.type.displayLabel else "${rule.type.displayLabel} • Paused",
                                        color = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Text(
                                    text = "$triggerText → $actionText",
                                    color = WemoTextSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (rules.size > 4) {
                        if (onOpenRulesTab != null) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button, onClick = onOpenRulesTab)
                                    .testTag("drawer_more_rules_link_${device.id}")
                            ) {
                                Text(
                                    text = "+${rules.size - 4} more rule(s) in Rules tab",
                                    color = WemoGreenDark,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Text(
                                text = "+${rules.size - 4} more rule(s) in Rules tab",
                                color = WemoGreenDark,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HardwareRuleBadgePill(
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
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
        )
    }
}

internal fun iconForHardwareRuleType(type: HardwareRuleType): ImageVector = when (type) {
    HardwareRuleType.LONG_PRESS -> Icons.Default.TouchApp
    HardwareRuleType.COUNTDOWN_TIMER -> Icons.Default.Timer
    HardwareRuleType.AWAY_MODE -> Icons.AutoMirrored.Filled.DirectionsWalk
    HardwareRuleType.SENSOR_TRIGGER, HardwareRuleType.MAKER_SENSOR -> Icons.Default.Sensors
    else -> Icons.Default.Schedule
}
