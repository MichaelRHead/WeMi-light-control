package org.suppressingfire.wemi.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenTop

internal val TvDeepBackground = Color(0xFF060E1A)
internal val TvSurfaceCard = Color(0xFF0C1829)
internal val TvSurfaceCardActive = Color(0xFF0F2744)
internal val TvCyanFocusBorder = Color(0xFF22D3EE)
internal val TvTextPrimary = Color(0xFFF8FAFC)
internal val TvTextSecondary = Color(0xFF94A3B8)

/**
 * 10-foot Android TV & Large-Screen Landscape Dashboard (`wemi TV`).
 *
 * Designed around three core remote-control ergonomics principles:
 * 1. Maximum Screen Real Estate: A single compact 42.dp top bar leaves ~90% of vertical screen height
 *    for the 3-column Room-Grouped Switch Grid (`GridCells.Fixed(3)`).
 * 2. Unmistakable 10-Foot Cursor Focus: Automatic initial hardware focus (`FocusRequester`) on the first
 *    switch card, paired with a high-contrast 3.5.dp Electric Cyan border (`#22D3EE`), bright illuminated
 *    card surface (`#133B66`), subtle scale (`1.022f`), and explicit `"FOCUSED • OK"` badge.
 * 3. Non-Interfering Dimmer Control: D-Pad Up/Down/Left/Right always navigates freely between the 3 columns
 *    of switch cards, and pressing OK on any card toggles power ON/OFF. Dimmer brightness is adjusted via
 *    an explicit Dimmer Adjust Mode (activated with Long-Press OK, Play/Pause, Menu, or clicking the dimmer
 *    strip) and exited immediately with OK or Back.
 */
