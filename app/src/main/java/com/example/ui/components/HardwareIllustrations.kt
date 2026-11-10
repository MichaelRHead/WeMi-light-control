package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.DeviceCategory
import com.example.ui.theme.WemoGreenBottom
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenTop

internal val StandbySlateDark = Color(0xFF1F232B)
internal val StandbySlateMid = Color(0xFF2D333F)
internal val StandbySlateLight = Color(0xFF3E4656)
internal val PowerOffDarkGray = Color(0xFF23272F)
internal val PowerOffIconDarkGray = Color(0xFF181B20)
internal val DisconnectedRed = Color(0xFFDC2626)
internal val OnlineGreenBadge = Color(0xFF059669)

/**
 * Draws the classic IEC "I/O" power symbol (broken circle arc + top vertical stem)
 * used for both Power ON and Power OFF states (distinguished by color).
 */
internal fun DrawScope.drawIecPowerSymbol(
    color: Color,
    center: Offset,
    radius: Float,
    strokeWidth: Float
) {
    drawArc(
        color = color,
        startAngle = -52f,
        sweepAngle = 284f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius + radius * 0.08f),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius - radius * 0.16f),
        end = Offset(center.x, center.y + radius * 0.06f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/**
 * Draws a disconnected / unavailable symbol (broken link / signal arc with diagonal disconnect slash).
 */
internal fun DrawScope.drawDisconnectedSymbol(
    color: Color,
    slashColor: Color,
    center: Offset,
    radius: Float,
    strokeWidth: Float
) {
    // Upper and lower broken connection arcs
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    drawArc(
        color = color,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    // Diagonal disconnect slash across the center
    val d = radius * 0.92f
    drawLine(
        color = slashColor,
        start = Offset(center.x - d, center.y - d),
        end = Offset(center.x + d, center.y + d),
        strokeWidth = strokeWidth * 1.05f,
        cap = StrokeCap.Round
    )
}

/**
 * Draws a distinct corner badge on hardware illustrations so the user can immediately distinguish:
 * 1) Reachable & ON: Emerald/Cyan circle with white IEC power symbol
 * 2) Reachable & OFF: Very dark gray circle with the same white IEC power symbol
 * 3) Disconnected / Unavailable: Red circle with white disconnected slash icon
 */
internal fun DrawScope.drawDeviceStateCornerBadge(
    isReachable: Boolean,
    isOn: Boolean,
    badgeCenter: Offset,
    badgeRadius: Float
) {
    // White separator halo around the corner badge
    drawCircle(
        color = Color.White,
        radius = badgeRadius * 1.18f,
        center = badgeCenter
    )

    when {
        !isReachable -> {
            // Unavailable / Disconnected: Red circle with white disconnected slash icon
            drawCircle(
                color = DisconnectedRed,
                radius = badgeRadius,
                center = badgeCenter
            )
            drawDisconnectedSymbol(
                color = Color.White,
                slashColor = Color.White,
                center = badgeCenter,
                radius = badgeRadius * 0.48f,
                strokeWidth = badgeRadius * 0.20f
            )
        }

        isOn -> {
            // Reachable & ON: Emerald-Teal badge with white IEC power symbol
            drawCircle(
                color = OnlineGreenBadge,
                radius = badgeRadius,
                center = badgeCenter
            )
            drawIecPowerSymbol(
                color = Color.White,
                center = badgeCenter,
                radius = badgeRadius * 0.48f,
                strokeWidth = badgeRadius * 0.22f
            )
        }

        else -> {
            // Reachable & OFF: Very dark gray badge with the same white IEC power symbol
            drawCircle(
                color = PowerOffDarkGray,
                radius = badgeRadius,
                center = badgeCenter
            )
            drawIecPowerSymbol(
                color = Color.White.copy(alpha = 0.92f),
                center = badgeCenter,
                radius = badgeRadius * 0.48f,
                strokeWidth = badgeRadius * 0.22f
            )
        }
    }
}

/**
 * Renders the appropriate hardware/category illustration based on the device's semantic
 * [DeviceCategory] (classified from the switch name and UPnP type):
 * - [DeviceCategory.CHRISTMAS_TREE] -> [ChristmasTreeIllustration]
 * - [DeviceCategory.FAN] -> [FanIllustration]
 * - [DeviceCategory.LIGHT] -> [LightbulbIllustration]
 * - [DeviceCategory.MOTION_SENSOR] -> [MotionSensorIllustration]
 * - [DeviceCategory.SWITCH_PLUG] -> [SmartPlugIllustration]
 */
@Composable
fun DeviceCategoryIllustration(
    category: DeviceCategory,
    isReachable: Boolean,
    isOn: Boolean,
    brightness: Int = 100,
    modifier: Modifier = Modifier
) {
    when (category) {
        DeviceCategory.CHRISTMAS_TREE -> ChristmasTreeIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
        DeviceCategory.FAN -> FanIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
        DeviceCategory.LIGHT -> LightbulbIllustration(
            isReachable = isReachable,
            isOn = isOn,
            brightness = brightness,
            modifier = modifier
        )
        DeviceCategory.MOTION_SENSOR -> MotionSensorIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
        DeviceCategory.SWITCH_PLUG -> SmartPlugIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
    }
}

/**
 * WeMi Power Ring Toggle Button:
 * - Both ON and OFF states use the classic IEC power symbol (broken circle arc + top vertical stem).
 * - When ON: Electric Cyan -> Deep Sapphire gradient ring, soft radial glow, and teal-green power symbol.
 * - When OFF: Very dark gray ring (`PowerOffDarkGray`) and very dark gray power symbol (`PowerOffIconDarkGray`).
 */
@Composable
fun PowerRingButton(
    isOn: Boolean,
    isToggling: Boolean,
    deviceName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor by animateColorAsState(
        targetValue = if (isOn) WemoGreenPrimary else PowerOffDarkGray,
        animationSpec = tween(durationMillis = 220),
        label = "powerRingColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "toggleSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(64.dp)
            .semantics {
                role = Role.Switch
                contentDescription = "Toggle power for $deviceName, currently ${if (isOn) "On" else "Off"}"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(58.dp)) {
            val strokeW = 3.4.dp.toPx()
            val radius = (size.minDimension - strokeW) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            if (isOn) {
                // Soft cyan-teal radial inner glow when ON
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WemoGreenTop.copy(alpha = 0.20f),
                            WemoGreenPrimary.copy(alpha = 0.06f)
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                // Gradient outer ring
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(WemoGreenTop, WemoGreenPrimary, WemoGreenBottom)
                    ),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeW)
                )
            } else {
                // Power OFF state: subtle neutral surface + very dark gray outer ring (same layout as ON)
                drawCircle(
                    color = Color(0xFFEAEEF4),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = ringColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeW)
                )
            }

            if (isToggling) {
                drawArc(
                    color = WemoGreenTop,
                    startAngle = spinAngle,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(strokeW / 2f, strokeW / 2f),
                    size = Size(size.width - strokeW, size.height - strokeW),
                    style = Stroke(width = strokeW * 1.3f, cap = StrokeCap.Round)
                )
            }

            // Same IEC power symbol for both ON and OFF, differentiated by color (Teal vs Very Dark Gray)
            drawIecPowerSymbol(
                color = if (isOn) WemoGreenDark else PowerOffIconDarkGray,
                center = center,
                radius = radius * 0.40f,
                strokeWidth = 3.0.dp.toPx()
            )
        }
    }
}

