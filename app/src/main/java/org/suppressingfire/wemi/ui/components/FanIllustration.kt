package org.suppressingfire.wemi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import org.suppressingfire.wemi.ui.theme.WemoGreenTop

/**
 * Custom Canvas illustration of a Multi-Blade Room / Ceiling Fan with protective grille and stand.
 * Supports 3 distinct visual states:
 * 1. ON (`isReachable && isOn`): Animated spinning aerodynamic blades, cyan airflow aura, illuminated cyan hub LED,
 *    and emerald ON corner badge.
 * 2. OFF — Accessible Standby (`isReachable && !isOn`): Full-opacity stationary brushed-steel blades inside a crisp
 *    dark-slate grille, warm amber standby LED in the center motor hub, and dark-slate/amber "O" OFF corner badge.
 * 3. Disconnected (`!isReachable`): Muted gray shroud with dashed red border and red "X" corner badge.
 */
@Composable
fun FanIllustration(
    isReachable: Boolean,
    isOn: Boolean,
    modifier: Modifier = Modifier
) {
    val activeSpin = isReachable && isOn
    val canvasAlpha = if (isReachable) 1.0f else 0.55f

    val infiniteTransition = rememberInfiniteTransition(label = "fanSpinTransition")
    val fanBladeAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fanBladeAngle"
    )
    val effectiveRotation = if (activeSpin) fanBladeAngle else 18f

    Canvas(
        modifier = modifier
            .size(width = 62.dp, height = 78.dp)
            .alpha(canvasAlpha)
    ) {
        val w = size.width
        val h = size.height
        val fanCenter = Offset(w * 0.50f, h * 0.41f)
        val shroudRadius = w * 0.36f

        if (activeSpin) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x5022D3EE),
                        Color(0x220284C7),
                        Color.Transparent
                    ),
                    center = fanCenter,
                    radius = w * 0.54f
                ),
                radius = w * 0.54f,
                center = fanCenter
            )
        }

        // Pedestal neck and base
        drawRoundRect(
            color = if (isReachable) StandbySlateMid else Color(0xFF94A3B8),
            topLeft = Offset(w * 0.44f, h * 0.66f),
            size = Size(w * 0.12f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        drawRoundRect(
            color = if (isReachable) StandbySlateDark else Color(0xFF94A3B8),
            topLeft = Offset(w * 0.24f, h * 0.80f),
            size = Size(w * 0.52f, h * 0.09f),
            cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
        )

        // Fan housing backdrop circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = when {
                    !isReachable -> listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                    activeSpin -> listOf(Color(0xFF0F2942), Color(0xFF082F49))
                    else -> listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                },
                center = fanCenter,
                radius = shroudRadius
            ),
            radius = shroudRadius,
            center = fanCenter
        )

        // Concentric inner safety grille ring
        drawCircle(
            color = if (activeSpin) Color(0xFF38BDF8).copy(alpha = 0.28f) else Color(0xFF475569).copy(alpha = 0.45f),
            radius = shroudRadius * 0.68f,
            center = fanCenter,
            style = Stroke(width = w * 0.015f)
        )

        // 4 aerodynamic fan blades (spinning when ON, stationary when OFF)
        val bladeColor = when {
            !isReachable -> Color(0xFFCBD5E1)
            activeSpin -> Color(0xFF67E8F9)
            else -> Color(0xFF94A3B8)
        }
        for (i in 0 until 4) {
            rotate(degrees = effectiveRotation + (i * 90f), pivot = fanCenter) {
                drawOval(
                    color = bladeColor,
                    topLeft = Offset(fanCenter.x - w * 0.065f, fanCenter.y - shroudRadius * 0.82f),
                    size = Size(w * 0.13f, shroudRadius * 0.68f)
                )
            }
        }

        // Outer fan shroud bezel
        drawCircle(
            color = when {
                !isReachable -> DisconnectedRed.copy(alpha = 0.70f)
                activeSpin -> WemoGreenTop
                else -> StandbySlateLight
            },
            radius = shroudRadius,
            center = fanCenter,
            style = Stroke(
                width = w * 0.038f,
                pathEffect = if (isReachable) null else PathEffect.dashPathEffect(floatArrayOf(w * 0.08f, w * 0.06f), 0f)
            )
        )

        // Center motor hub + status pilot LED
        drawCircle(
            color = if (isReachable) StandbySlateDark else Color(0xFF94A3B8),
            radius = w * 0.095f,
            center = fanCenter
        )
        when {
            activeSpin -> {
                drawCircle(
                    color = WemoGreenTop,
                    radius = w * 0.045f,
                    center = fanCenter
                )
            }
            isReachable -> {
                drawCircle(
                    color = StandbySlateLight,
                    radius = w * 0.036f,
                    center = fanCenter
                )
            }
            else -> {
                drawCircle(
                    color = DisconnectedRed,
                    radius = w * 0.036f,
                    center = fanCenter
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
