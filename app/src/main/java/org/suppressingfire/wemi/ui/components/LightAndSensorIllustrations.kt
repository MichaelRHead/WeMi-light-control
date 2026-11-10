package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.suppressingfire.wemi.ui.theme.WemoMotionCyan

/**
 * Redesigned WeMi Motion Radar Sensor illustration with deep sapphire/teal lens aperture
 * and glowing electric cyan radar rings.
 */
@Composable
fun MotionSensorIllustration(
    isReachable: Boolean,
    isOn: Boolean = true,
    modifier: Modifier = Modifier
) {
    val alphaVal = if (isReachable) 1f else 0.55f
    Canvas(
        modifier = modifier
            .size(width = 58.dp, height = 74.dp)
            .alpha(alphaVal)
    ) {
        val w = size.width
        val h = size.height

        // Base shadow
        drawOval(
            color = Color(0x2E0E7490),
            topLeft = Offset(w * 0.12f, h * 0.85f),
            size = Size(w * 0.76f, h * 0.08f)
        )

        // Sleek rounded sensor pod housing
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
                startY = h * 0.14f,
                endY = h * 0.86f
            ),
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.28f, w * 0.28f)
        )

        // Outer teal trim ring (or dashed red when offline)
        drawRoundRect(
            color = if (isReachable) Color(0xFF0891B2).copy(alpha = 0.45f) else DisconnectedRed.copy(alpha = 0.7f),
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.28f, w * 0.28f),
            style = Stroke(
                width = w * 0.028f,
                pathEffect = if (isReachable) null else PathEffect.dashPathEffect(floatArrayOf(w * 0.08f, w * 0.06f), 0f)
            )
        )

        // Deep sapphire/teal infrared Fresnel dome lens in center
        val lensCenter = Offset(w * 0.50f, h * 0.46f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (isReachable) {
                    listOf(
                        Color(0xFF22D3EE),
                        Color(0xFF0891B2),
                        Color(0xFF0F4C81)
                    )
                } else {
                    listOf(
                        Color(0xFF94A3B8),
                        Color(0xFF64748B),
                        Color(0xFF475569)
                    )
                },
                center = Offset(lensCenter.x - w * 0.04f, lensCenter.y - h * 0.03f),
                radius = w * 0.22f
            ),
            radius = w * 0.20f,
            center = lensCenter
        )

        // Concentric radar wave arcs inside lens
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = w * 0.12f,
            center = lensCenter,
            style = Stroke(width = w * 0.02f)
        )
        drawCircle(
            color = if (isReachable) WemoMotionCyan else Color(0xFFCBD5E1),
            radius = w * 0.045f,
            center = lensCenter
        )

        // Bottom cyan status pill
        drawLine(
            color = if (isReachable) Color(0xFF0891B2) else Color(0xFF94A3B8),
            start = Offset(w * 0.36f, h * 0.74f),
            end = Offset(w * 0.64f, h * 0.74f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )

        if (!isReachable) {
            drawDeviceStateCornerBadge(
                isReachable = false,
                isOn = false,
                badgeCenter = Offset(w * 0.80f, h * 0.78f),
                badgeRadius = w * 0.15f
            )
        }
    }
}

/**
 * WeMi Smart Dimmable Bulb / Dimmer illustration with three distinct states:
 * - Reachable & ON: Luminous cyan-white globe with radial glow, lit filament arc, and emerald ON badge.
 * - Reachable & OFF: Crisp smoke-slate glass globe (full opacity) with visible unlit filament structure,
 *   warm amber standby neck collar, and dark-slate/amber OFF standby badge.
 * - Disconnected: Faded silhouette with dashed red dome outline and red "X" offline badge.
 */
