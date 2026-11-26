package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.theme.WemoGreenTop

internal sealed interface TvFocusTarget {
    data class TopAction(val index: Int) : TvFocusTarget
    data class RoomHeader(val roomName: String) : TvFocusTarget
    data class DeviceToggle(val deviceId: Int) : TvFocusTarget
    data class DeviceSlider(val deviceId: Int) : TvFocusTarget
}

/**
 * Describes what the Android TV top-bar status pill (`tv_remote_dpad_ok`) displays for the
 * currently focused D-Pad target so navigating onto a Room Master bar or top-bar action never
 * leaves stale instructions for a previously selected switch on screen.
 */
internal sealed interface TvFocusedBannerTarget {
    data class Device(
        val device: DeviceEntity,
        val isDimming: Boolean
    ) : TvFocusedBannerTarget

    data class Room(
        val roomName: String,
        val hasReachableDimmers: Boolean
    ) : TvFocusedBannerTarget

    data class TopAction(
        val index: Int,
        val label: String,
        val actionHint: String
    ) : TvFocusedBannerTarget
}

internal fun resolveTvFocusedBannerTarget(
    activeFocusTarget: TvFocusTarget?,
    groupedByRoom: List<Pair<String, List<DeviceEntity>>>,
    orderedDevices: List<DeviceEntity>,
    lastFocusedDeviceId: Int?,
    isRefreshing: Boolean
): TvFocusedBannerTarget? {
    return when (activeFocusTarget) {
        is TvFocusTarget.RoomHeader -> {
            val roomEntry = groupedByRoom.firstOrNull {
                it.first.equals(activeFocusTarget.roomName, ignoreCase = true)
            }
            if (roomEntry != null) {
                val hasDimmers = roomEntry.second.any {
                    it.parsedType == DeviceType.LIGHTBULB_DIMMER && it.isReachable
                }
                TvFocusedBannerTarget.Room(
                    roomName = roomEntry.first,
                    hasReachableDimmers = hasDimmers
                )
            } else {
                orderedDevices.firstOrNull()?.let { TvFocusedBannerTarget.Device(it, isDimming = false) }
            }
        }
        is TvFocusTarget.DeviceSlider -> {
            val device = orderedDevices.firstOrNull { it.id == activeFocusTarget.deviceId }
                ?: orderedDevices.firstOrNull { it.id == lastFocusedDeviceId }
                ?: orderedDevices.firstOrNull()
            device?.let {
                val isReachableDimmer = it.parsedType == DeviceType.LIGHTBULB_DIMMER && it.isReachable
                TvFocusedBannerTarget.Device(it, isDimming = isReachableDimmer)
            }
        }
        is TvFocusTarget.DeviceToggle -> {
            val device = orderedDevices.firstOrNull { it.id == activeFocusTarget.deviceId }
                ?: orderedDevices.firstOrNull { it.id == lastFocusedDeviceId }
                ?: orderedDevices.firstOrNull()
            device?.let { TvFocusedBannerTarget.Device(it, isDimming = false) }
        }
        is TvFocusTarget.TopAction -> {
            when (activeFocusTarget.index) {
                0 -> TvFocusedBannerTarget.TopAction(0, "Movie 20%", "• OK Run Scene")
                1 -> TvFocusedBannerTarget.TopAction(1, "Holiday ON", "• OK Run Scene")
                2 -> TvFocusedBannerTarget.TopAction(2, "All OFF", "• OK Turn All Off")
                3 -> TvFocusedBannerTarget.TopAction(
                    index = 3,
                    label = if (isRefreshing) "Scanning..." else "Rescan",
                    actionHint = "• OK Scan LAN"
                )
                4 -> TvFocusedBannerTarget.TopAction(4, "Sync", "• OK Pair Phone")
                else -> null
            }
        }
        null -> {
            val fallback = orderedDevices.firstOrNull { it.id == lastFocusedDeviceId }
                ?: orderedDevices.firstOrNull()
            fallback?.let { TvFocusedBannerTarget.Device(it, isDimming = false) }
        }
    }
}

@Composable
internal fun TvFocusedStatusPill(
    focusedTarget: TvFocusedBannerTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDimmingFocused = (focusedTarget as? TvFocusedBannerTarget.Device)?.isDimming == true
    val (title, actionHint) = when (focusedTarget) {
        is TvFocusedBannerTarget.Device -> {
            val dev = focusedTarget.device
            val hint = when {
                focusedTarget.isDimming -> "• ◄/► ${dev.brightness}%"
                !dev.isReachable -> "• OK Reconnect"
                dev.parsedType == DeviceType.MOTION_SENSOR -> "• OK Refresh"
                else -> "• OK Toggle"
            }
            dev.name to hint
        }
        is TvFocusedBannerTarget.Room -> {
            val hint = if (focusedTarget.hasReachableDimmers) {
                "• OK Toggle Room • ◄/► Dim"
            } else {
                "• OK Toggle Room"
            }
            focusedTarget.roomName to hint
        }
        is TvFocusedBannerTarget.TopAction -> focusedTarget.label to focusedTarget.actionHint
    }

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (isDimmingFocused) TvDimmerGold else WemoGreenTop.copy(alpha = 0.20f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDimmingFocused) TvDimmerGold else TvCyanFocusBorder.copy(alpha = 0.65f)
        ),
        modifier = modifier
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .testTag("tv_remote_dpad_ok")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = if (isDimmingFocused) TvDeepBackground else TvCyanFocusBorder,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                color = if (isDimmingFocused) TvDeepBackground else Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = actionHint,
                color = if (isDimmingFocused) TvDeepBackground else Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
internal fun TvDimmerModeChip(
    focusedDevice: DeviceEntity,
    isDimmingFocused: Boolean,
    onToggleDimmingFocused: (Int?) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (isDimmingFocused) TvDimmerGold else Color(0xFF132D4E),
        border = BorderStroke(1.dp, TvDimmerGold.copy(alpha = 0.8f)),
        modifier = Modifier
            .focusProperties { canFocus = false }
            .clickable { onToggleDimmingFocused(if (isDimmingFocused) null else focusedDevice.id) }
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
                fontSize = 11.5.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
