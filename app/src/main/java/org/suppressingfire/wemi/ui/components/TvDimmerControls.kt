package org.suppressingfire.wemi.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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

/**
 * Interactive 10-foot Android TV dimmer strip (`tv_card_dim_mode_<id>`) and `-10%` / `+10%`
 * step buttons rendered inside reachable dimmer cards on `TvDashboardScreen`.
 */
@Composable
internal fun TvDeviceCardDimmerStrip(
    device: DeviceEntity,
    isSliderFocused: Boolean,
    isToggleHighlighted: Boolean,
    sliderFocusRequester: FocusRequester?,
    onSelectSliderFocus: () -> Unit,
    onToggleSliderFocus: () -> Unit,
    onStepBrightness: (Int) -> Unit,
    onNavigateUp: () -> Boolean,
    onNavigateDown: () -> Boolean
) {
    var hasSliderHardwareFocus by remember { mutableStateOf(false) }
    val currentOnStepBrightness by rememberUpdatedState(onStepBrightness)
    val currentOnNavigateUp by rememberUpdatedState(onNavigateUp)
    val currentOnNavigateDown by rememberUpdatedState(onNavigateDown)
    val isFocused = isSliderFocused || hasSliderHardwareFocus

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) TvDimmerGold.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = when {
                isFocused -> TvDimmerGold
                isToggleHighlighted -> TvCyanFocusBorder.copy(alpha = 0.45f)
                else -> Color.Transparent
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (sliderFocusRequester != null) mod.focusRequester(sliderFocusRequester) else mod }
            .onFocusChanged { state ->
                hasSliderHardwareFocus = state.isFocused
                if (state.isFocused) {
                    onSelectSliderFocus()
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft, Key.Minus -> {
                            currentOnStepBrightness(-10)
                            true
                        }
                        Key.DirectionRight, Key.Equals, Key.Plus -> {
                            currentOnStepBrightness(10)
                            true
                        }
                        Key.DirectionUp -> currentOnNavigateUp()
                        Key.DirectionDown -> currentOnNavigateDown()
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.Back,
                        Key.Escape,
                        Key.MediaPlayPause,
                        Key.Menu -> currentOnNavigateUp()
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            .focusProperties {
                canFocus = false
            }
            .clickable {
                onToggleSliderFocus()
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
                TvDimmerStepCircleButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Decrease brightness",
                    tint = if (isFocused) TvDimmerGold else Color.White,
                    testTag = "tv_card_dim_minus_${device.id}",
                    onClick = {
                        onSelectSliderFocus()
                        onStepBrightness(-10)
                    }
                )

                LinearProgressIndicator(
                    progress = { (device.brightness / 100f).coerceIn(0f, 1f) },
                    color = if (isFocused) TvDimmerGold else TvCyanFocusBorder,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Text(
                    text = "${device.brightness}%",
                    color = if (isFocused) TvDimmerGold else Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                TvDimmerStepCircleButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Increase brightness",
                    tint = if (isFocused) TvDimmerGold else Color.White,
                    testTag = "tv_card_dim_plus_${device.id}",
                    onClick = {
                        onSelectSliderFocus()
                        onStepBrightness(10)
                    }
                )
            }

            if (isFocused) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Left: -10%  •  Right: +10%   (Up: Toggle • Down: Next)",
                    color = TvDimmerGold,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (isToggleHighlighted) {
                Spacer(modifier = Modifier.height(3.dp))
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
                        text = "Press Down to focus dimmer slider",
                        color = TvTextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDimmerStepCircleButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
