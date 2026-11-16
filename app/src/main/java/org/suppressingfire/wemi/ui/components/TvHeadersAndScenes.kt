package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
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
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenTop

/**
 * Compact 42.dp single-row top bar for the 10-foot Android TV dashboard (`wemi TV`),
 * hosting the brand badge, active switch count, focused card status pill, Dimmer Adjust
 * mode toggle, 1-press TV scenes, and background LAN Rescan chip.
 */
@Composable
internal fun TvTopHeaderBar(
    activeSwitchCount: Int,
    totalSwitchCount: Int,
    focusedDevice: DeviceEntity?,
    dimmingDeviceId: Int?,
    isRefreshing: Boolean,
    topActionFocusRequesters: List<FocusRequester>,
    onSelectTopActionFocus: (Int) -> Unit,
    onNavigateDownFromTopBar: () -> Boolean,
    onNavigateTopActionHorizontal: (Int) -> Boolean,
    onPrimaryActionOnFocused: (DeviceEntity) -> Unit,
    onToggleDimmingFocused: (Int?) -> Unit,
    onApplyTvScene: (String) -> Unit,
    onScanSsdpClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WemoGreenPrimary.copy(alpha = 0.25f),
                border = BorderStroke(1.5.dp, TvCyanFocusBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Android TV Mode",
                        tint = TvCyanFocusBorder,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "wemi TV",
                        color = TvTextPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F2540),
                border = BorderStroke(1.dp, Color(0xFF1E3A5F))
            ) {
                Text(
                    text = "$activeSwitchCount/$totalSwitchCount ON",
                    color = TvCyanFocusBorder,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }

            // Focused Card Status & Quick Toggle Pill (non-focusable so D-Pad stays in the grid/scenes)
            if (focusedDevice != null) {
                val isFocusedDimmer =
                    focusedDevice.parsedType == DeviceType.LIGHTBULB_DIMMER && focusedDevice.isReachable
                val isDimmingFocused = dimmingDeviceId == focusedDevice.id && isFocusedDimmer

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (isDimmingFocused) TvDimmerGold else WemoGreenTop.copy(alpha = 0.20f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDimmingFocused) TvDimmerGold else TvCyanFocusBorder.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .focusProperties { canFocus = false }
                        .clickable {
                            if (isDimmingFocused) {
                                onToggleDimmingFocused(null)
                            } else {
                                onPrimaryActionOnFocused(focusedDevice)
                            }
                        }
                        .testTag("tv_remote_dpad_ok")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (isDimmingFocused) TvDeepBackground else TvCyanFocusBorder,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isDimmingFocused) {
                                "${focusedDevice.name}: ${focusedDevice.brightness}% (Left/Right Adjust • Down Next)"
                            } else {
                                "${focusedDevice.name} • OK Toggle"
                            },
                            color = if (isDimmingFocused) TvDeepBackground else Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isFocusedDimmer) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isDimmingFocused) TvDimmerGold else Color(0xFF132D4E),
                        border = BorderStroke(
                            width = 1.dp,
                            color = TvDimmerGold.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .clickable {
                                onToggleDimmingFocused(if (isDimmingFocused) null else focusedDevice.id)
                            }
                            .testTag("tv_dimmer_mode_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Toggle Dimmer Adjust Mode",
                                tint = if (isDimmingFocused) TvDeepBackground else TvDimmerGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isDimmingFocused) "Done Dimming" else "Dimmer ${focusedDevice.brightness}%",
                                color = if (isDimmingFocused) TvDeepBackground else TvDimmerGold,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Right: Compact Scene Shortcuts + Background LAN Rescan
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvSceneChip(
                title = "Movie 20%",
                icon = Icons.Default.Movie,
                focusRequester = topActionFocusRequesters.getOrNull(0),
                onFocusGained = { onSelectTopActionFocus(0) },
                onNavigateLeft = { true },
                onNavigateRight = { onNavigateTopActionHorizontal(1) },
                onNavigateDown = onNavigateDownFromTopBar,
                onClick = { onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.MOVIE_MODE.wireKey) },
                testTag = "tv_scene_movie"
            )
            TvSceneChip(
                title = "Holiday ON",
                icon = Icons.Default.Park,
                focusRequester = topActionFocusRequesters.getOrNull(1),
                onFocusGained = { onSelectTopActionFocus(1) },
                onNavigateLeft = { onNavigateTopActionHorizontal(0) },
                onNavigateRight = { onNavigateTopActionHorizontal(2) },
                onNavigateDown = onNavigateDownFromTopBar,
                onClick = { onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.HOLIDAY_ON.wireKey) },
                testTag = "tv_scene_holiday"
            )
            TvSceneChip(
                title = "All OFF",
                icon = Icons.Default.Bedtime,
                focusRequester = topActionFocusRequesters.getOrNull(2),
                onFocusGained = { onSelectTopActionFocus(2) },
                onNavigateLeft = { onNavigateTopActionHorizontal(1) },
                onNavigateRight = { onNavigateTopActionHorizontal(3) },
                onNavigateDown = onNavigateDownFromTopBar,
                onClick = { onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.BEDTIME_OFF.wireKey) },
                testTag = "tv_scene_bedtime"
            )

            var isScanFocused by remember { mutableStateOf(false) }
            val scanRequester = topActionFocusRequesters.getOrNull(3)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isScanFocused) WemoGreenPrimary else WemoGreenDark,
                border = BorderStroke(
                    width = if (isScanFocused) 2.5.dp else 1.dp,
                    color = if (isScanFocused) Color.White else TvCyanFocusBorder.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .let { mod -> if (scanRequester != null) mod.focusRequester(scanRequester) else mod }
                    .onFocusChanged { state ->
                        isScanFocused = state.isFocused
                        if (state.isFocused) {
                            onSelectTopActionFocus(3)
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    onScanSsdpClick()
                                    true
                                }
                                Key.DirectionLeft -> onNavigateTopActionHorizontal(2)
                                Key.DirectionRight -> true
                                Key.DirectionDown -> onNavigateDownFromTopBar()
                                Key.DirectionUp -> true
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .focusProperties { canFocus = false }
                    .clickable { onScanSsdpClick() }
                    .testTag("tv_scan_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Rescan LAN Switches",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRefreshing) "Scanning..." else "Rescan",
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}


@Composable
internal fun TvSceneChip(
    title: String,
    icon: ImageVector,
    focusRequester: FocusRequester? = null,
    onFocusGained: () -> Unit = {},
    onNavigateLeft: () -> Boolean = { false },
    onNavigateRight: () -> Boolean = { false },
    onNavigateDown: () -> Boolean = { false },
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
            .let { mod -> if (focusRequester != null) mod.focusRequester(focusRequester) else mod }
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocusGained()
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        Key.DirectionLeft -> onNavigateLeft()
                        Key.DirectionRight -> onNavigateRight()
                        Key.DirectionDown -> onNavigateDown()
                        Key.DirectionUp -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            .focusProperties { canFocus = false }
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
