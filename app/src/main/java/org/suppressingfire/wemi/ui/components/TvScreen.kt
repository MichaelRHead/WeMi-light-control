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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
 *    - When a Wi-Fi Home has zero switches (`orderedDevices.isEmpty()`), D-Pad focus starts directly on
 *      `tv_scan_button` (`Rescan`) so a remote-only user can discover LAN switches with a single OK press.
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
    onScanSsdpClick: () -> Unit,
    onOpenHouseholdSync: () -> Unit = {}
) {
    // Group switches by canonical room (with "Unassigned" placed last) while preserving user sortOrder within each room
    val groupedByRoom: List<Pair<String, List<DeviceEntity>>> = remember(devices, selectedRoomFilter) {
        val base = if (selectedRoomFilter == null) {
            devices
        } else {
            devices.filter {
                it.normalizedRoomName.equals(selectedRoomFilter, ignoreCase = true)
            }
        }
        org.suppressingfire.wemi.data.groupDevicesByCanonicalRoom(base).toList()
    }

    val orderedDevices: List<DeviceEntity> = remember(groupedByRoom) {
        groupedByRoom.flatMap { it.second }
    }
    val hasHolidayDevices = remember(devices) {
        devices.any { org.suppressingfire.wemi.data.isHolidayLightDevice(it) }
    }
    val firstDeviceId = orderedDevices.firstOrNull()?.id

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<TvFocusTarget, FocusRequester>() }
    val requesterFor: (TvFocusTarget) -> FocusRequester = { target ->
        focusRequesters.getOrPut(target) { FocusRequester() }
    }
    val topActionFocusRequesters = remember {
        (0..4).map { requesterFor(TvFocusTarget.TopAction(it)) }
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

    var activeFocusTarget by remember {
        mutableStateOf<TvFocusTarget?>(
            firstDeviceId?.let { TvFocusTarget.DeviceToggle(it) } ?: TvFocusTarget.TopAction(3)
        )
    }
    var lastFocusedDeviceId by remember {
        mutableStateOf(firstDeviceId)
    }
    var lastFocusedColumn by remember { mutableIntStateOf(0) }
    var didRequestInitialGridFocus by remember { mutableStateOf(false) }
    var previousHasHolidayDevices by remember { mutableStateOf(hasHolidayDevices) }
    // Capture synchronously during composition before BoxWithConstraints detaches the Holiday ON chip
    val holidayChipVanishedWhileFocused =
        previousHasHolidayDevices && !hasHolidayDevices && activeFocusTarget == TvFocusTarget.TopAction(1)

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

    // Stabilize hardware D-Pad focus across initial launch, empty-home Rescan, room filter changes, holiday chip changes, and offline dimmer transitions
    LaunchedEffect(orderedDevices, selectedRoomFilter, hasHolidayDevices) {
        previousHasHolidayDevices = hasHolidayDevices
        if (orderedDevices.isEmpty()) {
            didRequestInitialGridFocus = false
            lastFocusedDeviceId = null
            val scanTarget = TvFocusTarget.TopAction(3)
            activeFocusTarget = scanTarget
            runCatching { requesterFor(scanTarget).requestFocus() }
            return@LaunchedEffect
        }

        if (holidayChipVanishedWhileFocused ||
            (!hasHolidayDevices && activeFocusTarget == TvFocusTarget.TopAction(1))
        ) {
            if (lastFocusedDeviceId == null || orderedDevices.none { it.id == lastFocusedDeviceId }) {
                lastFocusedDeviceId = orderedDevices.first().id
            }
            val fallback = TvFocusTarget.TopAction(0)
            activeFocusTarget = fallback
            withFrameNanos { }
            runCatching { requesterFor(fallback).requestFocus() }
            return@LaunchedEffect
        }

        val currentSliderId = (activeFocusTarget as? TvFocusTarget.DeviceSlider)?.deviceId
        if (currentSliderId != null) {
            val sliderDevice = orderedDevices.firstOrNull { it.id == currentSliderId }
            if (sliderDevice == null) {
                val fallbackId = orderedDevices.first().id
                lastFocusedDeviceId = fallbackId
                val fallback = TvFocusTarget.DeviceToggle(fallbackId)
                activeFocusTarget = fallback
                runCatching { requesterFor(fallback).requestFocus() }
                return@LaunchedEffect
            } else if (!sliderDevice.isReachable || sliderDevice.parsedType != DeviceType.LIGHTBULB_DIMMER) {
                val fallback = TvFocusTarget.DeviceToggle(sliderDevice.id)
                activeFocusTarget = fallback
                runCatching { requesterFor(fallback).requestFocus() }
                return@LaunchedEffect
            }
        }

        if (!didRequestInitialGridFocus ||
            lastFocusedDeviceId == null ||
            orderedDevices.none { it.id == lastFocusedDeviceId }
        ) {
            val wasAlreadyInitialized = didRequestInitialGridFocus
            didRequestInitialGridFocus = true
            val initialId = orderedDevices.first().id
            lastFocusedDeviceId = initialId
            val currentFocus = activeFocusTarget
            val keepTopBarFocus = wasAlreadyInitialized &&
                currentFocus is TvFocusTarget.TopAction &&
                (hasHolidayDevices || currentFocus.index != 1)
            if (!keepTopBarFocus) {
                val initialTarget = TvFocusTarget.DeviceToggle(initialId)
                activeFocusTarget = initialTarget
                runCatching { requesterFor(initialTarget).requestFocus() }
            }
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
            showHolidayScene = hasHolidayDevices,
            topActionFocusRequesters = topActionFocusRequesters,
            onSelectTopActionFocus = { idx -> activeFocusTarget = TvFocusTarget.TopAction(idx) },
            onNavigateDownFromTopBar = {
                val firstRoom = groupedByRoom.firstOrNull()?.first
                if (firstRoom != null) navigateTo(TvFocusTarget.RoomHeader(firstRoom)) else true
            },
            onNavigateTopActionHorizontal = { targetIdx ->
                val resolvedIdx = if (!hasHolidayDevices && targetIdx == 1) 2 else targetIdx.coerceIn(0, 4)
                navigateTo(TvFocusTarget.TopAction(resolvedIdx))
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
            onScanSsdpClick = onScanSsdpClick,
            onOpenHouseholdSync = onOpenHouseholdSync
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
