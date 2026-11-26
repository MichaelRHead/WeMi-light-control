package org.suppressingfire.wemi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.ui.components.HardwareRulesDeviceCard
import org.suppressingfire.wemi.ui.components.HardwareRulesHeaderCard
import org.suppressingfire.wemi.ui.components.buildUdnToFriendlyNameMap
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Rules tab dedicated exclusively to native on-device switch rules (`rules.db` Long Press,
 * Auto-Off Delay, Schedules & Sunrise/Sunset, Timed Actions, clock sync, solar location, and
 * backup recovery) that run inside switch memory even when the phone is off.
 */
@Composable
fun RulesScreen(
    homeDevices: List<DeviceEntity> = emptyList(),
    hardwareRulesByDeviceId: Map<Int, DeviceHardwareRulesSnapshot> = emptyMap(),
    inspectingRuleDeviceIds: Set<Int> = emptySet(),
    isScanningAllDeviceRules: Boolean = false,
    onScanAllHardwareRules: () -> Unit = {},
    onAddHardwareRule: (DeviceEntity?) -> Unit = {},
    onInspectDeviceHardwareRules: (DeviceEntity, Boolean) -> Unit = { _, _ -> },
    onToggleHardwareRule: (DeviceEntity, HardwareRule, Boolean) -> Unit = { _, _, _ -> },
    onEditHardwareRule: (DeviceEntity, HardwareRule) -> Unit = { _, _ -> },
    onDeleteHardwareRule: (DeviceEntity, HardwareRule) -> Unit = { _, _ -> },
    onSyncDeviceClock: (DeviceEntity) -> Unit = {},
    onEditSolarLocation: (DeviceEntity) -> Unit = {},
    onOpenBackups: (DeviceEntity) -> Unit = {},
    onOpenDevicesTab: () -> Unit = {},
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WemoBackgroundGray)
            .testTag("rules_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "hardware_rules_header") {
            HardwareRulesHeaderCard(
                devices = controllableDevices,
                snapshotsByDeviceId = hardwareRulesByDeviceId,
                isScanningAll = isScanningAllDeviceRules,
                onScanAllClick = onScanAllHardwareRules,
                onAddRuleClick = { onAddHardwareRule(null) }
            )
        }

        if (controllableDevices.isEmpty()) {
            item(key = "hardware_rules_empty") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = WemoRowSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hardware_rules_empty_state")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                            Text(
                                text = "No switches in this home yet",
                                color = WemoTextPrimary,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Discover switches on the Devices tab to inspect and manage on-device Long Press, Auto-Off delays, and schedules stored directly inside each switch.",
                                color = WemoTextSecondary,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenDevicesTab,
                                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("rules_empty_go_to_devices_button")
                            ) {
                                Text("Go to Devices")
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                }
            }
        } else {
            sortedControllableDevices.forEach { device ->
                val snapshot = hardwareRulesByDeviceId[device.id]
                val effectiveCap = snapshot?.capability ?: device.parsedHardwareRulesCapability
                val canManage = device.isReachable && effectiveCap != HardwareRulesCapability.VERIFIED_UNSUPPORTED
                val isInspecting = device.id in inspectingRuleDeviceIds
                val deviceRules = snapshot?.rules?.filter { it.appliesToInspectedSwitch }.orEmpty()

                item(key = "hw_rules_dev_${device.id}") {
                    HardwareRulesDeviceCard(
                        device = device,
                        snapshot = snapshot,
                        isInspecting = isInspecting,
                        onReadDeviceRules = { forceRecheck ->
                            onInspectDeviceHardwareRules(device, forceRecheck)
                        },
                        onAddRuleForDevice = { onAddHardwareRule(device) },
                        onSyncClock = { onSyncDeviceClock(device) },
                        onEditSolarLocation = { onEditSolarLocation(device) },
                        onOpenBackups = { onOpenBackups(device) }
                    )
                }

                if (effectiveCap != HardwareRulesCapability.VERIFIED_UNSUPPORTED && deviceRules.isNotEmpty()) {
                    items(
                        items = deviceRules,
                        key = { rule -> "hw_rule_${device.id}_${rule.ruleId}" }
                    ) { rule ->
                        org.suppressingfire.wemi.ui.components.HardwareRuleSummaryRow(
                            deviceId = device.id,
                            rule = rule,
                            udnToName = udnToName,
                            controlsEnabled = canManage && !isInspecting,
                            onToggleEnabled = { enabled -> onToggleHardwareRule(device, rule, enabled) },
                            onEditClick = { onEditHardwareRule(device, rule) },
                            onDeleteClick = { onDeleteHardwareRule(device, rule) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp)
                        )
                    }
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
