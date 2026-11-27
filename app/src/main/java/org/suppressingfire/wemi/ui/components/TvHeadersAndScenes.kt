package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary

/**
 * Compact 42.dp single-row top bar for the 10-foot Android TV dashboard (`wemi TV`),
 * hosting the brand badge, active switch count, focused card status pill, Dimmer Adjust
 * mode toggle, 1-press TV scenes, and background LAN Rescan chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvTopHeaderBar(
    activeSwitchCount: Int,
    totalSwitchCount: Int,
    focusedBannerTarget: TvFocusedBannerTarget?,
    isRefreshing: Boolean,
    showHolidayScene: Boolean = false,
    topActionFocusRequesters: List<FocusRequester>,
    onSelectTopActionFocus: (Int) -> Unit,
    onNavigateDownFromTopBar: () -> Boolean,
    onNavigateTopActionHorizontal: (Int) -> Boolean,
    onPrimaryActionOnFocused: (DeviceEntity) -> Unit,
    onToggleRoomPower: (String) -> Unit,
    onToggleDimmingFocused: (Int?) -> Unit,
    onApplyTvScene: (String) -> Unit,
    onScanSsdpClick: () -> Unit,
    onOpenHouseholdSync: () -> Unit = {}
) {
    val focusedDeviceBanner = focusedBannerTarget as? TvFocusedBannerTarget.Device
    val focusedDimmer = focusedDeviceBanner?.device?.takeIf {
        it.parsedType == DeviceType.LIGHTBULB_DIMMER && it.isReachable
    }
    val isDimmingFocused = focusedDimmer != null && focusedDeviceBanner.isDimming

    val triggerBannerClick: () -> Unit = {
        when (focusedBannerTarget) {
            is TvFocusedBannerTarget.Device -> {
                if (isDimmingFocused) {
                    onToggleDimmingFocused(null)
                } else {
                    onPrimaryActionOnFocused(focusedBannerTarget.device)
                }
            }
            is TvFocusedBannerTarget.Room -> onToggleRoomPower(focusedBannerTarget.roomName)
            is TvFocusedBannerTarget.TopAction -> when (focusedBannerTarget.index) {
                0 -> onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.MOVIE_MODE.wireKey)
                1 -> if (showHolidayScene) {
                    onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.HOLIDAY_ON.wireKey)
                } else {
                    onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.MOVIE_MODE.wireKey)
                }
                2 -> onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.BEDTIME_OFF.wireKey)
                3 -> onScanSsdpClick()
                4 -> onOpenHouseholdSync()
                else -> Unit
            }
            null -> Unit
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tv_top_header_bar")
    ) {
        val availableWidth = maxWidth
        val isWideTvViewport = availableWidth >= 720.dp

        if (isWideTvViewport) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvBrandAndCountBadges(activeSwitchCount, totalSwitchCount)
                    if (focusedBannerTarget != null) {
                        TvFocusedStatusPill(
                            focusedTarget = focusedBannerTarget,
                            onClick = triggerBannerClick,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (focusedDimmer != null) {
                            TvDimmerModeChip(
                                focusedDevice = focusedDimmer,
                                isDimmingFocused = isDimmingFocused,
                                onToggleDimmingFocused = onToggleDimmingFocused
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TvTopRightActionChips(
                        isRefreshing = isRefreshing,
                        showHolidayScene = showHolidayScene,
                        topActionFocusRequesters = topActionFocusRequesters,
                        onSelectTopActionFocus = onSelectTopActionFocus,
                        onNavigateDownFromTopBar = onNavigateDownFromTopBar,
                        onNavigateTopActionHorizontal = onNavigateTopActionHorizontal,
                        onApplyTvScene = onApplyTvScene,
                        onScanSsdpClick = onScanSsdpClick,
                        onOpenHouseholdSync = onOpenHouseholdSync
                    )
                }
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TvBrandAndCountBadges(activeSwitchCount, totalSwitchCount)
                if (focusedBannerTarget != null) {
                    TvFocusedStatusPill(
                        focusedTarget = focusedBannerTarget,
                        onClick = triggerBannerClick,
                        modifier = Modifier.widthIn(max = availableWidth)
                    )
                    if (focusedDimmer != null) {
                        TvDimmerModeChip(
                            focusedDevice = focusedDimmer,
                            isDimmingFocused = isDimmingFocused,
                            onToggleDimmingFocused = onToggleDimmingFocused
                        )
                    }
                }
                TvTopRightActionChips(
                    isRefreshing = isRefreshing,
                    showHolidayScene = showHolidayScene,
                    topActionFocusRequesters = topActionFocusRequesters,
                    onSelectTopActionFocus = onSelectTopActionFocus,
                    onNavigateDownFromTopBar = onNavigateDownFromTopBar,
                    onNavigateTopActionHorizontal = onNavigateTopActionHorizontal,
                    onApplyTvScene = onApplyTvScene,
                    onScanSsdpClick = onScanSsdpClick,
                    onOpenHouseholdSync = onOpenHouseholdSync
                )
            }
        }
    }
}

@Composable
private fun TvBrandAndCountBadges(activeSwitchCount: Int, totalSwitchCount: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WemoGreenPrimary.copy(alpha = 0.25f),
        border = BorderStroke(1.5.dp, TvCyanFocusBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(Icons.Default.Tv, contentDescription = "Android TV Mode", tint = TvCyanFocusBorder, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "wemi TV",
                color = TvTextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false
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
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun TvTopRightActionChips(
    isRefreshing: Boolean,
    showHolidayScene: Boolean,
    topActionFocusRequesters: List<FocusRequester>,
    onSelectTopActionFocus: (Int) -> Unit,
    onNavigateDownFromTopBar: () -> Boolean,
    onNavigateTopActionHorizontal: (Int) -> Boolean,
    onApplyTvScene: (String) -> Unit,
    onScanSsdpClick: () -> Unit,
    onOpenHouseholdSync: () -> Unit
) {
    TvSceneChip(
        title = "Movie 20%",
        icon = Icons.Default.Movie,
        focusRequester = topActionFocusRequesters.getOrNull(0),
        onFocusGained = { onSelectTopActionFocus(0) },
        onNavigateLeft = { true },
        onNavigateRight = { onNavigateTopActionHorizontal(if (showHolidayScene) 1 else 2) },
        onNavigateDown = onNavigateDownFromTopBar,
        onClick = { onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.MOVIE_MODE.wireKey) },
        testTag = "tv_scene_movie"
    )
    if (showHolidayScene) {
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
    }
    TvSceneChip(
        title = "All OFF",
        icon = Icons.Default.Bedtime,
        focusRequester = topActionFocusRequesters.getOrNull(2),
        onFocusGained = { onSelectTopActionFocus(2) },
        onNavigateLeft = { onNavigateTopActionHorizontal(if (showHolidayScene) 1 else 0) },
        onNavigateRight = { onNavigateTopActionHorizontal(3) },
        onNavigateDown = onNavigateDownFromTopBar,
        onClick = { onApplyTvScene(org.suppressingfire.wemi.ui.TvScene.BEDTIME_OFF.wireKey) },
        testTag = "tv_scene_bedtime"
    )

    TvSceneChip(
        title = if (isRefreshing) "Scanning..." else "Rescan",
        icon = Icons.Default.Radar,
        iconContentDescription = "Rescan LAN Switches",
        focusRequester = topActionFocusRequesters.getOrNull(3),
        onFocusGained = { onSelectTopActionFocus(3) },
        onNavigateLeft = { onNavigateTopActionHorizontal(2) },
        onNavigateRight = { onNavigateTopActionHorizontal(4) },
        onNavigateDown = onNavigateDownFromTopBar,
        onClick = onScanSsdpClick,
        testTag = "tv_scan_button",
        focusedContainerColor = WemoGreenPrimary,
        unfocusedContainerColor = WemoGreenDark,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = TvCyanFocusBorder.copy(alpha = 0.65f),
        iconTint = Color.White,
        fontWeight = FontWeight.Bold
    )

    TvSceneChip(
        title = "Sync",
        icon = Icons.Default.Sync,
        iconContentDescription = "Pair and Sync from Phone",
        focusRequester = topActionFocusRequesters.getOrNull(4),
        onFocusGained = { onSelectTopActionFocus(4) },
        onNavigateLeft = { onNavigateTopActionHorizontal(3) },
        onNavigateRight = { true },
        onNavigateDown = onNavigateDownFromTopBar,
        onClick = onOpenHouseholdSync,
        testTag = "tv_sync_button"
    )
}

@Composable
internal fun TvSceneChip(
    title: String,
    icon: ImageVector,
    iconContentDescription: String? = null,
    focusRequester: FocusRequester? = null,
    onFocusGained: () -> Unit = {},
    onNavigateLeft: () -> Boolean = { false },
    onNavigateRight: () -> Boolean = { false },
    onNavigateDown: () -> Boolean = { false },
    onClick: () -> Unit,
    testTag: String,
    focusedContainerColor: Color = Color(0xFF133B66),
    unfocusedContainerColor: Color = Color(0xFF0F2540),
    focusedBorderColor: Color = TvCyanFocusBorder,
    unfocusedBorderColor: Color = TvCyanFocusBorder.copy(alpha = 0.35f),
    iconTint: Color = TvCyanFocusBorder,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    var isFocused by remember { mutableStateOf(false) }
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val currentOnNavigateLeft by androidx.compose.runtime.rememberUpdatedState(onNavigateLeft)
    val currentOnNavigateRight by androidx.compose.runtime.rememberUpdatedState(onNavigateRight)
    val currentOnNavigateDown by androidx.compose.runtime.rememberUpdatedState(onNavigateDown)
    Surface(
        color = if (isFocused) focusedContainerColor else unfocusedContainerColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) focusedBorderColor else unfocusedBorderColor
        ),
        modifier = Modifier
            .let { mod -> if (focusRequester != null) mod.focusRequester(focusRequester) else mod }
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocusGained()
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { currentOnClick(); true }
                        Key.DirectionLeft -> currentOnNavigateLeft()
                        Key.DirectionRight -> currentOnNavigateRight()
                        Key.DirectionDown -> currentOnNavigateDown()
                        Key.DirectionUp -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            .focusProperties { canFocus = false }
            .clickable {
                onFocusGained()
                focusRequester?.let { runCatching { it.requestFocus() } }
                onClick()
            }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = iconContentDescription, tint = iconTint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = title,
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = fontWeight,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
