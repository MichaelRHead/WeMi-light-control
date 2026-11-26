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
import java.util.Locale
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.normalizeRoomName
import org.suppressingfire.wemi.ui.theme.WemoGreenTop

internal sealed interface TvFocusTarget {
    data class TopAction(val index: Int) : TvFocusTarget
    data class RoomHeader(val canonicalRoomKey: String) : TvFocusTarget {
        companion object {
            fun forRoom(roomName: String): RoomHeader =
                RoomHeader(normalizeRoomName(roomName).lowercase(Locale.US))
        }
    }
    data class DeviceToggle(val deviceId: Int) : TvFocusTarget
    data class DeviceSlider(val deviceId: Int) : TvFocusTarget
}

/**
 * Reconciled Android TV D-Pad focus state after discovery, room grouping, filter, or
 * reachability changes so focus never strands on a detached room header, slider, or chip.
 */
internal data class TvFocusReconciliation(
    val nextActiveTarget: TvFocusTarget,
    val nextLastFocusedDeviceId: Int?,
    val nextDidRequestInitialGridFocus: Boolean,
    val shouldRequestFocus: Boolean,
    val awaitFrameBeforeFocus: Boolean = false
)

internal fun reconcileTvFocusState(
    activeFocusTarget: TvFocusTarget?,
    lastFocusedDeviceId: Int?,
    didRequestInitialGridFocus: Boolean,
    groupedByRoom: List<Pair<String, List<DeviceEntity>>>,
    orderedDevices: List<DeviceEntity>,
    hasHolidayDevices: Boolean,
    holidayChipVanishedWhileFocused: Boolean,
    focusedRoomVanishedWhileFocused: Boolean = false,
    focusedDeviceVanishedWhileFocused: Boolean = false
): TvFocusReconciliation {
    if (orderedDevices.isEmpty()) {
        return TvFocusReconciliation(
            nextActiveTarget = TvFocusTarget.TopAction(3),
            nextLastFocusedDeviceId = null,
            nextDidRequestInitialGridFocus = false,
            shouldRequestFocus = true
        )
    }

    val firstDeviceId = orderedDevices.first().id
    val validLastDeviceId = lastFocusedDeviceId?.takeIf { id -> orderedDevices.any { it.id == id } }
    val fallbackDeviceId = validLastDeviceId ?: firstDeviceId

    if (holidayChipVanishedWhileFocused ||
        (!hasHolidayDevices && activeFocusTarget == TvFocusTarget.TopAction(1))
    ) {
        return TvFocusReconciliation(
            nextActiveTarget = TvFocusTarget.TopAction(0),
            nextLastFocusedDeviceId = fallbackDeviceId,
            nextDidRequestInitialGridFocus = didRequestInitialGridFocus,
            shouldRequestFocus = true,
            awaitFrameBeforeFocus = true
        )
    }

    if (focusedRoomVanishedWhileFocused || focusedDeviceVanishedWhileFocused) {
        return TvFocusReconciliation(
            nextActiveTarget = TvFocusTarget.DeviceToggle(fallbackDeviceId),
            nextLastFocusedDeviceId = fallbackDeviceId,
            nextDidRequestInitialGridFocus = true,
            shouldRequestFocus = true,
            awaitFrameBeforeFocus = true
        )
    }

    val currentSliderId = (activeFocusTarget as? TvFocusTarget.DeviceSlider)?.deviceId
    if (currentSliderId != null) {
        val sliderDevice = orderedDevices.firstOrNull { it.id == currentSliderId }
        if (sliderDevice == null) {
            return TvFocusReconciliation(
                nextActiveTarget = TvFocusTarget.DeviceToggle(firstDeviceId),
                nextLastFocusedDeviceId = firstDeviceId,
                nextDidRequestInitialGridFocus = true,
                shouldRequestFocus = true,
                awaitFrameBeforeFocus = true
            )
        } else if (!sliderDevice.isReachable || sliderDevice.parsedType != DeviceType.LIGHTBULB_DIMMER) {
            return TvFocusReconciliation(
                nextActiveTarget = TvFocusTarget.DeviceToggle(sliderDevice.id),
                nextLastFocusedDeviceId = sliderDevice.id,
                nextDidRequestInitialGridFocus = true,
                shouldRequestFocus = true
            )
        }
    }

    if (activeFocusTarget is TvFocusTarget.RoomHeader) {
        val roomStillExists = groupedByRoom.any {
            TvFocusTarget.RoomHeader.forRoom(it.first) == activeFocusTarget
        }
        return if (roomStillExists) {
            TvFocusReconciliation(
                nextActiveTarget = activeFocusTarget,
                nextLastFocusedDeviceId = fallbackDeviceId,
                nextDidRequestInitialGridFocus = true,
                shouldRequestFocus = false
            )
        } else {
            TvFocusReconciliation(
                nextActiveTarget = TvFocusTarget.DeviceToggle(fallbackDeviceId),
                nextLastFocusedDeviceId = fallbackDeviceId,
                nextDidRequestInitialGridFocus = true,
                shouldRequestFocus = true,
                awaitFrameBeforeFocus = true
            )
        }
    }

    if (activeFocusTarget is TvFocusTarget.DeviceToggle &&
        orderedDevices.none { it.id == activeFocusTarget.deviceId }
    ) {
        return TvFocusReconciliation(
            nextActiveTarget = TvFocusTarget.DeviceToggle(fallbackDeviceId),
            nextLastFocusedDeviceId = fallbackDeviceId,
            nextDidRequestInitialGridFocus = true,
            shouldRequestFocus = true,
            awaitFrameBeforeFocus = true
        )
    }

    if (!didRequestInitialGridFocus || validLastDeviceId == null) {
        val keepTopBarFocus = didRequestInitialGridFocus &&
            activeFocusTarget is TvFocusTarget.TopAction &&
            (hasHolidayDevices || activeFocusTarget.index != 1)
        return if (keepTopBarFocus) {
            TvFocusReconciliation(
                nextActiveTarget = activeFocusTarget,
                nextLastFocusedDeviceId = firstDeviceId,
                nextDidRequestInitialGridFocus = true,
                shouldRequestFocus = false
            )
        } else {
            TvFocusReconciliation(
                nextActiveTarget = TvFocusTarget.DeviceToggle(firstDeviceId),
                nextLastFocusedDeviceId = firstDeviceId,
                nextDidRequestInitialGridFocus = true,
                shouldRequestFocus = true
            )
        }
    }

    return TvFocusReconciliation(
        nextActiveTarget = activeFocusTarget ?: TvFocusTarget.DeviceToggle(firstDeviceId),
        nextLastFocusedDeviceId = validLastDeviceId,
        nextDidRequestInitialGridFocus = didRequestInitialGridFocus,
        shouldRequestFocus = false
    )
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
                TvFocusTarget.RoomHeader.forRoom(it.first) == activeFocusTarget
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
                val fallback = orderedDevices.firstOrNull { it.id == lastFocusedDeviceId }
                    ?: orderedDevices.firstOrNull()
                fallback?.let { TvFocusedBannerTarget.Device(it, isDimming = false) }
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