@Composable
fun TvDashboardScreen(
    devices: List<DeviceEntity>,
    rooms: List<String>,
    selectedRoomFilter: String?,
    isRefreshing: Boolean,
    togglingDeviceIds: Set<Int>,
    togglingRooms: Set<String>,
    latestSoapLog: SoapLogEntry?,
    onSelectRoomFilter: (String?) -> Unit,
    onToggleDevicePower: (DeviceEntity) -> Unit,
    onStepDeviceBrightness: (DeviceEntity, Int) -> Unit,
    onTriggerMotion: (DeviceEntity) -> Unit,
    onReconnectDevice: (DeviceEntity) -> Unit,
    onToggleRoomPower: (String) -> Unit,
    onApplyTvScene: (String) -> Unit,
    onScanSsdpClick: () -> Unit
) {
    // Group switches by room (with "Unassigned" placed last) while preserving user sortOrder within each room
    val groupedByRoom: List<Pair<String, List<DeviceEntity>>> = remember(devices, selectedRoomFilter) {
        val base = if (selectedRoomFilter == null) {
            devices
        } else {
            devices.filter { it.roomName == selectedRoomFilter }
        }
        base.groupBy { it.roomName.ifBlank { "Unassigned" } }
            .entries
            .sortedWith(
                compareBy<Map.Entry<String, List<DeviceEntity>>> {
                    if (it.key.equals("Unassigned", ignoreCase = true)) 1 else 0
                }.thenBy { entry ->
                    entry.value.minOfOrNull { it.sortOrder } ?: Int.MAX_VALUE
                }
            )
            .map { it.key to it.value }
    }

    val orderedDevices: List<DeviceEntity> = remember(groupedByRoom) {
        groupedByRoom.flatMap { it.second }
    }

    var focusedDeviceId by remember(orderedDevices.size) {
        mutableStateOf(orderedDevices.firstOrNull()?.id)
    }
    var dimmingDeviceId by remember { mutableStateOf<Int?>(null) }

    val firstCardFocusRequester = remember { FocusRequester() }
    val firstDeviceId = orderedDevices.firstOrNull()?.id

    // Automatically request initial hardware D-Pad focus on the first switch card when TV dashboard loads
    LaunchedEffect(firstDeviceId) {
        if (firstDeviceId != null) {
            if (focusedDeviceId == null || orderedDevices.none { it.id == focusedDeviceId }) {
                focusedDeviceId = firstDeviceId
            }
            runCatching { firstCardFocusRequester.requestFocus() }
        }
    }

    val focusedDevice = orderedDevices.firstOrNull { it.id == focusedDeviceId }
        ?: orderedDevices.firstOrNull()

    // Pressing Back on the TV remote while in Dimmer Adjust Mode cleanly exits Dimmer Adjust Mode
    BackHandler(enabled = dimmingDeviceId != null) {
        dimmingDeviceId = null
    }

    val triggerPrimaryAction: (DeviceEntity) -> Unit = { target ->
        when {
            !target.isReachable -> onReconnectDevice(target)
            target.parsedType == DeviceType.MOTION_SENSOR -> onTriggerMotion(target)
            else -> onToggleDevicePower(target)
        }
    }

    val controllableDevices = devices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
    val activeSwitchCount = controllableDevices.count { it.isOn && it.isReachable }
    val totalSwitchCount = controllableDevices.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A192F), TvDeepBackground)
                )
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("tv_dashboard_screen")
    ) {
        // Single Compact Top Header Bar: Brand + Active Count + Focused Card Pill + Scene & Rescan Chips
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
                                    dimmingDeviceId = null
                                } else {
                                    triggerPrimaryAction(focusedDevice)
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
                                    "${focusedDevice.name}: ${focusedDevice.brightness}% (Left/Right Adjust • OK Done)"
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
                                    dimmingDeviceId = if (isDimmingFocused) null else focusedDevice.id
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
                    onClick = { onApplyTvScene("MOVIE_MODE") },
                    testTag = "tv_scene_movie"
                )
                TvSceneChip(
                    title = "Holiday ON",
                    icon = Icons.Default.Park,
                    onClick = { onApplyTvScene("HOLIDAY_ON") },
                    testTag = "tv_scene_holiday"
                )
                TvSceneChip(
                    title = "All OFF",
                    icon = Icons.Default.Bedtime,
                    onClick = { onApplyTvScene("BEDTIME_OFF") },
                    testTag = "tv_scene_bedtime"
                )

                var isScanFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isScanFocused) WemoGreenPrimary else WemoGreenDark,
                    border = BorderStroke(
                        width = if (isScanFocused) 2.5.dp else 1.dp,
                        color = if (isScanFocused) Color.White else TvCyanFocusBorder.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .onFocusChanged { isScanFocused = it.isFocused }
                        .focusable()
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

        Spacer(modifier = Modifier.height(10.dp))

        // Main Area: Full-Bleed 3-Column Room-Grouped Switch Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("tv_device_grid")
        ) {
            groupedByRoom.forEach { (roomName, roomDevices) ->
                item(
                    key = "room_header_$roomName",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    val roomDimmers = roomDevices.filter {
                        it.parsedType == DeviceType.LIGHTBULB_DIMMER && it.isReachable
                    }
                    TvRoomSectionHeader(
                        roomName = roomName,
                        roomDevices = roomDevices,
                        isTogglingRoom = togglingRooms.contains(roomName),
                        onToggleRoomPower = { onToggleRoomPower(roomName) },
                        onStepRoomBrightness = if (roomDimmers.isNotEmpty()) {
                            { delta ->
                                roomDimmers.forEach { dimmer ->
                                    onStepDeviceBrightness(dimmer, delta)
                                }
                            }
                        } else {
                            null
                        }
                    )
                }

                items(
                    items = roomDevices,
                    key = { it.id }
                ) { device ->
                    val isFirstCard = device.id == firstDeviceId
                    val isFocused = focusedDevice?.id == device.id
                    val isDimmingThisCard = dimmingDeviceId == device.id

                    TvDeviceCard(
                        device = device,
                        isFocusedByRemote = isFocused,
                        isDimmingMode = isDimmingThisCard,
                        isToggling = togglingDeviceIds.contains(device.id),
                        focusRequester = if (isFirstCard) firstCardFocusRequester else null,
                        onSelectFocus = {
                            focusedDeviceId = device.id
                            if (dimmingDeviceId != null && dimmingDeviceId != device.id) {
                                dimmingDeviceId = null
                            }
                        },
                        onPrimaryAction = { triggerPrimaryAction(device) },
                        onToggleDimmingMode = {
                            focusedDeviceId = device.id
                            dimmingDeviceId = if (dimmingDeviceId == device.id) null else device.id
                        },
                        onExitDimmingMode = {
                            if (dimmingDeviceId == device.id) {
                                dimmingDeviceId = null
                            }
                        },
                        onStepBrightness = { delta ->
                            onStepDeviceBrightness(device, delta)
                        }
                    )
                }
            }
        }
    }
}
