package org.suppressingfire.wemi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.theme.ComfortaaFontFamily
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenTop
import org.suppressingfire.wemi.ui.theme.WorkSansFontFamily

internal val TvDeepBackground = Color(0xFF071324)
internal val TvSurfaceCard = Color(0xFF0D213B)
internal val TvSurfaceCardActive = Color(0xFF0E345A)
internal val TvCyanFocusBorder = Color(0xFF22D3EE)
internal val TvTextPrimary = Color(0xFFF8FAFC)
internal val TvTextSecondary = Color(0xFF94A3B8)

/**
 * 10-Foot Android TV Interface for controlling smart switches with a TV Remote (D-Pad).
 * Supports both hardware Android TV remotes (DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER,
 * ENTER, MEDIA_PLAY_PAUSE) and an interactive On-Screen TV Remote D-Pad controller.
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
        it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR && it.isOn
    }
    val totalSwitchCount = devices.count {
        it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR
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
                    it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR && it.isOn
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
                VirtualTvRemoteBar(
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
                            focusedDevice.parsedType == DeviceType.MOTION_SENSOR ->
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
                TvDeviceCard(
                    device = device,
                    isFocusedByRemote = isSelectedByRemote,
                    isToggling = device.id in togglingDeviceIds || device.roomName in togglingRooms,
                    onSelectFocus = { focusedIndex = index },
                    onPrimaryAction = {
                        focusedIndex = index
                        when {
                            !device.isReachable -> onReconnectDevice(device)
                            device.parsedType == DeviceType.MOTION_SENSOR -> onTriggerMotion(device)
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
private fun VirtualTvRemoteBar(
    focusedDevice: DeviceEntity,
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
                            else if (focusedDevice.parsedType == DeviceType.LIGHTBULB_DIMMER)
                                "${if (focusedDevice.isOn) "ON" else "OFF"} (${focusedDevice.brightness}% Brightness)"
                            else if (focusedDevice.parsedType == DeviceType.MOTION_SENSOR)
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

                if (focusedDevice.parsedType == DeviceType.LIGHTBULB_DIMMER && focusedDevice.isReachable) {
                    OutlinedButton(
                        onClick = { onStepDimmer(-10) },
                        border = BorderStroke(1.dp, TvCyanFocusBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TvCyanFocusBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("tv_remote_dpad_dim_down")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
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
