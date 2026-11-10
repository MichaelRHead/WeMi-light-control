package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoRowExpandedSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary
import org.suppressingfire.wemi.ui.theme.WorkSansFontFamily
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedDrawer(
    device: DeviceEntity,
    onBrightnessChange: (Int) -> Unit,
    onTriggerMotion: () -> Unit,
    onReconnect: () -> Unit,
    onPingLatency: () -> Unit,
    onEditDevice: () -> Unit,
    onToggleNightMode: (() -> Unit)? = null
) {
    val type = device.parsedType
    var sliderVal by remember(device.brightness) {
        mutableFloatStateOf(device.brightness.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WemoRowExpandedSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (type == DeviceType.LIGHTBULB_DIMMER && device.isReachable) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brightness Level",
                    color = WemoTextPrimary,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "${sliderVal.roundToInt()}%",
                    color = WemoGreenDark,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.testTag("brightness_value_label_${device.id}")
                )
            }

            Slider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                onValueChangeFinished = {
                    onBrightnessChange(sliderVal.roundToInt())
                },
                valueRange = 1f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = WemoGreenPrimary,
                    activeTrackColor = WemoGreenPrimary,
                    inactiveTrackColor = WemoDividerGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("brightness_slider_${device.id}")
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(21, 50, 75, 100).forEach { preset ->
                    FilterChip(
                        selected = device.brightness == preset && device.isOn,
                        onClick = {
                            sliderVal = preset.toFloat()
                            onBrightnessChange(preset)
                        },
                        label = { Text("$preset%", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("brightness_preset_${device.id}_$preset")
                    )
                }
                if (onToggleNightMode != null) {
                    FilterChip(
                        selected = device.nightModeEnabled,
                        onClick = onToggleNightMode,
                        label = { Text("Night Mode (20%)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("night_mode_chip_${device.id}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Real-Time Connection & Latency Telemetry Banner inside Expanded Drawer
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, WemoDividerGray),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (device.isReachable) {
                            "Connection: Active (${device.latencyMs}ms SOAP Round-Trip)"
                        } else {
                            "Connection: Offline (Unreachable on Port ${device.port})"
                        },
                        color = if (device.isReachable) WemoGreenDark else StatusOfflineRed,
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Last Checked: ${device.lastCheckedTime} • Endpoint: ${device.ipAddress}:${device.port}",
                        color = WemoTextSecondary,
                        fontFamily = WorkSansFontFamily,
                        fontSize = 11.sp
                    )
                }

                AssistChip(
                    onClick = onPingLatency,
                    label = { Text("Ping RTT", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "Ping ${device.name} latency",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = WemoGreenDark,
                        labelColor = WemoGreenDark
                    ),
                    modifier = Modifier.testTag("ping_device_${device.id}")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UPnP Endpoint: http://${device.ipAddress}:${device.port}",
                    color = WemoTextSecondary,
                    fontFamily = WorkSansFontFamily,
                    fontSize = 12.sp
                )
                Text(
                    text = "SN: ${device.serialNumber} • ${device.firmwareVersion}",
                    color = WemoTextSecondary,
                    fontFamily = WorkSansFontFamily,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!device.isReachable) {
                    AssistChip(
                        onClick = onReconnect,
                        label = { Text("Reconnect") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = WemoGreenDark,
                            labelColor = WemoGreenDark
                        )
                    )
                } else if (type == DeviceType.MOTION_SENSOR) {
                    AssistChip(
                        onClick = onTriggerMotion,
                        label = { Text("Trigger Motion") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                AssistChip(
                    onClick = onEditDevice,
                    label = { Text("Configure IP") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (device.isReachable) Icons.Default.Edit else Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}
