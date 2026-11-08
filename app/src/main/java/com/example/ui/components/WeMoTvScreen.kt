package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.SoapLogEntry
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.ui.theme.ComfortaaFontFamily
import com.example.ui.theme.WemoGreenBottom
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenTop
import com.example.ui.theme.WorkSansFontFamily

private val TvDeepBackground = Color(0xFF071324)
private val TvSurfaceCard = Color(0xFF0D213B)
private val TvSurfaceCardActive = Color(0xFF0E345A)
private val TvCyanFocusBorder = Color(0xFF22D3EE)
private val TvTextPrimary = Color(0xFFF8FAFC)
private val TvTextSecondary = Color(0xFF94A3B8)

/**
 * 10-Foot Android TV Interface for controlling WeMi / WeMo smart switches with a TV Remote (D-Pad).
 * Supports both hardware Android TV remotes (DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER,
 * ENTER, MEDIA_PLAY_PAUSE) and an interactive On-Screen TV Remote D-Pad controller.
 */
@Composable
fun WeMoTvDashboardScreen(
    devices: List<WeMoDeviceEntity>,
    rooms: List<String>,
    selectedRoomFilter: String?,
    isRefreshing: Boolean,
    togglingDeviceIds: Set<Int>,
    togglingRooms: Set<String>,
    latestSoapLog: SoapLogEntry?,
    onSelectRoomFilter: (String?) -> Unit,
    onToggleDevicePower: (WeMoDeviceEntity) -> Unit,
    onStepDeviceBrightness: (WeMoDeviceEntity, Int) -> Unit,
    onTriggerMotion: (WeMoDeviceEntity) -> Unit,
    onReconnectDevice: (WeMoDeviceEntity) -> Unit,
    onToggleRoomPower: (String) -> Unit,
    onApplyTvScene: (String) -> Unit,
    onScanSsdpClick: () -> Unit,
    onExitTvMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredDevices = remember(devices, selectedRoomFilter) {
        if (selectedRoomFilter == null) {
            devices
        } else {
            devices.filter { it.roomName.equals(selectedRoomFilter, ignoreCase = true) }
        }
    }

    var focusedIndex by remember(filteredDevices.size, selectedRoomFilter) {
        mutableIntStateOf(0)
    }
    var showVirtualRemotePad by remember { mutableStateOf(true) }

    val focusedDevice = filteredDevices.getOrNull(
        focusedIndex.coerceIn(0, (filteredDevices.size - 1).coerceAtLeast(0))
    )

    val activeSwitchCount = devices.count {
        it.isReachable && it.parsedType != WeMoDeviceType.MOTION_SENSOR && it.isOn
    }
    val totalSwitchCount = devices.count {
        it.isReachable && it.parsedType != WeMoDeviceType.MOTION_SENSOR
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        TvDeepBackground,
                        Color(0xFF0A1B33),
                        Color(0xFF050E1B)
                    )
                )
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.MediaPlayPause, Key.MediaPlay -> {
                            val targetRoom = selectedRoomFilter
                                ?: focusedDevice?.roomName
                                ?: rooms.firstOrNull()
                            if (targetRoom != null) {
                                onToggleRoomPower(targetRoom)
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("tv_dashboard_screen")
    ) {
        // 1. 10-Foot Android TV Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WemoGreenPrimary.copy(alpha = 0.22f),
                    border = BorderStroke(1.5.dp, TvCyanFocusBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = TvCyanFocusBorder,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "wemi TV",
                            color = Color.White,
                            fontFamily = ComfortaaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }

                Column {
                    Text(
                        text = "10-Foot Smart Switch Remote Control",
                        color = TvTextPrimary,
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "$activeSwitchCount/$totalSwitchCount Switches ON • UDP 239.255.255.250:1900",
                        color = TvCyanFocusBorder,
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showVirtualRemotePad = !showVirtualRemotePad },
                    border = BorderStroke(
                        1.dp,
                        if (showVirtualRemotePad) TvCyanFocusBorder else TvTextSecondary.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (showVirtualRemotePad) {
                            WemoGreenPrimary.copy(alpha = 0.25f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("tv_virtual_remote_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showVirtualRemotePad) "Hide Remote Pad" else "Remote D-Pad",
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onExitTvMode,
                    border = BorderStroke(1.dp, TvTextSecondary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("tv_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit TV Mode",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Phone View",
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. One-Click TV Remote Living Room Scenes & Room Filter Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvSceneChip(
                title = "Movie Mode (20%)",
                icon = Icons.Default.Movie,
                onClick = { onApplyTvScene("MOVIE_MODE") },
                testTag = "tv_scene_movie"
            )
            TvSceneChip(
                title = "Holiday Lights ON",
                icon = Icons.Default.Celebration,
                onClick = { onApplyTvScene("HOLIDAY_ON") },
                testTag = "tv_scene_holiday"
            )
            TvSceneChip(
                title = "Bedtime All OFF",
                icon = Icons.Default.Bedtime,
                onClick = { onApplyTvScene("BEDTIME_OFF") },
                testTag = "tv_scene_bedtime"
            )
            TvSceneChip(
                title = if (isRefreshing) "Scanning LAN..." else "SSDP Scan",
                icon = Icons.Default.Radar,
                onClick = onScanSsdpClick,
                testTag = "tv_scan_button"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Room Filter Rail for TV Remote
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("tv_room_rail"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvRoomPill(
                label = "All Rooms (${devices.size})",
                isSelected = selectedRoomFilter == null,
                onClick = {
                    onSelectRoomFilter(null)
                    focusedIndex = 0
                },
                testTag = "tv_room_pill_All"
            )

            rooms.forEach { room ->
                val roomDevs = devices.filter { it.roomName.equals(room, ignoreCase = true) }
                val onCount = roomDevs.count {
                    it.isReachable && it.parsedType != WeMoDeviceType.MOTION_SENSOR && it.isOn
                }
                TvRoomPill(
                    label = "$room ($onCount/${roomDevs.size})",
                    isSelected = selectedRoomFilter.equals(room, ignoreCase = true),
                    onClick = {
                        onSelectRoomFilter(room)
                        focusedIndex = 0
                    },
                    testTag = "tv_room_pill_$room"
                )
            }

            val currentMasterRoom = selectedRoomFilter ?: focusedDevice?.roomName
            if (currentMasterRoom != null) {
                Button(
                    onClick = { onToggleRoomPower(currentMasterRoom) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WemoGreenPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("tv_master_room_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Toggle All in $currentMasterRoom",
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Interactive On-Screen Virtual TV Remote D-Pad Controller (for testing D-Pad actions anywhere)
        AnimatedVisibility(
            visible = showVirtualRemotePad && focusedDevice != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (focusedDevice != null) {
                WeMoVirtualTvRemoteBar(
                    focusedDevice = focusedDevice,
                    focusedIndex = focusedIndex,
                    totalCount = filteredDevices.size,
                    onPrevDevice = {
                        if (filteredDevices.isNotEmpty()) {
                            focusedIndex = (focusedIndex - 1 + filteredDevices.size) % filteredDevices.size
                        }
                    },
                    onNextDevice = {
                        if (filteredDevices.isNotEmpty()) {
                            focusedIndex = (focusedIndex + 1) % filteredDevices.size
                        }
                    },
                    onPressOkToggle = {
                        when {
                            !focusedDevice.isReachable -> onReconnectDevice(focusedDevice)
                            focusedDevice.parsedType == WeMoDeviceType.MOTION_SENSOR ->
                                onTriggerMotion(focusedDevice)
                            else -> onToggleDevicePower(focusedDevice)
                        }
                    },
                    onStepDimmer = { delta ->
                        onStepDeviceBrightness(focusedDevice, delta)
                    },
                    onToggleFocusedRoom = {
                        onToggleRoomPower(focusedDevice.roomName)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. 10-Foot Focusable Smart Switch Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("tv_devices_grid"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(
                items = filteredDevices,
                key = { _, dev -> "tv_card_${dev.id}" }
            ) { index, device ->
                val isSelectedByRemote = index == focusedIndex
                WeMoTvDeviceCard(
                    device = device,
                    isFocusedByRemote = isSelectedByRemote,
                    isToggling = device.id in togglingDeviceIds || device.roomName in togglingRooms,
                    onSelectFocus = { focusedIndex = index },
                    onPrimaryAction = {
                        focusedIndex = index
                        when {
                            !device.isReachable -> onReconnectDevice(device)
                            device.parsedType == WeMoDeviceType.MOTION_SENSOR -> onTriggerMotion(device)
                            else -> onToggleDevicePower(device)
                        }
                    },
                    onStepBrightness = { delta ->
                        focusedIndex = index
                        onStepDeviceBrightness(device, delta)
                    }
                )
            }
        }

        // 6. Bottom TV Remote Shortcut Legend & Live UPnP Status Ticker
        Surface(
            color = Color(0xFF0A192F),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[OK / Select] Toggle Light   [◀ / ▶] Dimmer -/+10%   [Play] Room Master Toggle",
                    color = TvCyanFocusBorder,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (latestSoapLog != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${latestSoapLog.soapAction} • ${latestSoapLog.endpoint}",
                        color = TvTextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeMoVirtualTvRemoteBar(
    focusedDevice: WeMoDeviceEntity,
    focusedIndex: Int,
    totalCount: Int,
    onPrevDevice: () -> Unit,
    onNextDevice: () -> Unit,
    onPressOkToggle: () -> Unit,
    onStepDimmer: (Int) -> Unit,
    onToggleFocusedRoom: () -> Unit
) {
    Surface(
        color = Color(0xFF0E2744),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, TvCyanFocusBorder.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tv_virtual_remote_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TV Remote Focus (${focusedIndex + 1}/$totalCount): ${focusedDevice.name}",
                        color = Color.White,
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${focusedDevice.roomName} • ${focusedDevice.ipAddress}:${focusedDevice.port} • " +
                            if (!focusedDevice.isReachable) "Offline (Press OK to Reconnect)"
                            else if (focusedDevice.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER)
                                "${if (focusedDevice.isOn) "ON" else "OFF"} (${focusedDevice.brightness}% Brightness)"
                            else if (focusedDevice.parsedType == WeMoDeviceType.MOTION_SENSOR)
                                "Motion: ${focusedDevice.lastMotionTime ?: "Active"}"
                            else if (focusedDevice.isOn) "Switch ON" else "Switch OFF",
                        color = TvCyanFocusBorder,
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrevDevice,
                    border = BorderStroke(1.dp, TvCyanFocusBorder.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("tv_remote_dpad_prev")
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Switch", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prev", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onNextDevice,
                    border = BorderStroke(1.dp, TvCyanFocusBorder.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("tv_remote_dpad_next")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Switch", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Next", fontSize = 12.sp)
                }

                Button(
                    onClick = onPressOkToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WemoGreenTop,
                        contentColor = TvDeepBackground
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("tv_remote_dpad_ok")
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OK: Toggle Switch",
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (focusedDevice.parsedType == WeMoDeviceType.LIGHTBULB_DIMMER && focusedDevice.isReachable) {
                    OutlinedButton(
                        onClick = { onStepDimmer(-10) },
                        border = BorderStroke(1.dp, TvCyanFocusBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TvCyanFocusBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("tv_remote_dpad_dim_down")
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("-10%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { onStepDimmer(10) },
                        border = BorderStroke(1.dp, TvCyanFocusBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TvCyanFocusBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("tv_remote_dpad_dim_up")
                    ) {
                        Text("+10%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                OutlinedButton(
                    onClick = onToggleFocusedRoom,
                    border = BorderStroke(1.dp, WemoGreenPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("tv_remote_room_toggle")
                ) {
                    Text(
                        text = "Room Toggle (${focusedDevice.roomName})",
                        fontFamily = WorkSansFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WeMoTvDeviceCard(
    device: WeMoDeviceEntity,
    isFocusedByRemote: Boolean,
    isToggling: Boolean,
    onSelectFocus: () -> Unit,
    onPrimaryAction: () -> Unit,
    onStepBrightness: (Int) -> Unit
) {
    var hasHardwareFocus by remember { mutableStateOf(false) }
    val isHighlighted = isFocusedByRemote || hasHardwareFocus
    val type = device.parsedType

    val cardColor by animateColorAsState(
        targetValue = when {
            !device.isReachable -> Color(0xFF111C2D)
            device.isOn && type != WeMoDeviceType.MOTION_SENSOR -> TvSurfaceCardActive
            else -> TvSurfaceCard
        },
        label = "tvCardBg"
    )

    val borderStroke = if (isHighlighted) {
        BorderStroke(3.dp, TvCyanFocusBorder)
    } else if (device.isReachable && device.isOn && type != WeMoDeviceType.MOTION_SENSOR) {
        BorderStroke(1.5.dp, WemoGreenPrimary.copy(alpha = 0.65f))
    } else {
        BorderStroke(1.dp, Color(0xFF1E293B))
    }

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = borderStroke,
        shadowElevation = if (isHighlighted) 10.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                hasHardwareFocus = state.isFocused
                if (state.isFocused) {
                    onSelectFocus()
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onPrimaryAction()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (type == WeMoDeviceType.LIGHTBULB_DIMMER && device.isReachable) {
                                onStepBrightness(-10)
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (type == WeMoDeviceType.LIGHTBULB_DIMMER && device.isReachable) {
                                onStepBrightness(10)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onPrimaryAction() }
            .testTag("tv_device_card_${device.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isHighlighted) {
                        TvCyanFocusBorder.copy(alpha = 0.22f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = iconForRoom(device.roomName),
                            contentDescription = null,
                            tint = TvCyanFocusBorder,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = device.roomName,
                            color = TvCyanFocusBorder,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Distinct Power / Connection Status Ring (ON vs OFF-Standby vs Disconnected)
                Surface(
                    shape = CircleShape,
                    color = when {
                        !device.isReachable -> Color(0xFF450A0A)
                        type == WeMoDeviceType.MOTION_SENSOR -> WemoGreenPrimary.copy(alpha = 0.25f)
                        device.isOn -> WemoGreenTop
                        else -> Color(0xFF1E293B)
                    },
                    border = BorderStroke(
                        width = 2.dp,
                        color = when {
                            !device.isReachable -> Color(0xFFEF4444)
                            device.isOn -> Color.White
                            else -> Color(0xFFF59E0B)
                        }
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                !device.isReachable -> Icons.Default.WifiOff
                                type == WeMoDeviceType.MOTION_SENSOR -> Icons.Default.Sensors
                                !device.isOn -> Icons.Default.PowerOff
                                type == WeMoDeviceType.LIGHTBULB_DIMMER -> Icons.Default.Lightbulb
                                else -> Icons.Default.PowerSettingsNew
                            },
                            contentDescription = "Toggle ${device.name}",
                            tint = when {
                                !device.isReachable -> Color(0xFFFCA5A5)
                                device.isOn -> TvDeepBackground
                                else -> Color(0xFFFBBF24)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = device.name,
                color = TvTextPrimary,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            val statusLabel = when {
                isToggling -> "Sending SOAP SetBinaryState..."
                !device.isReachable -> "Disconnected • Offline (Press OK to Retry)"
                type == WeMoDeviceType.MOTION_SENSOR -> "${device.connectionBadgeText} • Motion: ${device.lastMotionTime ?: "Active"}"
                type == WeMoDeviceType.LIGHTBULB_DIMMER ->
                    if (device.isOn) "${device.connectionBadgeText} • ON (${device.brightness}%)" else "${device.connectionBadgeText} • OFF (Standby)"
                else -> if (device.isOn) "${device.connectionBadgeText} • ON (${device.ipAddress})" else "${device.connectionBadgeText} • OFF (${device.ipAddress})"
            }

            Text(
                text = statusLabel,
                color = when {
                    !device.isReachable -> Color(0xFFF87171)
                    device.isOn -> TvCyanFocusBorder
                    else -> Color(0xFFFBBF24)
                },
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (type == WeMoDeviceType.LIGHTBULB_DIMMER && device.isReachable) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onStepBrightness(-10) }
                            .testTag("tv_card_dim_minus_${device.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease brightness",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (device.brightness / 100f).coerceIn(0f, 1f) },
                        color = TvCyanFocusBorder,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onStepBrightness(10) }
                            .testTag("tv_card_dim_plus_${device.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase brightness",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSceneChip(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        color = if (isFocused) WemoGreenPrimary else Color(0xFF102A4C),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else TvCyanFocusBorder.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvCyanFocusBorder,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun TvRoomPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (isSelected) TvCyanFocusBorder else Color(0xFF0F2540),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color.White else TvCyanFocusBorder.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = if (isSelected) TvDeepBackground else Color.White,
            fontFamily = WorkSansFontFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
