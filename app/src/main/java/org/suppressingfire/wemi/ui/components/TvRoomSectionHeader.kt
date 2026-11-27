package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary

/**
 * Full-span 3-column room master bar (`tv_room_header_<roomName>`) for the 10-foot Android TV
 * dashboard. Supports D-Pad `OK` room power toggle, `Up`/`Down` grid traversal, and `Left`/`Right`
 * `-10%` / `+10%` room dimmer stepping when the room contains reachable dimmers.
 */
@Composable
internal fun TvRoomSectionHeader(
    roomName: String,
    roomDevices: List<DeviceEntity>,
    isFocusedByRemote: Boolean,
    isTogglingRoom: Boolean,
    focusRequester: FocusRequester? = null,
    onSelectRoomFocus: () -> Unit,
    onNavigateUp: () -> Boolean,
    onNavigateDown: () -> Boolean,
    onToggleRoomPower: () -> Unit,
    onStepRoomBrightness: ((Int) -> Unit)? = null
) {
    var hasHardwareFocus by remember { mutableStateOf(false) }
    val currentOnToggleRoomPower by rememberUpdatedState(onToggleRoomPower)
    val currentOnNavigateUp by rememberUpdatedState(onNavigateUp)
    val currentOnNavigateDown by rememberUpdatedState(onNavigateDown)
    val currentOnStepRoomBrightness by rememberUpdatedState(onStepRoomBrightness)
    val isFocused = isFocusedByRemote || hasHardwareFocus
    val controllable = roomDevices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
    val onCount = controllable.count { it.isOn && it.isReachable }
    val dimmers = roomDevices.filter { it.parsedType == DeviceType.LIGHTBULB_DIMMER && it.isReachable }
    val avgBrightness = if (dimmers.isNotEmpty()) {
        dimmers.map { it.brightness }.average().toInt()
    } else {
        null
    }

    Surface(
        color = if (isFocused) TvFocusedCardBg else Color(0xFF0B192C),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isFocused) 3.5.dp else 1.dp,
            color = if (isFocused) TvCyanFocusBorder else Color(0xFF1E3A5F)
        ),
        shadowElevation = if (isFocused) 10.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (focusRequester != null) mod.focusRequester(focusRequester) else mod }
            .onFocusChanged { state ->
                hasHardwareFocus = state.isFocused
                if (state.isFocused) {
                    onSelectRoomFocus()
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            currentOnToggleRoomPower()
                            true
                        }
                        Key.DirectionUp -> currentOnNavigateUp()
                        Key.DirectionDown -> currentOnNavigateDown()
                        Key.DirectionLeft, Key.Minus -> {
                            currentOnStepRoomBrightness?.invoke(-10)
                            true
                        }
                        Key.DirectionRight, Key.Equals, Key.Plus -> {
                            currentOnStepRoomBrightness?.invoke(10)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            .focusProperties { canFocus = false }
            .clickable {
                onSelectRoomFocus()
                focusRequester?.let { runCatching { it.requestFocus() } }
                onToggleRoomPower()
            }
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
                            .clickable {
                                onSelectRoomFocus()
                                focusRequester?.let { runCatching { it.requestFocus() } }
                                onStepRoomBrightness(-10)
                            }
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
                            .clickable {
                                onSelectRoomFocus()
                                focusRequester?.let { runCatching { it.requestFocus() } }
                                onStepRoomBrightness(10)
                            }
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

                if (isFocused) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TvCyanFocusBorder
                    ) {
                        Text(
                            text = if (onStepRoomBrightness != null) {
                                "FOCUSED • OK TOGGLE • L/R DIM"
                            } else {
                                "FOCUSED • OK TOGGLE ROOM"
                            },
                            color = TvDeepBackground,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Room Master",
                        color = TvTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
