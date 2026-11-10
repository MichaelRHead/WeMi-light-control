package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceCategory
import com.example.data.DeviceEntity
import com.example.data.DeviceType
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenTop
import com.example.ui.theme.WorkSansFontFamily

@Composable
internal fun TvDeviceCard(
    device: DeviceEntity,
    isFocusedByRemote: Boolean,
    isToggling: Boolean,
    onSelectFocus: () -> Unit,
    onPrimaryAction: () -> Unit,
    onStepBrightness: (Int) -> Unit
) {
    var hasHardwareFocus by remember { mutableStateOf(false) }
    val isHighlighted = isFocusedByRemote || hasHardwareFocus
    val type = device.parsedType

    val cardColor by animateColorAsState(
        targetValue = when {
            !device.isReachable -> Color(0xFF111C2D)
            device.isOn && type != DeviceType.MOTION_SENSOR -> TvSurfaceCardActive
            else -> TvSurfaceCard
        },
        label = "tvCardBg"
    )

    val borderStroke = if (isHighlighted) {
        BorderStroke(3.dp, TvCyanFocusBorder)
    } else if (device.isReachable && device.isOn && type != DeviceType.MOTION_SENSOR) {
        BorderStroke(1.5.dp, WemoGreenPrimary.copy(alpha = 0.65f))
    } else {
        BorderStroke(1.dp, Color(0xFF1E293B))
    }

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = borderStroke,
        shadowElevation = if (isHighlighted) 10.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                hasHardwareFocus = state.isFocused
                if (state.isFocused) {
                    onSelectFocus()
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onPrimaryAction()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (type == DeviceType.LIGHTBULB_DIMMER && device.isReachable) {
                                onStepBrightness(-10)
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (type == DeviceType.LIGHTBULB_DIMMER && device.isReachable) {
                                onStepBrightness(10)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onPrimaryAction() }
            .testTag("tv_device_card_${device.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Line: Full-width unobstructed Switch Name with reactive font size scaling
            AutoResizingDeviceNameText(
                text = device.name,
                color = TvTextPrimary,
                initialFontSizeSp = 18f,
                minFontSizeSp = 11f,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tv_device_name_${device.id}")
            )

            Spacer(modifier = Modifier.height(10.dp))

            val statusLabel = when {
                isToggling -> "Sending SOAP SetBinaryState..."
                !device.isReachable -> "Disconnected • Offline (Press OK to Retry)"
                type == DeviceType.MOTION_SENSOR -> "${device.connectionBadgeText} • Motion: ${device.lastMotionTime ?: "Active"}"
                type == DeviceType.LIGHTBULB_DIMMER ->
                    if (device.isOn) "${device.connectionBadgeText} • ON (${device.brightness}%)" else "${device.connectionBadgeText} • OFF (Standby)"
                else -> if (device.isOn) "${device.connectionBadgeText} • ON (${device.ipAddress})" else "${device.connectionBadgeText} • OFF (${device.ipAddress})"
            }

            // Second Line (Below Name): Hardware Illustration + Category/Status Telemetry + Power Indicator Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeviceCategoryIllustration(
                    category = device.visualCategory,
                    isReachable = device.isReachable,
                    isOn = device.isOn,
                    brightness = device.brightness,
                    modifier = Modifier.size(width = 40.dp, height = 50.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isHighlighted) {
                            TvCyanFocusBorder.copy(alpha = 0.22f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        }
                    ) {
                        Text(
                            text = device.visualCategory.displayName,
                            color = TvCyanFocusBorder,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = statusLabel,
                        color = when {
                            !device.isReachable -> Color(0xFFF87171)
                            device.isOn -> TvCyanFocusBorder
                            else -> Color(0xFFFBBF24)
                        },
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Distinct Power / Connection Status Ring (ON vs OFF dark-gray vs Disconnected)
                Surface(
                    shape = CircleShape,
                    color = when {
                        !device.isReachable -> Color(0xFF450A0A)
                        type == DeviceType.MOTION_SENSOR -> WemoGreenPrimary.copy(alpha = 0.25f)
                        device.isOn -> WemoGreenTop
                        else -> PowerOffDarkGray
                    },
                    border = BorderStroke(
                        width = 2.dp,
                        color = when {
                            !device.isReachable -> Color(0xFFEF4444)
                            device.isOn -> Color.White
                            else -> Color(0xFF475569)
                        }
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                !device.isReachable -> Icons.Default.WifiOff
                                type == DeviceType.MOTION_SENSOR -> Icons.Default.Sensors
                                else -> Icons.Default.PowerSettingsNew
                            },
                            contentDescription = "Toggle ${device.name}",
                            tint = when {
                                !device.isReachable -> Color(0xFFFCA5A5)
                                device.isOn -> TvDeepBackground
                                else -> Color(0xFF94A3B8)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (type == DeviceType.LIGHTBULB_DIMMER && device.isReachable) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onStepBrightness(-10) }
                            .testTag("tv_card_dim_minus_${device.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease brightness",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (device.brightness / 100f).coerceIn(0f, 1f) },
                        color = TvCyanFocusBorder,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onStepBrightness(10) }
                            .testTag("tv_card_dim_plus_${device.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase brightness",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvSceneChip(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        color = if (isFocused) WemoGreenPrimary else Color(0xFF102A4C),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else TvCyanFocusBorder.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvCyanFocusBorder,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
internal fun TvRoomPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (isSelected) TvCyanFocusBorder else Color(0xFF0F2540),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color.White else TvCyanFocusBorder.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = if (isSelected) TvDeepBackground else Color.White,
            fontFamily = WorkSansFontFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
