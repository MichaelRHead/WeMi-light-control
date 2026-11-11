package org.suppressingfire.wemi.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SoapLogEntry

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
        TvTopHeaderBar(
            activeSwitchCount = activeSwitchCount,
            totalSwitchCount = totalSwitchCount,
            focusedDevice = focusedDevice,
            dimmingDeviceId = dimmingDeviceId,
            isRefreshing = isRefreshing,
            onPrimaryActionOnFocused = { triggerPrimaryAction(it) },
            onToggleDimmingFocused = { dimmingDeviceId = it },
            onApplyTvScene = onApplyTvScene,
            onScanSsdpClick = onScanSsdpClick
        )

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
