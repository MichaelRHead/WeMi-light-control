package org.suppressingfire.wemi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.ui.components.iconForRoom
import org.suppressingfire.wemi.ui.theme.WeMiDeepSapphireBottom
import org.suppressingfire.wemi.ui.theme.WeMiDeepTealAccent
import org.suppressingfire.wemi.ui.theme.WeMiElectricCyanTop
import org.suppressingfire.wemi.ui.theme.WeMiOceanTealPrimary
import org.suppressingfire.wemi.ui.theme.WeMiSoftCyanContainer
import org.suppressingfire.wemi.ui.theme.WeMiTheme
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Configuration activity launched by the Android Home Screen widget picker (or widget
 * reconfiguration) to bind a placed widget to any Room or individual Light/Switch.
 */
class SwitchWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.WHITE, AndroidColor.LTGRAY)
        )

        // Default to RESULT_CANCELED if the user backs out without picking a target
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val existingConfig = WidgetConfigStore.load(this, appWidgetId)

        setContent {
            WeMiTheme {
                var devices by remember { mutableStateOf<List<DeviceEntity>>(emptyList()) }
                var homes by remember { mutableStateOf<List<HomeNetworkEntity>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    val loaded = withContext(Dispatchers.IO) {
                        val repo = SwitchRepository(
                            dao = SwitchDatabase.getDatabase(applicationContext).switchDao(),
                            context = applicationContext
                        )
                        try {
                            if (repo.isDemoShowroomMode.value) {
                                repo.ensureSeeded()
                            }
                            Pair(repo.allDevices.first(), repo.allHomeNetworks.first())
                        } finally {
                            repo.close()
                        }
                    }
                    devices = loaded.first
                    homes = loaded.second
                    isLoading = false
                }

                WidgetConfigureScreen(
                    devices = devices,
                    homes = homes,
                    isLoading = isLoading,
                    initialTargetType = existingConfig?.targetType ?: WidgetTargetType.ROOM,
                    initialNetworkKey = existingConfig?.networkKey,
                    onSelectDevice = { selected ->
                        confirmSelection(
                            config = WidgetTargetConfig.forDevice(appWidgetId, selected),
                            allDevices = devices
                        )
                    },
                    onSelectRoom = { roomName, networkKey ->
                        confirmSelection(
                            config = WidgetTargetConfig.forRoom(appWidgetId, roomName, networkKey),
                            allDevices = devices
                        )
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun confirmSelection(config: WidgetTargetConfig, allDevices: List<DeviceEntity>) {
        WidgetConfigStore.save(this, config)
        WidgetUpdater.updateSingleWidget(
            context = this,
            appWidgetId = appWidgetId,
            config = config,
            devices = allDevices,
            isUpdating = false
        )
        val resultIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

@Composable
internal fun WidgetConfigureScreen(
    devices: List<DeviceEntity>,
    homes: List<HomeNetworkEntity>,
    isLoading: Boolean,
    initialTargetType: WidgetTargetType,
    initialNetworkKey: String?,
    onSelectDevice: (DeviceEntity) -> Unit,
    onSelectRoom: (roomName: String, networkKey: String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedTab by remember(initialTargetType) { mutableStateOf(initialTargetType) }
    var selectedHomeKey by remember(homes, initialNetworkKey) {
        mutableStateOf(
            initialNetworkKey?.takeIf { key -> homes.any { it.networkKey == key } }
                ?: homes.firstOrNull()?.networkKey
                ?: devices.firstOrNull()?.networkKey.orEmpty()
        )
    }

    val scopedDevices = remember(devices, selectedHomeKey, homes) {
        val controllable = devices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
        if (selectedHomeKey.isBlank() || homes.size <= 1) {
            controllable
        } else {
            controllable.filter { it.networkKey == selectedHomeKey }
        }
    }

    val roomsWithDevices = remember(scopedDevices) {
        val grouped = scopedDevices.groupBy { it.roomName.trim().ifBlank { "Unassigned" } }
        val assigned = grouped.filterKeys { !it.equals("Unassigned", ignoreCase = true) }
        val unassigned = grouped.filterKeys { it.equals("Unassigned", ignoreCase = true) }
        (assigned + unassigned).toList()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = WemoBackgroundGray,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(WeMiElectricCyanTop, WeMiOceanTealPrimary, WeMiDeepSapphireBottom)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_config_title),
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = stringResource(R.string.widget_config_subtitle),
                            color = Color.White.copy(alpha = 0.88f),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("widget_config_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Target Type Selector: Rooms vs. Individual Lights & Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == WidgetTargetType.ROOM,
                        onClick = { selectedTab = WidgetTargetType.ROOM },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Rooms (${roomsWithDevices.size})",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WeMiDeepTealAccent,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("widget_config_tab_rooms")
                    )

                    FilterChip(
                        selected = selectedTab == WidgetTargetType.DEVICE,
                        onClick = { selectedTab = WidgetTargetType.DEVICE },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Lights (${scopedDevices.size})",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WeMiDeepTealAccent,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("widget_config_tab_lights")
                    )
                }

                if (homes.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        homes.forEach { home ->
                            FilterChip(
                                selected = home.networkKey == selectedHomeKey,
                                onClick = { selectedHomeKey = home.networkKey },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(home.homeName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WeMiSoftCyanContainer,
                                    selectedLabelColor = WeMiDeepTealAccent,
                                    selectedLeadingIconColor = WeMiDeepTealAccent
                                ),
                                modifier = Modifier.testTag("widget_config_home_${home.networkKey}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading saved lights and rooms...",
                                color = WemoTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    scopedDevices.isEmpty() -> {
                        WidgetEmptyPickerCard()
                    }

                    selectedTab == WidgetTargetType.ROOM -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("widget_config_rooms_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(roomsWithDevices, key = { it.first }) { (roomName, roomDevices) ->
                                val activeCount = roomDevices.count { it.isReachable && it.isOn }
                                val anyOn = activeCount > 0
                                val homeKey = roomDevices.firstOrNull()?.networkKey ?: selectedHomeKey
                                WidgetSelectableCard(
                                    title = roomName,
                                    subtitle = "$activeCount/${roomDevices.size} ON • Toggle entire room",
                                    isOn = anyOn,
                                    stateLabel = if (anyOn) "$activeCount ON" else "OFF",
                                    leadingIcon = iconForRoom(roomName),
                                    testTag = "widget_config_room_$roomName",
                                    onClick = { onSelectRoom(roomName, homeKey) }
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("widget_config_lights_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(scopedDevices, key = { it.id }) { device ->
                                val stateLabel = when {
                                    !device.isReachable -> "OFFLINE"
                                    device.parsedType == DeviceType.LIGHTBULB_DIMMER && device.isOn ->
                                        "ON ${device.brightness}%"
                                    device.isOn -> "ON"
                                    else -> "OFF"
                                }
                                WidgetSelectableCard(
                                    title = device.name,
                                    subtitle = "${device.roomName.trim().ifBlank { "Unassigned" }} • ${device.ipAddress}",
                                    isOn = device.isReachable && device.isOn,
                                    stateLabel = stateLabel,
                                    leadingIcon = Icons.Default.Lightbulb,
                                    testTag = "widget_config_device_${device.id}",
                                    onClick = { onSelectDevice(device) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
