package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.ui.theme.WemoDividerGray
import com.example.ui.theme.WemoErrorRed
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft
import com.example.ui.theme.WemoRowExpandedSurface
import com.example.ui.theme.WemoRowSurface
import com.example.ui.theme.WemoTextPrimary
import com.example.ui.theme.WemoTextSecondary
import com.example.ui.theme.WorkSansFontFamily
import kotlin.math.roundToInt

@Composable
fun WeMoDeviceRow(
    device: WeMoDeviceEntity,
    isExpanded: Boolean,
    isEditMode: Boolean,
    isToggling: Boolean,
    onTogglePower: () -> Unit,
    onToggleExpand: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onTriggerMotion: () -> Unit,
    onReconnect: () -> Unit,
    onEditDevice: () -> Unit,
    onDeleteDevice: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = device.parsedType

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WemoRowSurface)
            .testTag("device_row_${device.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    when {
                        isEditMode -> onEditDevice()
                        !device.isReachable -> onReconnect()
                        type == WeMoDeviceType.MOTION_SENSOR -> onTriggerMotion()
                        else -> onToggleExpand()
                    }
                }
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Realistic Hardware Illustration matching the screenshot
            Box(
                modifier = Modifier.width(66.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                when (type) {
                    WeMoDeviceType.SWITCH_PLUG -> WeMoSmartPlugIllustration(
                        isReachable = device.isReachable,
                        isOn = device.isOn
                    )
                    WeMoDeviceType.MOTION_SENSOR -> WeMoMotionSensorIllustration(
                        isReachable = device.isReachable
                    )
                    WeMoDeviceType.LIGHTBULB_DIMMER -> WeMoLightbulbIllustration(
                        isReachable = device.isReachable,
                        isOn = device.isOn,
                        brightness = device.brightness
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle: Device Name + Status / Percentage + Expand Chevron
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = device.name,
                    color = if (device.isReachable) WemoTextPrimary else WemoTextPrimary.copy(alpha = 0.75f),
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                when {
                    !device.isReachable -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.status_not_detected),
                            color = WemoTextSecondary,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = 15.sp
                        )
                    }
                    type == WeMoDeviceType.MOTION_SENSOR -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.status_motion_sensed, device.lastMotionTime),
                            color = WemoTextSecondary,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Light,
                            fontSize = 15.sp
                        )
                    }
                    type == WeMoDeviceType.LIGHTBULB_DIMMER -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (device.isOn) "${device.brightness}%" else "Off (${device.brightness}%)",
                            color = if (device.isOn) WemoGreenPrimary else WemoTextSecondary,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = stringResource(
                                R.string.content_desc_expand_dimmer,
                                device.name
                            ),
                            tint = WemoGreenPrimary,
                            modifier = Modifier
                                .size(22.dp)
                                .testTag("expand_chevron_${device.id}")
                        )
                    }
                    else -> {
                        // Standard WeMo Switch / Plug: keep clean vertical alignment like screenshot
                        if (isExpanded || isEditMode) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${device.ipAddress}:${device.port} • ${if (device.isOn) "ON" else "OFF"}",
                                color = WemoTextSecondary,
                                fontFamily = WorkSansFontFamily,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Right: Edit mode controls OR Power Toggle Ring button
            if (isEditMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move ${device.name} up",
                            tint = WemoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move ${device.name} down",
                            tint = WemoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onEditDevice,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("edit_device_${device.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Configure ${device.name}",
                            tint = WemoGreenDark
                        )
                    }
                    IconButton(
                        onClick = onDeleteDevice,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("delete_device_${device.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${device.name}",
                            tint = WemoErrorRed
                        )
                    }
                }
            } else {
                when {
                    !device.isReachable -> {
                        // In the screenshot, "Hall Lightbulb (Not Detected)" has no power ring on the right.
                        // Tapping the row reconnects it, and we also provide an accessible subtle retry button if expanded.
                        Spacer(modifier = Modifier.width(64.dp))
                    }
                    type == WeMoDeviceType.MOTION_SENSOR -> {
                        // In the screenshot, "Bedroom Light Motion" has no power ring on the right.
                        Spacer(modifier = Modifier.width(64.dp))
                    }
                    else -> {
                        WeMoPowerRingButton(
                            isOn = device.isOn,
                            isToggling = isToggling,
                            deviceName = device.name,
                            onClick = onTogglePower,
                            modifier = Modifier.testTag("power_button_${device.id}")
                        )
                    }
                }
            }
        }

        // Expandable inline dimmer & UPnP controls drawer
        AnimatedVisibility(
            visible = isExpanded && !isEditMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            WeMoExpandedDrawer(
                device = device,
                onBrightnessChange = onBrightnessChange,
                onTriggerMotion = onTriggerMotion,
                onReconnect = onReconnect,
                onEditDevice = onEditDevice
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = WemoDividerGray
        )
    }
}

@Composable
private fun WeMoExpandedDrawer(
    device: WeMoDeviceEntity,
    onBrightnessChange: (Int) -> Unit,
    onTriggerMotion: () -> Unit,
    onReconnect: () -> Unit,
    onEditDevice: () -> Unit
) {
    val type = device.parsedType
    var sliderVal by remember(device.brightness) {
        mutableFloatStateOf(device.brightness.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WemoRowExpandedSurface)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (type == WeMoDeviceType.LIGHTBULB_DIMMER && device.isReachable) {
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
                    fontSize = 15.sp
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

            // Quick dimmer presets (including 21% from screenshot!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(21, 50, 75, 100).forEach { preset ->
                    FilterChip(
                        selected = device.brightness == preset && device.isOn,
                        onClick = {
                            sliderVal = preset.toFloat()
                            onBrightnessChange(preset)
                        },
                        label = { Text("$preset%") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Local UPnP endpoint metadata & quick actions
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
                } else if (type == WeMoDeviceType.MOTION_SENSOR) {
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
