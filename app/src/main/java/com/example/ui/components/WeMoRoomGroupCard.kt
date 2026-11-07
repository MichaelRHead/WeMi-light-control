package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.ui.theme.WemoDividerGray
import com.example.ui.theme.WemoGreenBottom
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft
import com.example.ui.theme.WemoRowExpandedSurface
import com.example.ui.theme.WemoRowSurface
import com.example.ui.theme.WemoTextPrimary
import com.example.ui.theme.WemoTextSecondary
import com.example.ui.theme.WorkSansFontFamily
import kotlin.math.roundToInt

/**
 * Horizontal filter & grouping bar at the top of the Devices tab allowing users to:
 * 1) Toggle between "Rooms View" (grouped by room with master room toggles) and "All Switches"
 * 2) Filter by a specific room ("All Rooms", "Living Room", "Bedroom", "Kitchen", etc.)
 */
@Composable
fun WeMoRoomFilterBar(
    rooms: List<String>,
    isGroupedByRoom: Boolean,
    selectedRoomFilter: String?,
    onToggleGroupedByRoom: () -> Unit,
    onSelectRoomFilter: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .testTag("room_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = isGroupedByRoom,
            onClick = onToggleGroupedByRoom,
            leadingIcon = {
                Icon(
                    imageVector = if (isGroupedByRoom) Icons.Default.GridView else Icons.Default.ViewAgenda,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = if (isGroupedByRoom) "Grouped by Room" else "Flat List",
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = WemoGreenDark,
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White
            ),
            modifier = Modifier.testTag("toggle_group_by_room_chip")
        )

        FilterChip(
            selected = selectedRoomFilter == null,
            onClick = { onSelectRoomFilter(null) },
            label = {
                Text(
                    text = "All Rooms (${rooms.size})",
                    fontFamily = WorkSansFontFamily,
                    fontSize = 12.sp
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = WemoGreenSoft,
                selectedLabelColor = WemoGreenDark
            ),
            modifier = Modifier.testTag("room_filter_All")
        )

        rooms.forEach { room ->
            FilterChip(
                selected = selectedRoomFilter == room,
                onClick = {
                    onSelectRoomFilter(if (selectedRoomFilter == room) null else room)
                },
                leadingIcon = {
                    Icon(
                        imageVector = iconForRoom(room),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                label = {
                    Text(
                        text = room,
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WemoGreenSoft,
                    selectedLabelColor = WemoGreenDark,
                    selectedLeadingIconColor = WemoGreenDark
                ),
                modifier = Modifier.testTag("room_filter_$room")
            )
        }
    }
}

/**
 * Interactive Room Group Header Card allowing users to:
 * - Toggle ALL switches/lights in the room ON or OFF with one tap via the master Room Power Ring
 * - Adjust master room brightness across all dimmable bulbs in the room
 * - Collapse or expand the room's individual device cards
 */
@Composable
fun WeMoRoomGroupHeaderCard(
    roomName: String,
    roomDevices: List<WeMoDeviceEntity>,
    isCollapsed: Boolean,
    isRoomToggling: Boolean,
    onToggleRoomPower: () -> Unit,
    onToggleCollapse: () -> Unit,
    onRoomBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val controllableSwitches = roomDevices.filter {
        it.isReachable && it.parsedType != WeMoDeviceType.MOTION_SENSOR
    }
    val dimmableBulbs = roomDevices.filter {
        it.isReachable && it.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER
    }
    val activeCount = controllableSwitches.count { it.isOn }
    val totalControllable = controllableSwitches.size
    val isAnySwitchOn = activeCount > 0

    val avgBrightness = if (dimmableBulbs.isNotEmpty()) {
        dimmableBulbs.map { it.brightness }.average().roundToInt()
    } else {
        100
    }

    var showRoomDimmer by remember { mutableStateOf(false) }
    var roomSliderVal by remember(avgBrightness) {
        mutableFloatStateOf(avgBrightness.toFloat())
    }

    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isAnySwitchOn) WemoGreenPrimary.copy(alpha = 0.55f) else WemoDividerGray
        ),
        shadowElevation = if (isAnySwitchOn) 2.dp else 0.5.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("room_header_$roomName")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = if (isAnySwitchOn) {
                                listOf(
                                    WemoGreenSoft.copy(alpha = 0.75f),
                                    Color.White
                                )
                            } else {
                                listOf(
                                    Color(0xFFF8FAFC),
                                    Color.White
                                )
                            }
                        )
                    )
                    .clickable(onClick = onToggleCollapse)
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Room Icon Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAnySwitchOn) WemoGreenPrimary else Color(0xFFCBD5E1),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForRoom(roomName),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Room Name + Active Lights Status Badge
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = roomName,
                            color = WemoTextPrimary,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            color = if (isAnySwitchOn) WemoGreenDark else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (totalControllable > 0) {
                                    "$activeCount/$totalControllable ON"
                                } else {
                                    "${roomDevices.size} Device(s)"
                                },
                                color = if (isAnySwitchOn) Color.White else WemoTextSecondary,
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val onlineDevices = roomDevices.filter { it.isReachable && it.latencyMs > 0 }
                    val avgLatency = if (onlineDevices.isNotEmpty()) {
                        onlineDevices.map { it.latencyMs }.average().roundToInt()
                    } else {
                        0
                    }
                    val roomSubtitle = buildString {
                        append("${onlineDevices.size}/${roomDevices.size} Active")
                        if (avgLatency > 0) {
                            append(" • Avg ${avgLatency}ms")
                        }
                        if (dimmableBulbs.isNotEmpty()) {
                            append(" • ${avgBrightness}% dim")
                        } else if (totalControllable > 0) {
                            append(" • Tap ring to toggle all")
                        }
                    }
                    Text(
                        text = roomSubtitle,
                        color = WemoTextSecondary,
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Optional Room Dimmer button if the room has dimmable lightbulbs
                if (dimmableBulbs.isNotEmpty()) {
                    IconButton(
                        onClick = { showRoomDimmer = !showRoomDimmer },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("room_dimmer_button_$roomName")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Adjust room brightness for $roomName",
                            tint = if (showRoomDimmer) WemoGreenPrimary else WemoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("room_collapse_button_$roomName")
                ) {
                    Icon(
                        imageVector = if (isCollapsed) {
                            Icons.Default.KeyboardArrowDown
                        } else {
                            Icons.Default.KeyboardArrowUp
                        },
                        contentDescription = if (isCollapsed) "Expand $roomName" else "Collapse $roomName",
                        tint = WemoTextSecondary
                    )
                }

                // Master Room Power Ring Toggle (Toggles all lights in the room at once!)
                if (totalControllable > 0) {
                    WeMoPowerRingButton(
                        isOn = isAnySwitchOn,
                        isToggling = isRoomToggling,
                        deviceName = "$roomName Group",
                        onClick = onToggleRoomPower,
                        modifier = Modifier.testTag("room_power_toggle_$roomName")
                    )
                }
            }

            // Expandable Room Master Dimmer Drawer
            AnimatedVisibility(
                visible = showRoomDimmer && dimmableBulbs.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WemoRowExpandedSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = WemoGreenDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$roomName Master Brightness (${dimmableBulbs.size} bulb${if (dimmableBulbs.size > 1) "s" else ""})",
                                color = WemoGreenBottom,
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "${roomSliderVal.roundToInt()}%",
                            color = WemoGreenDark,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = roomSliderVal,
                        onValueChange = { roomSliderVal = it },
                        onValueChangeFinished = {
                            onRoomBrightnessChange(roomSliderVal.roundToInt())
                        },
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = WemoGreenPrimary,
                            activeTrackColor = WemoGreenPrimary,
                            inactiveTrackColor = WemoDividerGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_dimmer_slider_$roomName")
                    )
                }
            }
        }
    }
}

fun iconForRoom(roomName: String): ImageVector {
    val lower = roomName.lowercase()
    return when {
        "living" in lower -> Icons.Default.Chair
        "bed" in lower -> Icons.Default.Bed
        "kitchen" in lower -> Icons.Default.Kitchen
        "ensuite" in lower || "bath" in lower -> Icons.Default.Bathtub
        "outdoor" in lower || "patio" in lower || "porch" in lower -> Icons.Default.Deck
        else -> Icons.Default.MeetingRoom
    }
}
