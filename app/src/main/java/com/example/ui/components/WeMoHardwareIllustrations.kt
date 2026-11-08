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
import com.example.data.WeMoDeviceCategory
import com.example.ui.theme.WemoGreenBottom
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenTop

internal val StandbySlateDark = Color(0xFF1E293B)
internal val StandbySlateMid = Color(0xFF334155)
internal val StandbySlateLight = Color(0xFF64748B)
internal val StandbyAmber = Color(0xFFF59E0B)
internal val StandbyAmberGlow = Color(0x38F59E0B)
internal val DisconnectedRed = Color(0xFFDC2626)
internal val OnlineGreenBadge = Color(0xFF059669)

/**
 * Draws a distinct corner badge on hardware illustrations so the user can immediately distinguish:
 * 1) Reachable & ON: Emerald/Cyan circle with vertical "I" power-on bar
 * 2) Reachable & OFF (Standby): Dark Slate circle with Warm Amber ring and open "O" standby symbol
 * 3) Disconnected / Offline: Red circle with white "X" offline cross
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
            // Disconnected: Red circle with crisp white "X"
            drawCircle(
                color = DisconnectedRed,
                radius = badgeRadius,
                center = badgeCenter
            )
            val d = badgeRadius * 0.42f
            val stroke = badgeRadius * 0.24f
            drawLine(
                color = Color.White,
                start = Offset(badgeCenter.x - d, badgeCenter.y - d),
                end = Offset(badgeCenter.x + d, badgeCenter.y + d),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(badgeCenter.x + d, badgeCenter.y - d),
                end = Offset(badgeCenter.x - d, badgeCenter.y + d),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        isOn -> {
            // Reachable & ON: Emerald-Teal badge with active IEC "I" power stem + arc
            drawCircle(
                color = OnlineGreenBadge,
                radius = badgeRadius,
                center = badgeCenter
            )
            val r = badgeRadius * 0.48f
            val stroke = badgeRadius * 0.22f
            drawArc(
                color = Color.White,
                startAngle = -50f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(badgeCenter.x - r, badgeCenter.y - r + badgeRadius * 0.06f),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawLine(
                color = Color.White,
                start = Offset(badgeCenter.x, badgeCenter.y - r - badgeRadius * 0.10f),
                end = Offset(badgeCenter.x, badgeCenter.y + badgeRadius * 0.04f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        else -> {
            // Reachable & OFF (Standby): Dark Slate badge with Warm Amber border & IEC "O" (Zero/Off) circle
            drawCircle(
                color = StandbySlateDark,
                radius = badgeRadius,
                center = badgeCenter
            )
            drawCircle(
                color = StandbyAmber,
                radius = badgeRadius * 0.92f,
                center = badgeCenter,
                style = Stroke(width = badgeRadius * 0.16f)
            )
            // Crisp IEC "O" (binary 0 = OFF) open standby ring in center
            drawCircle(
                color = Color.White,
                radius = badgeRadius * 0.40f,
                center = badgeCenter,
                style = Stroke(width = badgeRadius * 0.22f)
            )
        }
    }
}

/**
 * Renders the appropriate hardware/category illustration based on the device's semantic
 * [WeMoDeviceCategory] (classified from the switch name and UPnP type):
 * - [WeMoDeviceCategory.CHRISTMAS_TREE] -> [WeMoChristmasTreeIllustration]
 * - [WeMoDeviceCategory.FAN] -> [WeMoFanIllustration]
 * - [WeMoDeviceCategory.LIGHT] -> [WeMoLightbulbIllustration]
 * - [WeMoDeviceCategory.MOTION_SENSOR] -> [WeMoMotionSensorIllustration]
 * - [WeMoDeviceCategory.SWITCH_PLUG] -> [WeMoSmartPlugIllustration]
 */
@Composable
fun WeMoDeviceCategoryIllustration(
    category: WeMoDeviceCategory,
    isReachable: Boolean,
    isOn: Boolean,
    brightness: Int = 100,
    modifier: Modifier = Modifier
) {
    when (category) {
        WeMoDeviceCategory.CHRISTMAS_TREE -> WeMoChristmasTreeIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
        WeMoDeviceCategory.FAN -> WeMoFanIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
        WeMoDeviceCategory.LIGHT -> WeMoLightbulbIllustration(
            isReachable = isReachable,
            isOn = isOn,
            brightness = brightness,
            modifier = modifier
        )
        WeMoDeviceCategory.MOTION_SENSOR -> WeMoMotionSensorIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
        WeMoDeviceCategory.SWITCH_PLUG -> WeMoSmartPlugIllustration(
            isReachable = isReachable,
            isOn = isOn,
            modifier = modifier
        )
    }
}

/**
 * WeMi Power Ring Toggle Button with distinct visual iconography for ON vs OFF (Accessible Standby):
 * - When ON: Electric Cyan -> Deep Sapphire gradient ring, soft radial luminescence, and IEC "I/O" icon with active top vertical stem.
 * - When OFF (Accessible): Crisp Slate-Metallic ring with a subtle recessed fill, a Warm Amber Standby pilot dot at the top,
 *   and a clean IEC "O" (binary 0 = OFF) closed standby circle in the center.
 */
@Composable
fun WeMoPowerRingButton(
    isOn: Boolean,
    isToggling: Boolean,
    deviceName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor by animateColorAsState(
        targetValue = if (isOn) WemoGreenPrimary else StandbySlateLight,
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
                // Accessible OFF Standby state: crisp recessed slate fill + metallic outer ring
                drawCircle(
                    color = Color(0xFFF1F5F9),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = ringColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeW)
                )
                // Warm amber standby locator dot at top of ring (indicates switch is powered & reachable, currently OFF)
                val topDotCenter = Offset(center.x, center.y - radius + strokeW * 1.45f)
                drawCircle(
                    color = StandbyAmberGlow,
                    radius = 4.5.dp.toPx(),
                    center = topDotCenter
                )
                drawCircle(
                    color = StandbyAmber,
                    radius = 2.4.dp.toPx(),
                    center = topDotCenter
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

            val innerRadius = radius * 0.40f
            val innerStroke = 3.0.dp.toPx()
            if (isOn) {
                // ON glyph: IEC broken circle arc + active vertical "I" stem
                drawArc(
                    color = WemoGreenDark,
                    startAngle = -52f,
                    sweepAngle = 284f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius + 1.5.dp.toPx()),
                    size = Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = innerStroke, cap = StrokeCap.Round)
                )
                drawLine(
                    color = WemoGreenDark,
                    start = Offset(center.x, center.y - innerRadius - 2.5.dp.toPx()),
                    end = Offset(center.x, center.y + 1.dp.toPx()),
                    strokeWidth = innerStroke,
                    cap = StrokeCap.Round
                )
            } else {
                // OFF (Accessible Standby) glyph: Complete IEC "O" circle (binary 0 = OFF) in crisp dark slate
                drawCircle(
                    color = StandbySlateMid,
                    radius = innerRadius * 0.92f,
                    center = Offset(center.x, center.y + 1.dp.toPx()),
                    style = Stroke(width = innerStroke)
                )
            }
        }
    }
}

@Composable
fun WeMoPlugTabIcon(
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
