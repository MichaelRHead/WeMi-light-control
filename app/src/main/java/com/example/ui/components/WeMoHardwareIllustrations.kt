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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.WemoGreenBottom
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenTop
import com.example.ui.theme.WemoMotionCyan

private val StandbySlateDark = Color(0xFF1E293B)
private val StandbySlateMid = Color(0xFF334155)
private val StandbySlateLight = Color(0xFF64748B)
private val StandbyAmber = Color(0xFFF59E0B)
private val StandbyAmberGlow = Color(0x38F59E0B)
private val DisconnectedRed = Color(0xFFDC2626)
private val OnlineGreenBadge = Color(0xFF059669)

/**
 * Draws a distinct corner badge on hardware illustrations so the user can immediately distinguish:
 * 1) Reachable & ON: Emerald/Cyan circle with vertical "I" power-on bar
 * 2) Reachable & OFF (Standby): Dark Slate circle with Warm Amber ring and open "O" standby symbol
 * 3) Disconnected / Offline: Red circle with white "X" offline cross
 */
private fun DrawScope.drawDeviceStateCornerBadge(
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
 * WeMi Smart Wall Switch / Module illustration with three unmistakable visual states:
 * - Reachable & ON: Ocean-teal anodized bezel, glowing electric-cyan LED bar, active "I/O" touch ring, and emerald ON badge.
 * - Reachable & OFF: Crisp dark slate-navy metallic bezel (full opacity), warm amber standby locator LED, IEC "O" standby ring, and amber-bordered OFF badge.
 * - Disconnected: Faded housing with dashed red outline, crossed-out LED bar, and red "X" offline badge.
 */
@Composable
fun WeMoSmartPlugIllustration(
    isReachable: Boolean,
    isOn: Boolean,
    modifier: Modifier = Modifier
) {
    val alphaVal = if (isReachable) 1f else 0.58f
    Canvas(
        modifier = modifier
            .size(width = 58.dp, height = 74.dp)
            .alpha(alphaVal)
    ) {
        val w = size.width
        val h = size.height

        // Soft ambient shadow beneath switch
        drawOval(
            color = when {
                !isReachable -> Color(0x18DC2626)
                isOn -> Color(0x330891B2)
                else -> Color(0x281E293B)
            },
            topLeft = Offset(w * 0.12f, h * 0.87f),
            size = Size(w * 0.76f, h * 0.09f)
        )

        // Outer architectural switch frame
        val frameLeft = w * 0.10f
        val frameTop = h * 0.07f
        val frameW = w * 0.80f
        val frameH = h * 0.83f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = when {
                    !isReachable -> listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0), Color(0xFF94A3B8))
                    isOn -> listOf(Color(0xFF0E7490), Color(0xFF155E75), Color(0xFF0F4C81))
                    else -> listOf(StandbySlateMid, StandbySlateDark, Color(0xFF0F172A))
                },
                start = Offset(frameLeft, frameTop),
                end = Offset(frameLeft + frameW, frameTop + frameH)
            ),
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameW, frameH),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
        )

        // Dashed red border when disconnected so offline state is unmistakable
        if (!isReachable) {
            drawRoundRect(
                color = DisconnectedRed.copy(alpha = 0.75f),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
                style = Stroke(
                    width = w * 0.03f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.08f, w * 0.06f), 0f)
                )
            )
        }

        // Inner frosted touch rocker plate
        val inset = w * 0.06f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = when {
                    !isReachable -> listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                    isOn -> listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
                    else -> listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                },
                startY = frameTop + inset,
                endY = frameTop + frameH - inset
            ),
            topLeft = Offset(frameLeft + inset, frameTop + inset),
            size = Size(frameW - inset * 2, frameH - inset * 2),
            cornerRadius = CornerRadius(w * 0.13f, w * 0.13f)
        )

        // Upper rocker status LED bar:
        // - ON: Glowing cyan LED bar
        // - OFF (Accessible): Warm amber standby locator LED dot + slate bar
        // - Disconnected: Muted gray bar
        when {
            isReachable && isOn -> {
                drawRoundRect(
                    color = Color(0x4022D3EE),
                    topLeft = Offset(w * 0.28f, h * 0.20f),
                    size = Size(w * 0.44f, h * 0.08f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                )
                drawLine(
                    color = Color(0xFF06B6D4),
                    start = Offset(w * 0.33f, h * 0.24f),
                    end = Offset(w * 0.67f, h * 0.24f),
                    strokeWidth = w * 0.045f,
                    cap = StrokeCap.Round
                )
            }

            isReachable && !isOn -> {
                // Warm amber standby pilot LED glow (shows switch has power & Wi-Fi, load is OFF)
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(w * 0.33f, h * 0.24f),
                    end = Offset(w * 0.67f, h * 0.24f),
                    strokeWidth = w * 0.04f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = StandbyAmberGlow,
                    radius = w * 0.09f,
                    center = Offset(w * 0.50f, h * 0.24f)
                )
                drawCircle(
                    color = StandbyAmber,
                    radius = w * 0.042f,
                    center = Offset(w * 0.50f, h * 0.24f)
                )
            }

            else -> {
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(w * 0.33f, h * 0.24f),
                    end = Offset(w * 0.67f, h * 0.24f),
                    strokeWidth = w * 0.04f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Center touch emblem on the switch face:
        // - ON: Cyan ring + vertical "I" bar
        // - OFF (Accessible): Crisp Slate-Navy IEC "O" closed circle (binary 0 = OFF)
        // - Disconnected: Muted gray broken ring with diagonal slash
        val touchCenter = Offset(w * 0.50f, h * 0.46f)
        when {
            isReachable && isOn -> {
                drawCircle(
                    color = Color(0x2606B6D4),
                    radius = w * 0.16f,
                    center = touchCenter
                )
                drawCircle(
                    color = Color(0xFF06B6D4),
                    radius = w * 0.12f,
                    center = touchCenter,
                    style = Stroke(width = w * 0.032f)
                )
                drawLine(
                    color = Color(0xFF06B6D4),
                    start = Offset(touchCenter.x, touchCenter.y - w * 0.06f),
                    end = Offset(touchCenter.x, touchCenter.y + w * 0.02f),
                    strokeWidth = w * 0.032f,
                    cap = StrokeCap.Round
                )
            }

            isReachable && !isOn -> {
                // Standby OFF: Soft amber-slate tint + complete IEC "O" circle (no vertical "I" stem)
                drawCircle(
                    color = Color(0x18334155),
                    radius = w * 0.16f,
                    center = touchCenter
                )
                drawCircle(
                    color = StandbySlateMid,
                    radius = w * 0.115f,
                    center = touchCenter,
                    style = Stroke(width = w * 0.036f)
                )
            }

            else -> {
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = w * 0.115f,
                    center = touchCenter,
                    style = Stroke(width = w * 0.028f)
                )
                drawLine(
                    color = DisconnectedRed,
                    start = Offset(touchCenter.x - w * 0.09f, touchCenter.y - w * 0.09f),
                    end = Offset(touchCenter.x + w * 0.09f, touchCenter.y + w * 0.09f),
                    strokeWidth = w * 0.032f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Lower smart outlet / receptacle detail
        val socketTop = h * 0.64f
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(w * 0.28f, socketTop),
            size = Size(w * 0.44f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )
        drawLine(
            color = Color(0xFF334155),
            start = Offset(w * 0.40f, socketTop + h * 0.04f),
            end = Offset(w * 0.40f, socketTop + h * 0.10f),
            strokeWidth = w * 0.036f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF334155),
            start = Offset(w * 0.60f, socketTop + h * 0.04f),
            end = Offset(w * 0.60f, socketTop + h * 0.10f),
            strokeWidth = w * 0.036f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = if (isReachable && isOn) Color(0xFF0E7490) else StandbySlateMid,
            radius = w * 0.032f,
            center = Offset(w * 0.50f, socketTop + h * 0.135f)
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

/**
 * Redesigned WeMi Motion Radar Sensor illustration with deep sapphire/teal lens aperture
 * and glowing electric cyan radar rings.
 */
@Composable
fun WeMoMotionSensorIllustration(
    isReachable: Boolean,
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
fun WeMoLightbulbIllustration(
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
        // - OFF (Accessible): Warm Amber Standby
        // - Disconnected: Muted Gray
        drawLine(
            color = when {
                !isReachable -> Color(0xFF94A3B8)
                glowFactor > 0f -> Color(0xFF06B6D4)
                else -> StandbyAmber
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
