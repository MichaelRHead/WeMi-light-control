package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NetworkCheck
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.WeMoConnectionQuality
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

private val StatusActiveEmerald = Color(0xFF059669)
private val StatusActiveSoftBg = Color(0xFFECFDF5)
private val StatusGoodTeal = Color(0xFF0891B2)
private val StatusSlowAmber = Color(0xFFD97706)
private val StatusSlowSoftBg = Color(0xFFFFFBEB)
private val StatusOfflineRed = Color(0xFFDC2626)
private val StatusOfflineSoftBg = Color(0xFFFEF2F2)

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
    onPingLatency: () -> Unit = onReconnect,
    onToggleNightMode: (() -> Unit)? = null,
    autoOffRemainingSeconds: Int? = null,
    modifier: Modifier = Modifier
) {
    val type = device.parsedType

    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (device.isReachable && device.isOn) 2.dp else 0.dp,
        shadowElevation = if (device.isReachable && device.isOn) 2.dp else 0.5.dp,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !device.isReachable -> StatusOfflineRed.copy(alpha = 0.25f)
                device.isOn && type != WeMoDeviceType.MOTION_SENSOR ->
                    WemoGreenPrimary.copy(alpha = 0.28f)
                else -> WemoDividerGray
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("device_row_${device.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Hardware Illustration with live connection status dot overlay
                Box(
                    modifier = Modifier.width(64.dp),
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

                // Middle: Device Name + Room Badge + Real-Time Connection/Latency Indicator + Status
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = device.name,
                            color = if (device.isReachable) WemoTextPrimary else WemoTextPrimary.copy(alpha = 0.65f),
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            color = WemoGreenSoft.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = device.roomName,
                                color = WemoGreenDark,
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Real-Time Connection Status & Latency Visual Indicator Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WeMoConnectionStatusBadge(
                            device = device,
                            isPinging = isToggling,
                            modifier = Modifier.testTag("connection_status_badge_${device.id}")
                        )

                        when {
                            !device.isReachable -> {
                                Text(
                                    text = stringResource(R.string.status_not_detected),
                                    color = WemoTextSecondary,
                                    fontFamily = WorkSansFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            type == WeMoDeviceType.MOTION_SENSOR -> {
                                Text(
                                    text = stringResource(R.string.status_motion_sensed, device.lastMotionTime),
                                    color = WemoGreenDark,
                                    fontFamily = WorkSansFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            type == WeMoDeviceType.LIGHTBULB_DIMMER -> {
                                Surface(
                                    color = if (device.isOn) WemoGreenSoft else WemoDividerGray.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (device.isOn) "${device.brightness}%" else "Off • ${device.brightness}%",
                                        color = if (device.isOn) WemoGreenDark else WemoTextSecondary,
                                        fontFamily = WorkSansFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
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
                                        .size(18.dp)
                                        .testTag("expand_chevron_${device.id}")
                                )
                            }
                            else -> {
                                val subLabel = if (autoOffRemainingSeconds != null && autoOffRemainingSeconds > 0) {
                                    val mins = autoOffRemainingSeconds / 60
                                    val secs = autoOffRemainingSeconds % 60
                                    "${device.ipAddress} • Auto-Off ${mins}:${secs.toString().padStart(2, '0')}"
                                } else {
                                    device.ipAddress
                                }
                                Text(
                                    text = subLabel,
                                    color = WemoTextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Right: Edit mode controls, Reconnect pill (for Offline), OR Cyan-Teal Power Toggle Ring
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
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StatusOfflineSoftBg,
                                border = BorderStroke(1.dp, StatusOfflineRed.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(onClick = onReconnect)
                                    .testTag("reconnect_button_${device.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reconnect ${device.name}",
                                        tint = StatusOfflineRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Retry",
                                        color = StatusOfflineRed,
                                        fontFamily = WorkSansFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        type == WeMoDeviceType.MOTION_SENSOR -> {
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

            // Expandable inline dimmer & UPnP telemetry drawer
            AnimatedVisibility(
                visible = isExpanded && !isEditMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                    WeMoExpandedDrawer(
                        device = device,
                        onBrightnessChange = onBrightnessChange,
                        onTriggerMotion = onTriggerMotion,
                        onReconnect = onReconnect,
                        onPingLatency = onPingLatency,
                        onEditDevice = onEditDevice,
                        onToggleNightMode = onToggleNightMode
                    )
                }
            }
        }
    }
}

/**
 * Real-time connection status & latency indicator pill displayed on every discovered switch.
 * Shows an animated pulsing status LED, a 4-bar UPnP signal meter, and round-trip latency (e.g., "Active • 11ms" or "Offline").
 */
@Composable
fun WeMoConnectionStatusBadge(
    device: WeMoDeviceEntity,
    isPinging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val quality = device.connectionQuality

    val (dotColor, bgColor, textColor) = when (quality) {
        WeMoConnectionQuality.EXCELLENT ->
            Triple(StatusActiveEmerald, StatusActiveSoftBg, StatusActiveEmerald)
        WeMoConnectionQuality.GOOD ->
            Triple(StatusGoodTeal, WemoGreenSoft.copy(alpha = 0.7f), WemoGreenDark)
        WeMoConnectionQuality.SLOW ->
            Triple(StatusSlowAmber, StatusSlowSoftBg, StatusSlowAmber)
        WeMoConnectionQuality.OFFLINE ->
            Triple(StatusOfflineRed, StatusOfflineSoftBg, StatusOfflineRed)
    }

    val pulseTransition = rememberInfiniteTransition(label = "connPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (quality == WeMoConnectionQuality.OFFLINE) 0.4f else 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPinging) 350 else 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.8.dp, dotColor.copy(alpha = 0.32f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            // Pulsing Connection Status Dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = pulseAlpha))
            )

            // 4-Bar Signal Quality Meter
            WeMoSignalBarsIndicator(
                activeBars = quality.signalBars,
                activeColor = dotColor
            )

            // Status + Latency Text (e.g. "Active • 11ms" or "Offline • Timeout")
            Text(
                text = if (isPinging) "Pinging..." else device.connectionBadgeText,
                color = textColor,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * 4-bar wireless UPnP signal quality & latency strength indicator drawn on Canvas.
 */
@Composable
fun WeMoSignalBarsIndicator(
    activeBars: Int,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 13.dp, height = 10.dp)) {
        val barCount = 4
        val gap = 1.6.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val fraction = (i + 1) / barCount.toFloat()
            val barHeight = size.height * (0.32f + 0.68f * fraction)
            val x = i * (barWidth + gap)
            val y = size.height - barHeight
            val color = if (i < activeBars) {
                activeColor
            } else {
                activeColor.copy(alpha = 0.22f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun WeMoExpandedDrawer(
    device: WeMoDeviceEntity,
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
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Real-Time Connection & Latency Telemetry Banner inside Expanded Drawer
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, WemoDividerGray),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                if (onToggleNightMode != null) {
                    FilterChip(
                        selected = device.nightModeEnabled,
                        onClick = onToggleNightMode,
                        label = { Text("Night Mode (20%)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WemoGreenSoft,
                            selectedLabelColor = WemoGreenDark
                        ),
                        modifier = Modifier.testTag("night_mode_chip_${device.id}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
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
