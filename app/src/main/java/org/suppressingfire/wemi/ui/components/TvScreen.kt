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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SoapLogEntry

internal val TvDeepBackground = Color(0xFF060E1A)
internal val TvSurfaceCard = Color(0xFF0C1829)
internal val TvSurfaceCardActive = Color(0xFF0F2744)
internal val TvCyanFocusBorder = Color(0xFF22D3EE)
internal val TvTextPrimary = Color(0xFFF8FAFC)
internal val TvTextSecondary = Color(0xFF94A3B8)

internal sealed interface TvFocusTarget {
    data class TopAction(val index: Int) : TvFocusTarget
    data class RoomHeader(val roomName: String) : TvFocusTarget
    data class DeviceToggle(val deviceId: Int) : TvFocusTarget
    data class DeviceSlider(val deviceId: Int) : TvFocusTarget
}

/**
 * 10-foot Android TV & Large-Screen Landscape Dashboard (`wemi TV`).
 *
 * Designed around three core remote-control ergonomics principles:
 * 1. Maximum Screen Real Estate: A single compact 42.dp top bar leaves ~90% of vertical screen height
 *    for the 3-column Room-Grouped Switch Grid (`GridCells.Fixed(3)`).
 * 2. Deterministic Vertical & Horizontal D-Pad Navigation:
 *    - Navigating Up or Down through a Room Bar (`TvRoomSectionHeader`) from ANY column (including the
 *      leftmost switch widget in Column 1) always selects the Room Bar as the next widget.
 *    - Navigating Down into a dimmer card focuses its On/Off Toggle (`tv_device_card_<id>`), where
 *      Left/Right moves between switch cards and OK toggles power.
 *    - Navigating Down once more focuses the Dimmer Slider (`tv_card_dim_mode_<id>`), where Left/Right
 *      lowers/raises brightness (`-10%` / `+10%`) and Down moves to the widget below.
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
    val firstDeviceId = orderedDevices.firstOrNull()?.id

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<TvFocusTarget, FocusRequester>() }
    val requesterFor: (TvFocusTarget) -> FocusRequester = { target ->
        focusRequesters.getOrPut(target) { FocusRequester() }
    }
    val topActionFocusRequesters = remember {
        (0..3).map { requesterFor(TvFocusTarget.TopAction(it)) }
    }

    val gridIndexByTarget: Map<TvFocusTarget, Int> = remember(groupedByRoom) {
        buildMap {
            var currentGridIndex = 0
            groupedByRoom.forEach { (roomName, roomDevices) ->
                put(TvFocusTarget.RoomHeader(roomName), currentGridIndex)
                currentGridIndex += 1
                roomDevices.forEach { device ->
                    put(TvFocusTarget.DeviceToggle(device.id), currentGridIndex)
                    if (device.parsedType == DeviceType.LIGHTBULB_DIMMER && device.isReachable) {
                        put(TvFocusTarget.DeviceSlider(device.id), currentGridIndex)
                    }
                    currentGridIndex += 1
                }
            }
        }
    }

    var activeFocusTarget by remember(orderedDevices.size) {
        mutableStateOf<TvFocusTarget?>(firstDeviceId?.let { TvFocusTarget.DeviceToggle(it) })
    }
    var lastFocusedDeviceId by remember(orderedDevices.size) {
        mutableStateOf(firstDeviceId)
    }
    var lastFocusedColumn by remember { mutableIntStateOf(0) }

    val navigateTo: (TvFocusTarget?) -> Boolean = { target ->
        if (target != null) {
            activeFocusTarget = target
            when (target) {
                is TvFocusTarget.DeviceToggle -> lastFocusedDeviceId = target.deviceId
                is TvFocusTarget.DeviceSlider -> lastFocusedDeviceId = target.deviceId
                else -> Unit
            }
            val requester = requesterFor(target)
            val gridIdx = gridIndexByTarget[target]
            val isVisibleInGrid = gridIdx == null ||
                gridState.layoutInfo.visibleItemsInfo.any { it.index == gridIdx }
            if (isVisibleInGrid) {
                runCatching { requester.requestFocus() }
            } else {
                coroutineScope.launch {
                    gridState.scrollToItem(gridIdx)
                    withFrameNanos { }
                    runCatching { requester.requestFocus() }
                }
            }
        }
        true
    }

    // Automatically request initial hardware D-Pad focus on the first switch card when TV dashboard loads
    LaunchedEffect(firstDeviceId) {
        if (firstDeviceId != null) {
            if (lastFocusedDeviceId == null || orderedDevices.none { it.id == lastFocusedDeviceId }) {
                lastFocusedDeviceId = firstDeviceId
                activeFocusTarget = TvFocusTarget.DeviceToggle(firstDeviceId)
            }
            runCatching { requesterFor(TvFocusTarget.DeviceToggle(firstDeviceId)).requestFocus() }
        }
    }

    val focusedDevice = orderedDevices.firstOrNull { it.id == lastFocusedDeviceId }
        ?: orderedDevices.firstOrNull()
    val dimmingDeviceId: Int? = (activeFocusTarget as? TvFocusTarget.DeviceSlider)?.deviceId

    // Pressing Back on the TV remote while on the Dimmer Slider returns focus to the card's On/Off Toggle
    BackHandler(enabled = dimmingDeviceId != null) {
        val currentDimId = dimmingDeviceId
        if (currentDimId != null) {
            navigateTo(TvFocusTarget.DeviceToggle(currentDimId))
        }
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
            topActionFocusRequesters = topActionFocusRequesters,
            onSelectTopActionFocus = { idx -> activeFocusTarget = TvFocusTarget.TopAction(idx) },
            onNavigateDownFromTopBar = {
                val firstRoom = groupedByRoom.firstOrNull()?.first
                if (firstRoom != null) navigateTo(TvFocusTarget.RoomHeader(firstRoom)) else true
            },
            onNavigateTopActionHorizontal = { targetIdx ->
                navigateTo(TvFocusTarget.TopAction(targetIdx.coerceIn(0, 3)))
            },
            onPrimaryActionOnFocused = { triggerPrimaryAction(it) },
            onToggleDimmingFocused = { targetDimId ->
                if (targetDimId != null) {
                    navigateTo(TvFocusTarget.DeviceSlider(targetDimId))
                } else if (focusedDevice != null) {
                    navigateTo(TvFocusTarget.DeviceToggle(focusedDevice.id))
                }
            },
            onApplyTvScene = onApplyTvScene,
            onScanSsdpClick = onScanSsdpClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Main Area: Full-Bleed 3-Column Room-Grouped Switch Grid
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("tv_device_grid")
        ) {
            groupedByRoom.forEachIndexed { roomIndex, (roomName, roomDevices) ->
                val roomRows = roomDevices.chunked(3)
                val devicePositionById: Map<Int, Pair<Int, Int>> = buildMap {
                    roomRows.forEachIndexed { rIdx, row ->
                        row.forEachIndexed { cIdx, dev ->
                            put(dev.id, rIdx to cIdx)
                        }
                    }
                }

                item(
                    key = "room_header_$roomName",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    val roomDimmers = roomDevices.filter {
                        it.parsedType == DeviceType.LIGHTBULB_DIMMER && it.isReachable
                    }
                    val roomTarget = TvFocusTarget.RoomHeader(roomName)
                    val upFromRoomTarget: TvFocusTarget = if (roomIndex > 0) {
                        val prevRows = groupedByRoom[roomIndex - 1].second.chunked(3)
                        val prevLastRow = prevRows.lastOrNull().orEmpty()
                        val targetDev = prevLastRow.getOrNull(
                            lastFocusedColumn.coerceAtMost(prevLastRow.lastIndex.coerceAtLeast(0))
                        )
                        if (targetDev != null) {
                            if (targetDev.parsedType == DeviceType.LIGHTBULB_DIMMER && targetDev.isReachable) {
                                TvFocusTarget.DeviceSlider(targetDev.id)
                            } else {
                                TvFocusTarget.DeviceToggle(targetDev.id)
                            }
                        } else {
                            TvFocusTarget.RoomHeader(groupedByRoom[roomIndex - 1].first)
                        }
                    } else {
                        TvFocusTarget.TopAction(0)
                    }

                    val downFromRoomTarget: TvFocusTarget? = run {
                        val firstRow = roomRows.firstOrNull().orEmpty()
                        val targetDev = firstRow.getOrNull(
                            lastFocusedColumn.coerceAtMost(firstRow.lastIndex.coerceAtLeast(0))
                        )
                        when {
                            targetDev != null -> TvFocusTarget.DeviceToggle(targetDev.id)
                            roomIndex + 1 < groupedByRoom.size ->
                                TvFocusTarget.RoomHeader(groupedByRoom[roomIndex + 1].first)
                            else -> null
                        }
                    }

                    TvRoomSectionHeader(
                        roomName = roomName,
                        roomDevices = roomDevices,
                        isFocusedByRemote = activeFocusTarget == roomTarget,
                        isTogglingRoom = togglingRooms.contains(roomName),
                        focusRequester = requesterFor(roomTarget),
                        onSelectRoomFocus = { activeFocusTarget = roomTarget },
                        onNavigateUp = { navigateTo(upFromRoomTarget) },
                        onNavigateDown = { navigateTo(downFromRoomTarget) },
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
                    val (rowIndex, colIndex) = devicePositionById[device.id] ?: (0 to 0)
                    val currentRow = roomRows.getOrNull(rowIndex).orEmpty()
                    val isDimmer = device.parsedType == DeviceType.LIGHTBULB_DIMMER && device.isReachable
                    val toggleTarget = TvFocusTarget.DeviceToggle(device.id)
                    val sliderTarget = TvFocusTarget.DeviceSlider(device.id)

                    val leftTarget: TvFocusTarget? = if (colIndex > 0) {
                        currentRow.getOrNull(colIndex - 1)?.let { TvFocusTarget.DeviceToggle(it.id) }
                    } else {
                        null
                    }
                    val rightTarget: TvFocusTarget? = if (colIndex < currentRow.lastIndex) {
                        currentRow.getOrNull(colIndex + 1)?.let { TvFocusTarget.DeviceToggle(it.id) }
                    } else {
                        null
                    }
                    val upFromToggleTarget: TvFocusTarget = if (rowIndex > 0) {
                        val prevRow = roomRows[rowIndex - 1]
                        val aboveDev = prevRow[colIndex.coerceAtMost(prevRow.lastIndex)]
                        if (aboveDev.parsedType == DeviceType.LIGHTBULB_DIMMER && aboveDev.isReachable) {
                            TvFocusTarget.DeviceSlider(aboveDev.id)
                        } else {
                            TvFocusTarget.DeviceToggle(aboveDev.id)
                        }
                    } else {
                        TvFocusTarget.RoomHeader(roomName)
                    }
                    val downBelowCardTarget: TvFocusTarget? = when {
                        rowIndex + 1 < roomRows.size -> {
                            val nextRow = roomRows[rowIndex + 1]
                            val belowDev = nextRow[colIndex.coerceAtMost(nextRow.lastIndex)]
                            TvFocusTarget.DeviceToggle(belowDev.id)
                        }
                        roomIndex + 1 < groupedByRoom.size ->
                            TvFocusTarget.RoomHeader(groupedByRoom[roomIndex + 1].first)
                        else -> null
                    }

                    TvDeviceCard(
                        device = device,
                        isToggleFocusedByRemote = activeFocusTarget == toggleTarget,
                        isSliderFocusedByRemote = activeFocusTarget == sliderTarget,
                        isToggling = togglingDeviceIds.contains(device.id),
                        toggleFocusRequester = requesterFor(toggleTarget),
                        sliderFocusRequester = if (isDimmer) requesterFor(sliderTarget) else null,
                        onSelectToggleFocus = {
                            activeFocusTarget = toggleTarget
                            lastFocusedDeviceId = device.id
                            lastFocusedColumn = colIndex
                        },
                        onSelectSliderFocus = {
                            activeFocusTarget = sliderTarget
                            lastFocusedDeviceId = device.id
                            lastFocusedColumn = colIndex
                        },
                        onPrimaryAction = { triggerPrimaryAction(device) },
                        onStepBrightness = { delta ->
                            onStepDeviceBrightness(device, delta)
                        },
                        onNavigateLeft = {
                            if (leftTarget != null) {
                                lastFocusedColumn = colIndex - 1
                                navigateTo(leftTarget)
                            } else {
                                true
                            }
                        },
                        onNavigateRight = {
                            if (rightTarget != null) {
                                lastFocusedColumn = colIndex + 1
                                navigateTo(rightTarget)
                            } else {
                                true
                            }
                        },
                        onNavigateUpFromToggle = {
                            lastFocusedColumn = colIndex
                            navigateTo(upFromToggleTarget)
                        },
                        onNavigateDownFromToggle = {
                            lastFocusedColumn = colIndex
                            if (isDimmer) {
                                navigateTo(sliderTarget)
                            } else {
                                navigateTo(downBelowCardTarget)
                            }
                        },
                        onNavigateUpFromSlider = {
                            lastFocusedColumn = colIndex
                            navigateTo(toggleTarget)
                        },
                        onNavigateDownFromSlider = {
                            lastFocusedColumn = colIndex
                            navigateTo(downBelowCardTarget)
                        }
                    )
                }
            }
        }
    }
}

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
                            onStepBrightness(-10)
                            true
                        }
                        Key.DirectionRight, Key.Equals, Key.Plus -> {
                            onStepBrightness(10)
                            true
                        }
                        Key.DirectionUp -> onNavigateUp()
                        Key.DirectionDown -> onNavigateDown()
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.Back,
                        Key.Escape,
                        Key.MediaPlayPause,
                        Key.Menu -> onNavigateUp()
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
                    maxLines = 1
                )
            } else if (isToggleHighlighted) {
                Spacer(modifier = Modifier.height(2.dp))
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
