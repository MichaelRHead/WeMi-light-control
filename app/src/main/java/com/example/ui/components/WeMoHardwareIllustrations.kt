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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoMotionCyan
import com.example.ui.theme.WemoRingInactive

/**
 * High-detail Canvas illustration of the classic Belkin WeMo Smart Plug / Light Switch
 * seen on "Christmas Tree Living", "Christmas Candle", and "Christmas Tree Kitchen".
 */
@Composable
fun WeMoSmartPlugIllustration(
    isReachable: Boolean,
    isOn: Boolean,
    modifier: Modifier = Modifier
) {
    val alphaVal = if (isReachable) 1f else 0.35f
    Canvas(
        modifier = modifier
            .size(width = 58.dp, height = 74.dp)
            .alpha(alphaVal)
    ) {
        val w = size.width
        val h = size.height

        // Drop shadow at bottom
        drawOval(
            color = Color(0x28000000),
            topLeft = Offset(w * 0.14f, h * 0.88f),
            size = Size(w * 0.72f, h * 0.09f)
        )

        // Outer rounded switch body
        val bodyLeft = w * 0.12f
        val bodyTop = h * 0.06f
        val bodyW = w * 0.76f
        val bodyH = h * 0.86f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFE7EAEE),
                    Color(0xFFF9FAFB),
                    Color(0xFFD5D9DF)
                ),
                startX = bodyLeft,
                endX = bodyLeft + bodyW
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
        )

        // Subtle inner face highlight
        val faceInset = w * 0.045f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFECEFF3)),
                startY = bodyTop + faceInset,
                endY = bodyTop + bodyH - faceInset
            ),
            topLeft = Offset(bodyLeft + faceInset, bodyTop + faceInset),
            size = Size(bodyW - faceInset * 2, bodyH - faceInset * 2),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
        )

        // Top small power/Wi-Fi button circle & status dot
        val topCenter = Offset(w * 0.5f, h * 0.23f)
        drawCircle(
            color = Color(0xFFD8DCE2),
            radius = w * 0.085f,
            center = topCenter,
            style = Stroke(width = w * 0.022f)
        )
        drawCircle(
            color = if (isOn && isReachable) WemoGreenPrimary else Color(0xFFA0A7B0),
            radius = w * 0.032f,
            center = Offset(w * 0.5f, h * 0.125f)
        )

        // 7 vertical vent slots across the middle
        val slotCount = 7
        val slotTotalW = bodyW * 0.52f
        val slotStartX = (w - slotTotalW) / 2f
        val slotStep = slotTotalW / (slotCount - 1)
        for (i in 0 until slotCount) {
            val sx = slotStartX + i * slotStep
            drawLine(
                color = Color(0xFF7B828B),
                start = Offset(sx, h * 0.42f),
                end = Offset(sx, h * 0.47f),
                strokeWidth = w * 0.026f,
                cap = StrokeCap.Round
            )
        }

        // Recessed NEMA 5-15 outlet face at bottom
        val outletLeft = w * 0.25f
        val outletTop = h * 0.55f
        val outletW = w * 0.50f
        val outletH = h * 0.29f
        drawRoundRect(
            color = Color(0xFFE3E7EC),
            topLeft = Offset(outletLeft, outletTop),
            size = Size(outletW, outletH),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        drawRoundRect(
            color = Color(0xFFB8BFC7),
            topLeft = Offset(outletLeft, outletTop),
            size = Size(outletW, outletH),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = Stroke(width = w * 0.02f)
        )

        // Left neutral blade slot (slightly taller)
        drawLine(
            color = Color(0xFF42474D),
            start = Offset(w * 0.38f, h * 0.61f),
            end = Offset(w * 0.38f, h * 0.70f),
            strokeWidth = w * 0.042f,
            cap = StrokeCap.Square
        )
        // Right hot blade slot
        drawLine(
            color = Color(0xFF42474D),
            start = Offset(w * 0.62f, h * 0.62f),
            end = Offset(w * 0.62f, h * 0.695f),
            strokeWidth = w * 0.042f,
            cap = StrokeCap.Square
        )
        // Bottom U-shaped ground hole
        drawCircle(
            color = Color(0xFF42474D),
            radius = w * 0.042f,
            center = Offset(w * 0.50f, h * 0.77f)
        )
    }
}