@Composable
fun LightbulbIllustration(
    isReachable: Boolean,
    isOn: Boolean,
    brightness: Int,
    modifier: Modifier = Modifier
) {
    val alphaVal = if (isReachable) 1f else 0.55f
    Canvas(
        modifier = modifier
            .size(width = 58.dp, height = 74.dp)
            .alpha(alphaVal)
    ) {
        val w = size.width
        val h = size.height
        val glowFactor = if (isReachable && isOn) (brightness / 100f).coerceIn(0.2f, 1f) else 0f

        // Ambient cyan halo behind the bulb when ON
        if (glowFactor > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF22D3EE).copy(alpha = 0.32f * glowFactor),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h * 0.32f),
                    radius = w * 0.48f
                ),
                radius = w * 0.48f,
                center = Offset(w * 0.50f, h * 0.32f)
            )
        }

        // Brushed titanium E26 screw base
        val baseLeft = w * 0.36f
        val baseTop = h * 0.72f
        val baseW = w * 0.28f
        val baseH = h * 0.16f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF64748B), Color(0xFFCBD5E1), Color(0xFF475569)),
                startX = baseLeft,
                endX = baseLeft + baseW
            ),
            topLeft = Offset(baseLeft, baseTop),
            size = Size(baseW, baseH),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        for (i in 1..3) {
            val ty = baseTop + (baseH * i / 4f)
            drawLine(
                color = Color(0xFF334155),
                start = Offset(baseLeft, ty),
                end = Offset(baseLeft + baseW, ty),
                strokeWidth = w * 0.022f
            )
        }
        drawOval(
            color = Color(0xFF0F172A),
            topLeft = Offset(w * 0.41f, h * 0.86f),
            size = Size(w * 0.18f, h * 0.05f)
        )

        // Tapered heat-sink neck
        val neckPath = Path().apply {
            moveTo(w * 0.24f, h * 0.45f)
            lineTo(w * 0.76f, h * 0.45f)
            lineTo(w * 0.65f, h * 0.73f)
            lineTo(w * 0.35f, h * 0.73f)
            close()
        }
        drawPath(
            path = neckPath,
            brush = Brush.horizontalGradient(
                colors = if (isReachable && !isOn) {
                    listOf(StandbySlateMid, Color(0xFF64748B), StandbySlateDark)
                } else {
                    listOf(Color(0xFFCBD5E1), Color(0xFFF8FAFC), Color(0xFF94A3B8))
                },
                startX = w * 0.24f,
                endX = w * 0.76f
            )
        )

        // Collar stripe on the bulb neck:
        // - ON: Electric Cyan
        // - OFF: Very Dark Gray
        // - Disconnected: Muted Gray
        drawLine(
            color = when {
                !isReachable -> Color(0xFF94A3B8)
                glowFactor > 0f -> Color(0xFF06B6D4)
                else -> PowerOffDarkGray
            },
            start = Offset(w * 0.27f, h * 0.52f),
            end = Offset(w * 0.73f, h * 0.52f),
            strokeWidth = w * 0.052f,
            cap = StrokeCap.Round
        )

        // Frosted glass bulb dome
        val domeRect = Rect(
            left = w * 0.18f,
            top = h * 0.08f,
            right = w * 0.82f,
            bottom = h * 0.59f
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = when {
                    glowFactor > 0f -> listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFE0F7FA),
                        Color(0xFF67E8F9)
                    )
                    isReachable -> listOf(
                        Color(0xFFE2E8F0),
                        Color(0xFFCBD5E1),
                        Color(0xFF94A3B8)
                    )
                    else -> listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFE2E8F0),
                        Color(0xFFCBD5E1)
                    )
                },
                center = Offset(w * 0.45f, h * 0.28f),
                radius = w * 0.38f
            ),
            topLeft = domeRect.topLeft,
            size = domeRect.size
        )

        // Inner filament / LED emitter detail so ON vs OFF (unlit filament) vs Disconnected is clear
        if (isReachable && isOn) {
            drawArc(
                color = Color(0xFF0891B2).copy(alpha = 0.7f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.34f, h * 0.22f),
                size = Size(w * 0.32f, h * 0.22f),
                style = Stroke(width = w * 0.035f, cap = StrokeCap.Round)
            )
        } else if (isReachable && !isOn) {
            // Unlit dark-slate filament posts & standby coil inside the bulb
            drawLine(
                color = StandbySlateMid,
                start = Offset(w * 0.42f, h * 0.46f),
                end = Offset(w * 0.42f, h * 0.30f),
                strokeWidth = w * 0.028f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = StandbySlateMid,
                start = Offset(w * 0.58f, h * 0.46f),
                end = Offset(w * 0.58f, h * 0.30f),
                strokeWidth = w * 0.028f,
                cap = StrokeCap.Round
            )
            drawArc(
                color = StandbySlateDark,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(w * 0.38f, h * 0.23f),
                size = Size(w * 0.24f, h * 0.16f),
                style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
            )
        }

        drawOval(
            color = when {
                !isReachable -> DisconnectedRed.copy(alpha = 0.7f)
                glowFactor > 0f -> Color(0xFF06B6D4).copy(alpha = 0.55f)
                else -> StandbySlateLight
            },
            topLeft = domeRect.topLeft,
            size = domeRect.size,
            style = Stroke(
                width = w * 0.026f,
                pathEffect = if (isReachable) null else PathEffect.dashPathEffect(floatArrayOf(w * 0.08f, w * 0.06f), 0f)
            )
        )

        // Corner state badge (ON vs OFF-Standby vs Disconnected)
        drawDeviceStateCornerBadge(
            isReachable = isReachable,
            isOn = isOn,
            badgeCenter = Offset(w * 0.80f, h * 0.78f),
            badgeRadius = w * 0.15f
        )
    }
}
