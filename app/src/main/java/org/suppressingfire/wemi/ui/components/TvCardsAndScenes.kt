package org.suppressingfire.wemi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenTop

internal val TvDimmerGold = Color(0xFFFBBF24)
internal val TvFocusedCardBg = Color(0xFF133B66)
internal val TvDimmingCardBg = Color(0xFF232014)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvDeviceCard(
    device: DeviceEntity,
    isFocusedByRemote: Boolean,
    isDimmingMode: Boolean,
    isToggling: Boolean,
    focusRequester: FocusRequester? = null,
    onSelectFocus: () -> Unit,
    onPrimaryAction: () -> Unit,
    onToggleDimmingMode: () -> Unit,
    onExitDimmingMode: () -> Unit,
    onStepBrightness: (Int) -> Unit
) {
    var hasHardwareFocus by remember { mutableStateOf(false) }
    val isHighlighted = isFocusedByRemote || hasHardwareFocus
    val type = device.parsedType
    val isDimmer = type == DeviceType.LIGHTBULB_DIMMER && device.isReachable

    val cardColor by animateColorAsState(
        targetValue = when {
            isDimmingMode && isDimmer -> TvDimmingCardBg
            isHighlighted -> TvFocusedCardBg
            !device.isReachable -> Color(0xFF111C2D)
            device.isOn && type != DeviceType.MOTION_SENSOR -> TvSurfaceCardActive
            else -> TvSurfaceCard
        },
        label = "tvCardBg"
    )

    val borderStroke = when {
        isDimmingMode && isDimmer -> BorderStroke(4.dp, TvDimmerGold)
        isHighlighted -> BorderStroke(3.5.dp, TvCyanFocusBorder)
        device.isReachable && device.isOn && type != DeviceType.MOTION_SENSOR ->
            BorderStroke(1.5.dp, WemoGreenPrimary.copy(alpha = 0.65f))
        else -> BorderStroke(1.dp, Color(0xFF1E293B))
    }

    val baseModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        shadowElevation = if (isHighlighted || isDimmingMode) 12.dp else 2.dp,
        modifier = baseModifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = if (isHighlighted || isDimmingMode) 1.022f else 1f
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { state ->
                hasHardwareFocus = state.isFocused
                if (state.isFocused) {
                    onSelectFocus()
                } else if (isDimmingMode) {
                    onExitDimmingMode()
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (isDimmingMode && isDimmer) {
                        when (event.key) {
                            Key.DirectionLeft, Key.Minus -> {
                                onStepBrightness(-10)
                                true
                            }
                            Key.DirectionRight, Key.Equals, Key.Plus -> {
                                onStepBrightness(10)
                                true
                            }
                            Key.DirectionUp -> {
                                onStepBrightness(20)
                                true
                            }
                            Key.DirectionDown -> {
                                onStepBrightness(-20)
                                true
                            }
                            Key.DirectionCenter,
                            Key.Enter,
                            Key.NumPadEnter,
                            Key.Back,
                            Key.Escape,
                            Key.MediaPlayPause,
                            Key.Menu -> {
                                onExitDimmingMode()
                                true
                            }
                            else -> false
                        }
                    } else {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onPrimaryAction()
                                true
                            }
                            Key.MediaPlayPause,
                            Key.Menu,
                            Key.Info,
                            Key.LeftBracket,
                            Key.RightBracket -> {
                                if (isDimmer) {
                                    onToggleDimmingMode()
                                    true
                                } else {
                                    false
                                }
                            }
                            // Never hijack DirectionLeft or DirectionRight in normal mode so
                            // 3-column D-Pad navigation between cards is always fast and predictable.
                            else -> false
                        }
                    }
                } else {
                    false
                }
            }
            .focusable()
            .combinedClickable(
                onClick = {
                    onSelectFocus()
                    if (isDimmingMode) {
                        onExitDimmingMode()
                    } else {
                        onPrimaryAction()
                    }
                },
                onLongClick = {
                    onSelectFocus()
                    if (isDimmer) {
                        onToggleDimmingMode()
                    } else {
                        onPrimaryAction()
                    }
                }
            )
            .testTag("tv_device_card_${device.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            // Top Line: Switch Name + unmistakable 10-foot Focus / Dimming indicator badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AutoResizingDeviceNameText(
                    text = device.name,
                    color = TvTextPrimary,
                    initialFontSizeSp = 16.5f,
                    minFontSizeSp = 11f,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tv_device_name_${device.id}")
                )

                if (isDimmingMode && isDimmer) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TvDimmerGold
                    ) {
                        Text(
                            text = "DIM -/+",
                            color = TvDeepBackground,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (isHighlighted) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TvCyanFocusBorder
                    ) {
                        Text(
                            text = "FOCUSED • OK",
                            color = TvDeepBackground,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            val statusLabel = when {
                isToggling -> "Sending SOAP SetBinaryState..."
                !device.isReachable -> "Disconnected • Press OK to Retry"
                type == DeviceType.MOTION_SENSOR -> "${device.connectionBadgeText} • Motion: ${device.lastMotionTime ?: "Active"}"
                type == DeviceType.LIGHTBULB_DIMMER ->
                    if (device.isOn) "ON • ${device.brightness}% Brightness" else "OFF • Standby (${device.brightness}%)"
                else -> if (device.isOn) "ON • ${device.connectionBadgeText}" else "OFF • ${device.connectionBadgeText}"
            }

            // Second Line: Hardware Illustration + Category/Status Telemetry + Power Indicator Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                DeviceCategoryIllustration(
                    category = device.visualCategory,
                    isReachable = device.isReachable,
                    isOn = device.isOn,
                    brightness = device.brightness,
                    modifier = Modifier.size(width = 34.dp, height = 42.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (isHighlighted) {
                                TvCyanFocusBorder.copy(alpha = 0.22f)
                            } else {
                                Color.White.copy(alpha = 0.08f)
                            }
                        ) {
                            Text(
                                text = device.visualCategory.displayName,
                                color = TvCyanFocusBorder,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = device.ipAddress,
                            color = TvTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = statusLabel,
                        color = when {
                            !device.isReachable -> Color(0xFFF87171)
                            isDimmingMode -> TvDimmerGold
                            device.isOn -> TvCyanFocusBorder
                            else -> Color(0xFF94A3B8)
                        },
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
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
                    modifier = Modifier.size(38.dp)
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isDimmer) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDimmingMode) {
                        TvDimmerGold.copy(alpha = 0.20f)
                    } else {
                        Color.White.copy(alpha = 0.06f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDimmingMode) {
                            TvDimmerGold
                        } else if (isHighlighted) {
                            TvCyanFocusBorder.copy(alpha = 0.45f)
                        } else {
                            Color.Transparent
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false }
                        .clickable {
                            onSelectFocus()
                            onToggleDimmingMode()
                        }
                        .testTag("tv_card_dim_mode_${device.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .focusProperties { canFocus = false }
                                    .clickable {
                                        onSelectFocus()
                                        onStepBrightness(-10)
                                    }
                                    .testTag("tv_card_dim_minus_${device.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease brightness",
                                        tint = if (isDimmingMode) TvDimmerGold else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (device.brightness / 100f).coerceIn(0f, 1f) },
                                color = if (isDimmingMode) TvDimmerGold else TvCyanFocusBorder,
                                trackColor = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Text(
                                text = "${device.brightness}%",
                                color = if (isDimmingMode) TvDimmerGold else Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )

                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .focusProperties { canFocus = false }
                                    .clickable {
                                        onSelectFocus()
                                        onStepBrightness(10)
                                    }
                                    .testTag("tv_card_dim_plus_${device.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase brightness",
                                        tint = if (isDimmingMode) TvDimmerGold else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (isDimmingMode) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Left: -10%  •  Right: +10%   (OK or Back = Done)",
                                color = TvDimmerGold,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        } else if (isHighlighted) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = TvTextSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Long-press OK or press Play/Menu to adjust dimmer",
                                    color = TvTextSecondary,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvRoomSectionHeader(
    roomName: String,
    roomDevices: List<DeviceEntity>,
    isTogglingRoom: Boolean,
    onToggleRoomPower: () -> Unit,
    onStepRoomBrightness: ((Int) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val controllable = roomDevices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
    val onCount = controllable.count { it.isOn && it.isReachable }
    val dimmers = roomDevices.filter { it.parsedType == DeviceType.LIGHTBULB_DIMMER && it.isReachable }
    val avgBrightness = if (dimmers.isNotEmpty()) {
        dimmers.map { it.brightness }.average().toInt()
    } else {
        null
    }

    Surface(
        color = if (isFocused) Color(0xFF133B66) else Color(0xFF0B192C),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) TvCyanFocusBorder else Color(0xFF1E3A5F)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onToggleRoomPower()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onToggleRoomPower() }
            .testTag("tv_room_header_$roomName")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (onCount > 0) TvCyanFocusBorder else Color(0xFF475569))
                )
                Text(
                    text = roomName.uppercase(),
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (onCount > 0) WemoGreenPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (isTogglingRoom) {
                            "Switching..."
                        } else {
                            "$onCount/${controllable.size} ON"
                        },
                        color = if (onCount > 0) TvCyanFocusBorder else TvTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (avgBrightness != null) {
                    Text(
                        text = "Dimmers: $avgBrightness%",
                        color = TvTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (avgBrightness != null && onStepRoomBrightness != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .clickable { onStepRoomBrightness(-10) }
                            .testTag("tv_room_dim_minus_$roomName")
                    ) {
                        Text(
                            text = "-10%",
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .clickable { onStepRoomBrightness(10) }
                            .testTag("tv_room_dim_plus_$roomName")
                    ) {
                        Text(
                            text = "+10%",
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = if (isFocused) "OK: Toggle Room" else "Room Master",
                    color = if (isFocused) TvCyanFocusBorder else TvTextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                )
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
        color = if (isFocused) Color(0xFF133B66) else Color(0xFF0F2540),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) TvCyanFocusBorder else TvCyanFocusBorder.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvCyanFocusBorder,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}
