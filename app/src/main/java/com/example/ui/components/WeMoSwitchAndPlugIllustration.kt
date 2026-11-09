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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

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
                // Dark gray OFF indicator bar
                drawLine(
                    color = PowerOffDarkGray,
                    start = Offset(w * 0.33f, h * 0.24f),
                    end = Offset(w * 0.67f, h * 0.24f),
                    strokeWidth = w * 0.045f,
                    cap = StrokeCap.Round
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
        // - ON: Cyan IEC power symbol
        // - OFF: Very dark gray IEC power symbol
        // - Disconnected: Broken ring with diagonal disconnect slash
        val touchCenter = Offset(w * 0.50f, h * 0.46f)
        when {
            isReachable && isOn -> {
                drawCircle(
                    color = Color(0x2606B6D4),
                    radius = w * 0.16f,
                    center = touchCenter
                )
                drawIecPowerSymbol(
                    color = Color(0xFF06B6D4),
                    center = touchCenter,
                    radius = w * 0.115f,
                    strokeWidth = w * 0.032f
                )
            }

            isReachable && !isOn -> {
                drawCircle(
                    color = Color(0x181F232B),
                    radius = w * 0.16f,
                    center = touchCenter
                )
                drawIecPowerSymbol(
                    color = PowerOffIconDarkGray,
                    center = touchCenter,
                    radius = w * 0.115f,
                    strokeWidth = w * 0.032f
                )
            }

            else -> {
                drawDisconnectedSymbol(
                    color = Color(0xFF94A3B8),
                    slashColor = DisconnectedRed,
                    center = touchCenter,
                    radius = w * 0.115f,
                    strokeWidth = w * 0.030f
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
