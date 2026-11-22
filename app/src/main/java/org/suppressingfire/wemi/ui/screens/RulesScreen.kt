package org.suppressingfire.wemi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.suppressingfire.wemi.data.RuleEntity
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.ui.components.HardwareRulesDeviceCard
import org.suppressingfire.wemi.ui.components.HardwareRulesHeaderCard
import org.suppressingfire.wemi.ui.components.buildUdnToFriendlyNameMap
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

@Composable
fun RulesScreen(
    rules: List<RuleEntity>,
    onToggleRule: (RuleEntity) -> Unit,
    onRunRuleNow: (RuleEntity) -> Unit,
    onDeleteRule: (RuleEntity) -> Unit,
    onAddRuleClick: () -> Unit,
    homeDevices: List<DeviceEntity> = emptyList(),
    hardwareRulesByDeviceId: Map<Int, DeviceHardwareRulesSnapshot> = emptyMap(),
    inspectingRuleDeviceIds: Set<Int> = emptySet(),
    isScanningAllDeviceRules: Boolean = false,
    onScanAllHardwareRules: () -> Unit = {},
    onInspectDeviceHardwareRules: (DeviceEntity, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val controllableDevices = remember(homeDevices) {
        homeDevices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
    }
    val sortedControllableDevices = remember(controllableDevices, hardwareRulesByDeviceId) {
        controllableDevices.sortedWith(
            compareBy<DeviceEntity>(
                { device -> hardwareRulesSortTier(device, hardwareRulesByDeviceId[device.id]) },
                { device -> -(hardwareRulesByDeviceId[device.id]?.rules?.count { it.appliesToInspectedSwitch } ?: 0) },
                { device -> device.name.lowercase(Locale.US) }
            )
        )
    }
    val udnToName = remember(homeDevices) { buildUdnToFriendlyNameMap(homeDevices) }
    var showDeviceCards by rememberSaveable { mutableStateOf(false) }
    val expandHardwareDevices = showDeviceCards
    val inspectedCount = controllableDevices.count { it.id in hardwareRulesByDeviceId }
    val totalStoredRules = controllableDevices.sumOf { dev ->
        hardwareRulesByDeviceId[dev.id]?.rules?.count { it.appliesToInspectedSwitch } ?: 0
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WemoBackgroundGray)
            .testTag("rules_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (controllableDevices.isNotEmpty()) {
            item(key = "hardware_rules_header") {
                HardwareRulesHeaderCard(
                    devices = controllableDevices,
                    snapshotsByDeviceId = hardwareRulesByDeviceId,
                    isScanningAll = isScanningAllDeviceRules,
                    onScanAllClick = {
                        showDeviceCards = true
                        onScanAllHardwareRules()
                    }
                )
            }
            item(key = "hardware_rules_expand_row") {
                val toggleSummary = when {
                    expandHardwareDevices ->
                        "Sorted by stored rules & support • tap Refresh per switch"
                    inspectedCount > 0 ->
                        "$totalStoredRules on-device rule(s) read across $inspectedCount switch(es)"
                    else ->
                        "Inspect per-switch capability & stored rules"
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(color = WemoRowSurface, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = toggleSummary,
                                color = WemoTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showDeviceCards = !showDeviceCards },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("toggle_hardware_rules_device_list_button")
                            ) {
                                Text(
                                    text = if (expandHardwareDevices) {
                                        "Hide Switches"
                                    } else {
                                        "Show Switches (${controllableDevices.size})"
                                    },
                                    color = WemoGreenDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = if (expandHardwareDevices) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    contentDescription = null,
                                    tint = WemoGreenDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                }
            }
            if (expandHardwareDevices) {
                items(sortedControllableDevices, key = { "hw_rules_dev_${it.id}" }) { device ->
                    HardwareRulesDeviceCard(
                        device = device,
                        snapshot = hardwareRulesByDeviceId[device.id],
                        isInspecting = device.id in inspectingRuleDeviceIds,
                        udnToName = udnToName,
                        onReadDeviceRules = { forceRecheck ->
                            onInspectDeviceHardwareRules(device, forceRecheck)
                        }
                    )
                }
            }
            item(key = "rules_section_spacer") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                }
            }
        }

        item(key = "local_app_rules_header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = WemoRowSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Local Switch Rules & Timers",
                                    color = WemoTextPrimary,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    color = WemoGreenSoft,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "App-Run",
                                        color = WemoGreenDark,
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
                                text = "Runs from this device over local Wi-Fi • create & edit rules here",
                                color = WemoTextSecondary,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onAddRuleClick,
                            colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("add_rule_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "New Rule",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
            }
        }

        if (rules.isEmpty()) {
            item(key = "local_app_rules_empty") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(color = WemoRowSurface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No app-run rules yet. Tap New Rule for daily schedules, motion triggers, or auto-off timers.",
                            color = WemoTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            Surface(
                color = WemoRowSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rule_row_${rule.id}")
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (rule.isEnabled) WemoGreenSoft else Color(0xFFECEFF2),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (rule.ruleType) {
                                        "MOTION_TRIGGER" -> Icons.Default.Sensors
                                        "AUTO_OFF" -> Icons.Default.Timer
                                        else -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.name,
                                color = WemoTextPrimary,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                                fontSize = 18.sp
                            )
                            Text(
                                text = rule.triggerSummary,
                                color = WemoTextSecondary,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp
                            )
                            Text(
                                text = rule.actionSummary,
                                color = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        IconButton(
                            onClick = { onRunRuleNow(rule) },
                            modifier = Modifier.testTag("run_rule_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test rule ${rule.name} now",
                                tint = WemoGreenPrimary
                            )
                        }

                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { onToggleRule(rule) },
                            colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary),
                            modifier = Modifier.testTag("toggle_rule_${rule.id}")
                        )

                        IconButton(
                            onClick = { onDeleteRule(rule) },
                            modifier = Modifier.testTag("delete_rule_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete rule ${rule.name}",
                                tint = WemoTextSecondary
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                }
            }
        }
    }
}

private fun hardwareRulesSortTier(
    device: DeviceEntity,
    snapshot: DeviceHardwareRulesSnapshot?
): Int {
    val cap = snapshot?.capability ?: device.parsedHardwareRulesCapability
    val hasStoredRules = snapshot?.rules?.any { it.appliesToInspectedSwitch } == true
    return when {
        hasStoredRules -> 0
        device.isReachable && cap.isSupportedOrLikely -> 1
        device.isReachable && cap == HardwareRulesCapability.UNKNOWN -> 2
        device.isReachable -> 3
        else -> 4
    }
}