/**
 * High-detail Canvas illustration of the Belkin WeMo Motion Sensor pedestal
 * seen on "Bedroom Light Motion".
 */
@Composable
fun WeMoMotionSensorIllustration(
    isReachable: Boolean,
    modifier: Modifier = Modifier
) {
    val alphaVal = if (isReachable) 1f else 0.35f
    Canvas(
        modifier = modifier
            .size(width = 58.dp, height = 74.dp)
            .alpha(alphaVal)
    ) {
        val w = size.width
        val h = size.height

        // Base shadow
        drawOval(
            color = Color(0x33000000),
            topLeft = Offset(w * 0.10f, h * 0.85f),
            size = Size(w * 0.80f, h * 0.08f)
        )

        // Tapered cylinder body path
        val bodyPath = Path().apply {
            moveTo(w * 0.20f, h * 0.22f)
            lineTo(w * 0.80f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.84f)
            quadraticTo(w * 0.50f, h * 0.90f, w * 0.14f, h * 0.84f)
            close()
        }
        drawPath(
            path = bodyPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFE0E4E9),
                    Color(0xFFFDFEFF),
                    Color(0xFFCFD5DC)
                ),
                startX = w * 0.14f,
                endX = w * 0.86f
            )
        )

        // Concave top dish ellipse
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFC5CCD4), Color(0xFFF4F6F8)),
                startY = h * 0.13f,
                endY = h * 0.30f
            ),
            topLeft = Offset(w * 0.20f, h * 0.14f),
            size = Size(w * 0.60f, h * 0.16f)
        )
        drawOval(
            color = Color(0xFFE8ECF0),
            topLeft = Offset(w * 0.20f, h * 0.14f),
            size = Size(w * 0.60f, h * 0.16f),
            style = Stroke(width = w * 0.025f)
        )

        // Glowing cyan motion indicator dot in lower half
        val ledCenter = Offset(w * 0.50f, h * 0.63f)
        drawCircle(
            color = WemoMotionCyan.copy(alpha = 0.28f),
            radius = w * 0.085f,
            center = ledCenter
        )
        drawCircle(
            color = WemoMotionCyan,
            radius = w * 0.036f,
            center = ledCenter
        )

        // Dark base foot strip
        drawLine(
            color = Color(0xFF6B727B),
            start = Offset(w * 0.16f, h * 0.86f),
            end = Offset(w * 0.84f, h * 0.86f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * High-detail Canvas illustration of the WeMo Smart LED Lightbulb (A19 profile)
 * seen on "Hall Lightbulb", "Lightbulb Landing", "Lightbulb Ensuite", and "Lightbulb Bedroom".
 */
@Composable
fun WeMoLightbulbIllustration(
    isReachable: Boolean,
    isOn: Boolean,
    brightness: Int,
    modifier: Modifier = Modifier
) {
    val alphaVal = if (isReachable) 1f else 0.32f
    Canvas(
        modifier = modifier
            .size(width = 58.dp, height = 74.dp)
            .alpha(alphaVal)
    ) {
        val w = size.width
        val h = size.height

        // Screw base (E26 threads at bottom)
        val baseLeft = w * 0.36f
        val baseTop = h * 0.72f
        val baseW = w * 0.28f
        val baseH = h * 0.16f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF8C939B), Color(0xFFDADEE4), Color(0xFF727880)),
                startX = baseLeft,
                endX = baseLeft + baseW
            ),
            topLeft = Offset(baseLeft, baseTop),
            size = Size(baseW, baseH),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        // Thread ridges
        for (i in 1..3) {
            val ty = baseTop + (baseH * i / 4f)
            drawLine(
                color = Color(0xFF595F66),
                start = Offset(baseLeft, ty),
                end = Offset(baseLeft + baseW, ty),
                strokeWidth = w * 0.022f
            )
        }
        // Bottom metallic contact point
        drawOval(
            color = Color(0xFF2B2E33),
            topLeft = Offset(w * 0.41f, h * 0.86f),
            size = Size(w * 0.18f, h * 0.05f)
        )

        // Lower tapered ceramic heat-sink neck
        val neckPath = Path().apply {
            moveTo(w * 0.25f, h * 0.45f)
            lineTo(w * 0.75f, h * 0.45f)
            lineTo(w * 0.65f, h * 0.73f)
            lineTo(w * 0.35f, h * 0.73f)
            close()
        }
        drawPath(
            path = neckPath,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFDCE0E6), Color(0xFFFAFBFD), Color(0xFFC8CED6)),
                startX = w * 0.25f,
                endX = w * 0.75f
            )
        )

        // Upper frosted glass bulb dome
        val domeRect = Rect(
            left = w * 0.19f,
            top = h * 0.08f,
            right = w * 0.81f,
            bottom = h * 0.60f
        )
        val glowFactor = if (isReachable && isOn) (brightness / 100f).coerceIn(0.15f, 1f) else 0f
        drawOval(
            brush = Brush.radialGradient(
                colors = if (glowFactor > 0f) {
                    listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF6F9F4),
                        Color(0xFFDDE3E9)
                    )
                } else {
                    listOf(
                        Color(0xFFF7F9FA),
                        Color(0xFFE7EBEF),
                        Color(0xFFD2D8DF)
                    )
                },
                center = Offset(w * 0.45f, h * 0.28f),
                radius = w * 0.36f
            ),
            topLeft = domeRect.topLeft,
            size = domeRect.size
        )

        // Subtle equator seam between glass dome and ceramic neck
        drawLine(
            color = Color(0xFFCFD5DC),
            start = Offset(w * 0.24f, h * 0.45f),
            end = Offset(w * 0.76f, h * 0.45f),
            strokeWidth = w * 0.02f
        )
    }
}

