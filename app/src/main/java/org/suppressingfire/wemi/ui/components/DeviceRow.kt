package org.suppressingfire.wemi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoErrorRed
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Full-width switch title that reactively steps down its font size if a long switch name
 * overflows the card width, ensuring the full switch name is always visible without occlusion.
 */
@Composable
fun AutoResizingDeviceNameText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    initialFontSizeSp: Float = 18f,
    minFontSizeSp: Float = 11f,
    fontWeight: FontWeight = FontWeight.SemiBold,
    singleLineOnly: Boolean = false
) {
    var fontSizeSp by remember(text, initialFontSizeSp) { mutableFloatStateOf(initialFontSizeSp) }
    var allowWrapAtMinSize by remember(text, initialFontSizeSp) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.SansSerif,
        fontWeight = fontWeight,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.2f).sp,
        softWrap = !singleLineOnly && allowWrapAtMinSize,
        maxLines = if (!singleLineOnly && allowWrapAtMinSize) 2 else 1,
        overflow = if (singleLineOnly) TextOverflow.Ellipsis else TextOverflow.Visible,
        onTextLayout = { layoutResult ->
            if (!allowWrapAtMinSize && layoutResult.didOverflowWidth) {
                if (fontSizeSp > minFontSizeSp) {
                    fontSizeSp = (fontSizeSp * 0.90f).coerceAtLeast(minFontSizeSp)
                } else if (!singleLineOnly) {
                    allowWrapAtMinSize = true
                }
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceRow(
    device: DeviceEntity,
    isExpanded: Boolean,
    isEditMode: Boolean,
    isToggling: Boolean,
    onTogglePower: () -> Unit,
    onToggleExpand: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onTriggerMotion: () -> Unit,
    onReconnect: () -> Unit,
    onEditDevice: () -> Unit,
    onDeleteDevice: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPingLatency: () -> Unit = onReconnect,
    onToggleNightMode: (() -> Unit)? = null,
    autoOffRemainingSeconds: Int? = null,
    modifier: Modifier = Modifier
) {
    val type = device.parsedType

    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (device.isReachable && device.isOn) 2.dp else 0.dp,
        shadowElevation = if (device.isReachable && device.isOn) 2.dp else 0.5.dp,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !device.isReachable -> StatusOfflineRed.copy(alpha = 0.25f)
                device.isOn && type != DeviceType.MOTION_SENSOR ->
                    WemoGreenPrimary.copy(alpha = 0.28f)
                else -> WemoDividerGray
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("device_row_${device.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when {
                            isEditMode -> onEditDevice()
                            !device.isReachable -> onReconnect()
                            type == DeviceType.MOTION_SENSOR -> onTriggerMotion()
                            else -> onToggleExpand()
                        }
                    }
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                // Top Line: Full-width unobstructed Switch Name with reactive font size scaling
                AutoResizingDeviceNameText(
                    text = device.name,
                    color = if (device.isReachable) WemoTextPrimary else WemoTextPrimary.copy(alpha = 0.65f),
                    initialFontSizeSp = 17.5f,
                    minFontSizeSp = 11f,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_${device.id}")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Second Line (Below Name): Hardware Illustration + Status/Category Widgets + Power/Edit Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Semantic Category / Hardware Illustration with live connection status badge overlay
                    Box(
                        modifier = Modifier.width(52.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        DeviceCategoryIllustration(
                            category = device.visualCategory,
                            isReachable = device.isReachable,
                            isOn = device.isOn,
                            brightness = device.brightness,
                            modifier = Modifier.size(width = 48.dp, height = 52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Middle: All secondary status, category, and telemetry widgets below the switch name
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ConnectionStatusBadge(
                            device = device,
                            isPinging = isToggling,
                            modifier = Modifier.testTag("connection_status_badge_${device.id}")
                        )

                        when {
                            !device.isReachable -> {
                                Text(
                                    text = stringResource(R.string.status_not_detected),
                                    color = WemoTextSecondary,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            type == DeviceType.MOTION_SENSOR -> {
                                Text(
                                    text = stringResource(R.string.status_motion_sensed, device.lastMotionTime),
                                    color = WemoGreenDark,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            type == DeviceType.LIGHTBULB_DIMMER -> {
                                PowerStatePill(
                                    isOn = device.isOn,
                                    label = if (device.isOn) "ON • ${device.brightness}%" else "OFF • ${device.brightness}%",
                                    modifier = Modifier.testTag("power_state_badge_${device.id}")
                                )
                                Icon(
                                    imageVector = if (isExpanded) {
                                        Icons.Default.KeyboardArrowUp
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                    contentDescription = stringResource(
                                        R.string.content_desc_expand_dimmer,
                                        device.name
                                    ),
                                    tint = WemoGreenPrimary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .testTag("expand_chevron_${device.id}")
                                )
                            }
                            else -> {
                                PowerStatePill(
                                    isOn = device.isOn,
                                    label = if (device.isOn) "ON" else "OFF",
                                    modifier = Modifier.testTag("power_state_badge_${device.id}")
                                )
                                val subLabel = if (autoOffRemainingSeconds != null && autoOffRemainingSeconds > 0) {
                                    val mins = autoOffRemainingSeconds / 60
                                    val secs = autoOffRemainingSeconds % 60
                                    "Auto-Off ${mins}:${secs.toString().padStart(2, '0')}"
                                } else {
                                    device.ipAddress
                                }
                                Text(
                                    text = subLabel,
                                    color = WemoTextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.testTag("category_badge_${device.id}")
                        ) {
                            Text(
                                text = device.visualCategory.displayName,
                                color = Color(0xFF475569),
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                // Right: Edit mode controls, Reconnect pill (for Offline), OR Cyan-Teal Power Toggle Ring
                if (isEditMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onMoveUp,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move ${device.name} up",
                                tint = WemoTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onMoveDown,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move ${device.name} down",
                                tint = WemoTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onEditDevice,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("edit_device_${device.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Configure ${device.name}",
                                tint = WemoGreenDark
                            )
                        }
                        IconButton(
                            onClick = onDeleteDevice,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("delete_device_${device.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete ${device.name}",
                                tint = WemoErrorRed
                            )
                        }
                    }
                } else {
                    when {
                        !device.isReachable -> {
                            DisconnectedRingButton(
                                deviceName = device.name,
                                onClick = onReconnect,
                                modifier = Modifier.testTag("reconnect_button_${device.id}")
                            )
                        }
                        type == DeviceType.MOTION_SENSOR -> {
                            Spacer(modifier = Modifier.width(64.dp))
                        }
                        else -> {
                            PowerRingButton(
                                isOn = device.isOn,
                                isToggling = isToggling,
                                deviceName = device.name,
                                onClick = onTogglePower,
                                modifier = Modifier.testTag("power_button_${device.id}")
                            )
                        }
                    }
                }
                }
            }

            // Expandable inline dimmer & UPnP telemetry drawer
            AnimatedVisibility(
                visible = isExpanded && !isEditMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                    ExpandedDrawer(
                        device = device,
                        onBrightnessChange = onBrightnessChange,
                        onTriggerMotion = onTriggerMotion,
                        onReconnect = onReconnect,
                        onPingLatency = onPingLatency,
                        onEditDevice = onEditDevice,
                        onToggleNightMode = onToggleNightMode
                    )
                }
            }
        }
    }
}
