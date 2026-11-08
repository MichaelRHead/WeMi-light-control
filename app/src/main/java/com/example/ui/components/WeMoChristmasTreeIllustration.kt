package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Custom Canvas illustration of a Holiday / Christmas Tree with string lights, star topper, and stand.
 * Supports 3 distinct visual states:
 * 1. ON (`isReachable && isOn`): Lush emerald evergreen tiers, warm golden aura, glowing star topper,
 *    and multi-color illuminated fairy lights (gold, ruby, cyan, warm-white) + green ON corner badge.
 * 2. OFF — Accessible Standby (`isReachable && !isOn`): Full-opacity deep forest-slate evergreen tiers,
 *    unlit brass star topper, unlit frosted bulb silhouettes along the garland, warm amber standby LED on the tree stand,
 *    and dark-slate/amber "O" OFF corner badge.
 * 3. Disconnected (`!isReachable`): Muted gray silhouette with dashed red border and red "X" corner badge.
 */
@Composable
fun WeMoChristmasTreeIllustration(
    isReachable: Boolean,
    isOn: Boolean,
    modifier: Modifier = Modifier
) {
    val activeGlow = isReachable && isOn
    val canvasAlpha = if (isReachable) 1.0f else 0.55f

    Canvas(
        modifier = modifier
            .size(width = 62.dp, height = 78.dp)
            .alpha(canvasAlpha)
    ) {
        val w = size.width
        val h = size.height

        if (activeGlow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x55FACC15),
                        Color(0x3010B981),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h * 0.44f),
                    radius = w * 0.62f
                ),
                radius = w * 0.62f,
                center = Offset(w * 0.50f, h * 0.44f)
            )
        } else if (isReachable) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x1E64748B),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h * 0.48f),
                    radius = w * 0.50f
                ),
                radius = w * 0.50f,
                center = Offset(w * 0.50f, h * 0.48f)
            )
        }

        // Tree trunk & festive stand at base
        drawRoundRect(
            color = when {
                !isReachable -> Color(0xFF94A3B8)
                activeGlow -> Color(0xFF78350F)
                else -> Color(0xFF475569)
            },
            topLeft = Offset(w * 0.42f, h * 0.72f),
            size = Size(w * 0.16f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        drawRoundRect(
            color = when {
                !isReachable -> Color(0xFF64748B)
                activeGlow -> Color(0xFFB91C1C)
                else -> StandbySlateDark
            },
            topLeft = Offset(w * 0.28f, h * 0.83f),
            size = Size(w * 0.44f, h * 0.08f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        if (isReachable && !isOn) {
            // Standby amber pilot LED on the tree switch base
            drawCircle(
                color = StandbyAmberGlow,
                radius = w * 0.055f,
                center = Offset(w * 0.50f, h * 0.87f)
            )
            drawCircle(
                color = StandbyAmber,
                radius = w * 0.026f,
                center = Offset(w * 0.50f, h * 0.87f)
            )
        }

        val tierBrush = when {
            !isReachable -> Brush.verticalGradient(
                colors = listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8))
            )
            activeGlow -> Brush.verticalGradient(
                colors = listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF065F46))
            )
            else -> Brush.verticalGradient(
                colors = listOf(Color(0xFF1E3A2F), Color(0xFF132A22), Color(0xFF0F172A))
            )
        }
        val tierStrokeColor = when {
            !isReachable -> DisconnectedRed.copy(alpha = 0.70f)
            activeGlow -> Color(0xFF34D399)
            else -> StandbySlateLight
        }
        val strokeEffect = if (isReachable) null else PathEffect.dashPathEffect(floatArrayOf(w * 0.07f, w * 0.05f), 0f)

        // Bottom evergreen tier
        val bottomTier = Path().apply {
            moveTo(w * 0.50f, h * 0.42f)
            lineTo(w * 0.12f, h * 0.75f)
            lineTo(w * 0.88f, h * 0.75f)
            close()
        }
        drawPath(path = bottomTier, brush = tierBrush)
        drawPath(
            path = bottomTier,
            color = tierStrokeColor,
            style = Stroke(width = w * 0.022f, pathEffect = strokeEffect)
        )

        // Middle evergreen tier
        val midTier = Path().apply {
            moveTo(w * 0.50f, h * 0.27f)
            lineTo(w * 0.18f, h * 0.56f)
            lineTo(w * 0.82f, h * 0.56f)
            close()
        }
        drawPath(path = midTier, brush = tierBrush)
        drawPath(
            path = midTier,
            color = tierStrokeColor,
            style = Stroke(width = w * 0.022f, pathEffect = strokeEffect)
        )

        // Top evergreen tier
        val topTier = Path().apply {
            moveTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.25f, h * 0.39f)
            lineTo(w * 0.75f, h * 0.39f)
            close()
        }
        drawPath(path = topTier, brush = tierBrush)
        drawPath(
            path = topTier,
            color = tierStrokeColor,
            style = Stroke(width = w * 0.022f, pathEffect = strokeEffect)
        )

        // Garland wire sweeps across the 3 tiers
        val wireColor = when {
            !isReachable -> Color(0xFF94A3B8)
            activeGlow -> Color(0xFFA7F3D0).copy(alpha = 0.55f)
            else -> StandbySlateLight.copy(alpha = 0.75f)
        }
        drawLine(
            color = wireColor,
            start = Offset(w * 0.31f, h * 0.34f),
            end = Offset(w * 0.68f, h * 0.37f),
            strokeWidth = w * 0.018f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = wireColor,
            start = Offset(w * 0.69f, h * 0.48f),
            end = Offset(w * 0.26f, h * 0.53f),
            strokeWidth = w * 0.018f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = wireColor,
            start = Offset(w * 0.22f, h * 0.66f),
            end = Offset(w * 0.76f, h * 0.71f),
            strokeWidth = w * 0.018f,
            cap = StrokeCap.Round
        )

        // Star topper at peak of tree
        val starCenter = Offset(w * 0.50f, h * 0.12f)
        if (activeGlow) {
            drawCircle(
                color = Color(0x66FACC15),
                radius = w * 0.14f,
                center = starCenter
            )
        }
        val starColor = when {
            !isReachable -> Color(0xFF94A3B8)
            activeGlow -> Color(0xFFFACC15)
            else -> Color(0xFF64748B)
        }
        val starPath = Path().apply {
            val outerR = w * 0.095f
            val innerR = w * 0.042f
            for (i in 0 until 10) {
                val angleRad = Math.toRadians((i * 36.0) - 90.0)
                val r = if (i % 2 == 0) outerR else innerR
                val px = starCenter.x + (r * kotlin.math.cos(angleRad)).toFloat()
                val py = starCenter.y + (r * kotlin.math.sin(angleRad)).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(path = starPath, color = starColor)

        // String light bulbs positioned on the tree tiers
        val bulbPositions = listOf(
            Offset(w * 0.36f, h * 0.34f) to Color(0xFFFACC15), // Warm Gold
            Offset(w * 0.53f, h * 0.29f) to Color(0xFFEF4444), // Festive Red
            Offset(w * 0.63f, h * 0.36f) to Color(0xFF38BDF8), // Ice Cyan
            Offset(w * 0.32f, h * 0.51f) to Color(0xFFF43F5E), // Ruby Pink
            Offset(w * 0.49f, h * 0.47f) to Color(0xFFFDE047), // Candle Yellow
            Offset(w * 0.66f, h * 0.50f) to Color(0xFF22D3EE), // Electric Cyan
            Offset(w * 0.26f, h * 0.67f) to Color(0xFF38BDF8), // Ice Cyan
            Offset(w * 0.45f, h * 0.65f) to Color(0xFFEF4444), // Festive Red
            Offset(w * 0.61f, h * 0.70f) to Color(0xFFFACC15)  // Warm Gold
        )

        bulbPositions.forEach { (pos, litColor) ->
            if (activeGlow) {
                drawCircle(
                    color = litColor.copy(alpha = 0.45f),
                    radius = w * 0.068f,
                    center = pos
                )
                drawCircle(
                    color = litColor,
                    radius = w * 0.036f,
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.015f,
                    center = Offset(pos.x - w * 0.008f, pos.y - h * 0.006f)
                )
            } else if (isReachable) {
                // Accessible OFF: unlit frosted glass ornament/bulb caps with dark slate rim
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = w * 0.032f,
                    center = pos
                )
                drawCircle(
                    color = StandbySlateDark,
                    radius = w * 0.032f,
                    center = pos,
                    style = Stroke(width = w * 0.012f)
                )
            } else {
                drawCircle(
                    color = Color(0xFFCBD5E1),
                    radius = w * 0.026f,
                    center = pos
                )
            }
        }

        // Corner state badge (ON vs OFF-Standby vs Disconnected)
        drawDeviceStateCornerBadge(
            isReachable = isReachable,
            isOn = isOn,
            badgeCenter = Offset(w * 0.80f, h * 0.78f),
            badgeRadius = w * 0.15f
        )
    }
}