/**
 * The signature circular power button on the right side of every switch/lightbulb row in the screenshot.
 * Features the outer ring and classic IEC power glyph inside.
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
        targetValue = if (isOn) WemoGreenPrimary else WemoRingInactive,
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
        Canvas(modifier = Modifier.size(60.dp)) {
            val strokeW = 3.2.dp.toPx()
            val radius = (size.minDimension - strokeW) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Subtle soft tint inside when ON
            if (isOn) {
                drawCircle(
                    color = WemoGreenPrimary.copy(alpha = 0.06f),
                    radius = radius,
                    center = center
                )
            }

            // Outer circle ring
            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeW)
            )

            if (isToggling) {
                // Animated arc overlay while SOAP request is in flight
                drawArc(
                    color = WemoGreenPrimary,
                    startAngle = spinAngle,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(strokeW / 2f, strokeW / 2f),
                    size = Size(size.width - strokeW, size.height - strokeW),
                    style = Stroke(width = strokeW * 1.25f, cap = StrokeCap.Round)
                )
            }

            // Inner IEC power icon: broken circle arc + top vertical stem
            val innerRadius = radius * 0.42f
            val innerStroke = 3.0.dp.toPx()
            drawArc(
                color = ringColor,
                startAngle = -52f,
                sweepAngle = 284f,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius + 1.5.dp.toPx()),
                size = Size(innerRadius * 2f, innerRadius * 2f),
                style = Stroke(width = innerStroke, cap = StrokeCap.Round)
            )

            // Vertical power stem at top
            drawLine(
                color = ringColor,
                start = Offset(center.x, center.y - innerRadius - 2.5.dp.toPx()),
                end = Offset(center.x, center.y + 1.dp.toPx()),
                strokeWidth = innerStroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Custom Canvas icon for the "Devices" bottom tab (horizontal 2-prong electrical plug silhouette
 * matching the screenshot).
 */
@Composable
fun WeMoPlugTabIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(30.dp)) {
        val w = size.width
        val h = size.height

        // Left cord stub
        drawLine(
            color = tint,
            start = Offset(w * 0.08f, h * 0.50f),
            end = Offset(w * 0.25f, h * 0.50f),
            strokeWidth = h * 0.10f,
            cap = StrokeCap.Round
        )

        // D-shaped plug body
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

        // Two horizontal metal prongs extending right
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
