package org.suppressingfire.wemi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    isToggleFocusedByRemote: Boolean,
    isSliderFocusedByRemote: Boolean,
    isToggling: Boolean,
    toggleFocusRequester: FocusRequester? = null,
    sliderFocusRequester: FocusRequester? = null,
    onSelectToggleFocus: () -> Unit,
    onSelectSliderFocus: () -> Unit,
    onPrimaryAction: () -> Unit,
    onStepBrightness: (Int) -> Unit,
    onNavigateLeft: () -> Boolean,
    onNavigateRight: () -> Boolean,
    onNavigateUpFromToggle: () -> Boolean,
    onNavigateDownFromToggle: () -> Boolean,
    onNavigateUpFromSlider: () -> Boolean,
    onNavigateDownFromSlider: () -> Boolean
) {
    var hasToggleHardwareFocus by remember { mutableStateOf(false) }
    val currentOnPrimaryAction by rememberUpdatedState(onPrimaryAction)
    val currentOnNavigateLeft by rememberUpdatedState(onNavigateLeft)
    val currentOnNavigateRight by rememberUpdatedState(onNavigateRight)
    val currentOnNavigateUpFromToggle by rememberUpdatedState(onNavigateUpFromToggle)
    val currentOnNavigateDownFromToggle by rememberUpdatedState(onNavigateDownFromToggle)
    val isToggleHighlighted = isToggleFocusedByRemote || hasToggleHardwareFocus
    val isSliderHighlighted = isSliderFocusedByRemote
    val isCardHighlighted = isToggleHighlighted || isSliderHighlighted
    val type = device.parsedType
    val isDimmer = type == DeviceType.LIGHTBULB_DIMMER && device.isReachable
    val currentIsDimmer by rememberUpdatedState(isDimmer)

    val cardColor by animateColorAsState(
        targetValue = when {
            isSliderHighlighted && isDimmer -> TvDimmingCardBg
            isToggleHighlighted -> TvFocusedCardBg
            !device.isReachable -> Color(0xFF111C2D)
            device.isOn && type != DeviceType.MOTION_SENSOR -> TvSurfaceCardActive
            else -> TvSurfaceCard
        },
        label = "tvCardBg"
    )

    val borderStroke = when {
        isSliderHighlighted && isDimmer -> BorderStroke(4.dp, TvDimmerGold)
        isToggleHighlighted -> BorderStroke(3.5.dp, TvCyanFocusBorder)
        device.isReachable && device.isOn && type != DeviceType.MOTION_SENSOR ->
            BorderStroke(1.5.dp, WemoGreenPrimary.copy(alpha = 0.65f))
        else -> BorderStroke(1.dp, Color(0xFF1E293B))
    }

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        shadowElevation = if (isCardHighlighted) 12.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = if (isCardHighlighted) 1.022f else 1f
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            // SIBLING 1: On/Off Toggle Focusable Zone (navigating Left/Right moves between switch cards;
            // navigating Down on a dimmer card focuses the Dimmer Slider below)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isToggleHighlighted && isDimmer) {
                    TvCyanFocusBorder.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
                border = if (isToggleHighlighted && isDimmer) {
                    BorderStroke(1.5.dp, TvCyanFocusBorder.copy(alpha = 0.85f))
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (toggleFocusRequester != null) mod.focusRequester(toggleFocusRequester) else mod
                    }
                    .onFocusChanged { state ->
                        hasToggleHardwareFocus = state.isFocused
                        if (state.isFocused) {
                            onSelectToggleFocus()
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    currentOnPrimaryAction()
                                    true
                                }
                                Key.DirectionLeft -> currentOnNavigateLeft()
                                Key.DirectionRight -> currentOnNavigateRight()
                                Key.DirectionUp -> currentOnNavigateUpFromToggle()
                                Key.DirectionDown -> currentOnNavigateDownFromToggle()
                                Key.MediaPlayPause,
                                Key.Menu,
                                Key.Info,
                                Key.LeftBracket,
                                Key.RightBracket -> {
                                    if (currentIsDimmer) {
                                        currentOnNavigateDownFromToggle()
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
                    .focusProperties { canFocus = false }
                    .combinedClickable(
                        onClick = {
                            onSelectToggleFocus()
                            onPrimaryAction()
                        },
                        onLongClick = {
                            if (isDimmer) {
                                onNavigateDownFromToggle()
                            } else {
                                onSelectToggleFocus()
                                onPrimaryAction()
                            }
                        }
                    )
                    .testTag("tv_device_card_${device.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp)
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

                        if (isSliderHighlighted && isDimmer) {
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
                        } else if (isToggleHighlighted) {
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
                                    color = if (isCardHighlighted) {
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
                                    isSliderHighlighted -> TvDimmerGold
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

                        // Distinct Power / Connection Status Ring (highlighted when On/Off Toggle is focused)
                        Surface(
                            shape = CircleShape,
                            color = when {
                                !device.isReachable -> Color(0xFF450A0A)
                                type == DeviceType.MOTION_SENSOR -> WemoGreenPrimary.copy(alpha = 0.25f)
                                device.isOn -> WemoGreenTop
                                else -> PowerOffDarkGray
                            },
                            border = BorderStroke(
                                width = if (isToggleHighlighted) 2.5.dp else 2.dp,
                                color = when {
                                    !device.isReachable -> Color(0xFFEF4444)
                                    isToggleHighlighted -> TvCyanFocusBorder
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
                }
            }

            // SIBLING 2: Focusable Dimmer Slider Strip (navigating Down from On/Off Toggle focuses the slider;
            // Left/Right lowers/raises brightness; Down moves to the widget below)
            if (isDimmer) {
                Spacer(modifier = Modifier.height(6.dp))
                TvDeviceCardDimmerStrip(
                    device = device,
                    isSliderFocused = isSliderHighlighted,
                    isToggleHighlighted = isToggleHighlighted,
                    sliderFocusRequester = sliderFocusRequester,
                    onSelectSliderFocus = onSelectSliderFocus,
                    onToggleSliderFocus = {
                        if (isSliderHighlighted) {
                            onNavigateUpFromSlider()
                        } else {
                            onNavigateDownFromToggle()
                        }
                    },
                    onStepBrightness = onStepBrightness,
                    onNavigateUp = onNavigateUpFromSlider,
                    onNavigateDown = onNavigateDownFromSlider
                )
            }
        }
    }
}