/**
 * 64dp circular Disconnected / Unavailable button displayed in place of the Power Ring when a switch
 * is unreachable on the LAN (`!device.isReachable`). Tapping triggers a 2-stage reconnect probe.
 */
@Composable
fun DisconnectedRingButton(
    deviceName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(64.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Reconnect $deviceName"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(58.dp)) {
            val strokeW = 3.0.dp.toPx()
            val radius = (size.minDimension - strokeW) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                color = Color(0xFFFEF2F2),
                radius = radius,
                center = center
            )
            drawCircle(
                color = DisconnectedRed.copy(alpha = 0.65f),
                radius = radius,
                center = center,
                style = Stroke(
                    width = strokeW,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                        0f
                    )
                )
            )
            drawDisconnectedSymbol(
                color = Color(0xFF64748B),
                slashColor = DisconnectedRed,
                center = center,
                radius = radius * 0.40f,
                strokeWidth = 2.8.dp.toPx()
            )
        }
    }
}

@Composable
fun PlugTabIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(30.dp)) {
        val w = size.width
        val h = size.height

        drawLine(
            color = tint,
            start = Offset(w * 0.08f, h * 0.50f),
            end = Offset(w * 0.25f, h * 0.50f),
            strokeWidth = h * 0.10f,
            cap = StrokeCap.Round
        )

        val plugPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(w * 0.24f, h * 0.23f, w * 0.66f, h * 0.77f),
                    topLeft = CornerRadius(w * 0.20f, w * 0.20f),
                    bottomLeft = CornerRadius(w * 0.20f, w * 0.20f),
                    topRight = CornerRadius(w * 0.04f, w * 0.04f),
                    bottomRight = CornerRadius(w * 0.04f, w * 0.04f)
                )
            )
        }
        drawPath(path = plugPath, color = tint)

        drawLine(
            color = tint,
            start = Offset(w * 0.66f, h * 0.37f),
            end = Offset(w * 0.90f, h * 0.37f),
            strokeWidth = h * 0.08f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.66f, h * 0.63f),
            end = Offset(w * 0.90f, h * 0.63f),
            strokeWidth = h * 0.08f,
            cap = StrokeCap.Round
        )
    }
}
