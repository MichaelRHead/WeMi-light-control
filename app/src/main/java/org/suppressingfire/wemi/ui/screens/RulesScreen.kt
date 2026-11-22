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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.RuleEntity
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
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
    val controllableDevices = homeDevices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
    val udnToName = buildUdnToFriendlyNameMap(homeDevices)
    var showDeviceCards by rememberSaveable { mutableStateOf(false) }
    val hasInspectedSnapshots = controllableDevices.any { it.id in hardwareRulesByDeviceId }
    val expandHardwareDevices = showDeviceCards || hasInspectedSnapshots || isScanningAllDeviceRules

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
            if (!expandHardwareDevices) {
                item(key = "hardware_rules_expand_row") {
                    Surface(color = WemoRowSurface, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inspect per-switch capability & stored rules",
                                color = WemoTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showDeviceCards = true },
                                modifier = Modifier.testTag("toggle_hardware_rules_device_list_button")
                            ) {
                                Text(
                                    text = "Show Switches (${controllableDevices.size})",
                                    color = WemoGreenDark,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                }
            } else {
                items(controllableDevices, key = { "hw_rules_dev_${it.id}" }) { device ->
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
        }

        item(key = "local_app_rules_header") {
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
                        Text(
                            text = "Local Switch Rules & Timers",
                            color = WemoTextPrimary,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Executes directly over LAN without cloud servers",
                            color = WemoTextSecondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = onAddRuleClick,
                        colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                        modifier = Modifier.testTag("add_rule_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Rule")
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
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
