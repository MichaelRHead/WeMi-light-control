package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WeMoConnectionQuality
import com.example.data.WeMoDeviceEntity
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft
import com.example.ui.theme.WorkSansFontFamily

internal val StatusActiveEmerald = Color(0xFF059669)
internal val StatusActiveSoftBg = Color(0xFFECFDF5)
internal val StatusGoodTeal = Color(0xFF0891B2)
internal val StatusSlowAmber = Color(0xFFD97706)
internal val StatusSlowSoftBg = Color(0xFFFFFBEB)
internal val StatusOfflineRed = Color(0xFFDC2626)
internal val StatusOfflineSoftBg = Color(0xFFFEF2F2)

/**
 * Full-width switch title text that always keeps the entire device name visible across the card width
 * and reactively reduces its font size (`maxFontSizeSp` down to `minFontSizeSp`) when a switch name
 * is too long to fit at the default size.
 */
@Composable
fun WeMoAutoShrinkSwitchName(
    name: String,
    color: Color,
    maxFontSizeSp: Float = 18f,
    minFontSizeSp: Float = 11f,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontFamily: FontFamily = WorkSansFontFamily,
    modifier: Modifier = Modifier
) {
    val estimatedInitialSp = remember(name, maxFontSizeSp, minFontSizeSp) {
        val len = name.length
        val est = when {
            len <= 24 -> maxFontSizeSp
            len <= 30 -> maxFontSizeSp - 1.5f
            len <= 36 -> maxFontSizeSp - 3f
            len <= 44 -> maxFontSizeSp - 4.5f
            len <= 54 -> maxFontSizeSp - 5.5f
            else -> minFontSizeSp
        }
        est.coerceIn(minFontSizeSp, maxFontSizeSp)
    }

    var currentFontSizeSp by remember(name, maxFontSizeSp, minFontSizeSp) {
        mutableFloatStateOf(estimatedInitialSp)
    }

    val allowSecondLine = currentFontSizeSp <= minFontSizeSp

    Text(
        text = name,
        color = color,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = currentFontSizeSp.sp,
        lineHeight = (currentFontSizeSp + 3f).sp,
        maxLines = if (allowSecondLine) 2 else 1,
        softWrap = allowSecondLine,
        overflow = TextOverflow.Visible,
        onTextLayout = { layoutResult ->
            if ((layoutResult.didOverflowWidth || layoutResult.hasVisualOverflow) &&
                currentFontSizeSp > minFontSizeSp
            ) {
                currentFontSizeSp = (currentFontSizeSp - 1f).coerceAtLeast(minFontSizeSp)
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Real-time connection status & latency indicator pill displayed on every discovered switch.
 * Shows an animated pulsing status LED, a 4-bar UPnP signal meter, and round-trip latency (e.g., "Active • 11ms" or "Offline").
 */
@Composable
fun WeMoConnectionStatusBadge(
    device: WeMoDeviceEntity,
    isPinging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val quality = device.connectionQuality

    val (dotColor, bgColor, textColor) = when (quality) {
        WeMoConnectionQuality.EXCELLENT ->
            Triple(StatusActiveEmerald, StatusActiveSoftBg, StatusActiveEmerald)
        WeMoConnectionQuality.GOOD ->
            Triple(StatusGoodTeal, WemoGreenSoft.copy(alpha = 0.7f), WemoGreenDark)
        WeMoConnectionQuality.SLOW ->
            Triple(StatusSlowAmber, StatusSlowSoftBg, StatusSlowAmber)
        WeMoConnectionQuality.OFFLINE ->
            Triple(StatusOfflineRed, StatusOfflineSoftBg, StatusOfflineRed)
    }

    val pulseTransition = rememberInfiniteTransition(label = "connPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (quality == WeMoConnectionQuality.OFFLINE) 0.4f else 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPinging) 350 else 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.8.dp, dotColor.copy(alpha = 0.32f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            // Pulsing Connection Status Dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = pulseAlpha))
            )

            // 4-Bar Signal Quality Meter
            WeMoSignalBarsIndicator(
                activeBars = quality.signalBars,
                activeColor = dotColor
            )

            // Status + Latency Text (e.g. "Active • 11ms" or "Offline • Timeout")
            Text(
                text = if (isPinging) "Pinging..." else device.connectionBadgeText,
                color = textColor,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * 4-bar wireless UPnP signal quality & latency strength indicator drawn on Canvas.
 */
@Composable
fun WeMoSignalBarsIndicator(
    activeBars: Int,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 13.dp, height = 10.dp)) {
        val barCount = 4
        val gap = 1.6.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val fraction = (i + 1) / barCount.toFloat()
            val barHeight = size.height * (0.32f + 0.68f * fraction)
            val x = i * (barWidth + gap)
            val y = size.height - barHeight
            val color = if (i < activeBars) {
                activeColor
            } else {
                activeColor.copy(alpha = 0.22f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

/**
 * Distinct power state badge displayed beside the connection status pill for reachable devices:
 * - ON: Glowing emerald/cyan pill with IEC "I/O" active power icon
 * - OFF (Accessible Standby): Slate-navy & warm-amber pill with IEC "O" (Zero/Off) open circle icon
 */
@Composable
fun WeMoPowerStatePill(
    isOn: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isOn) WemoGreenSoft else Color(0xFFF1F5F9)
    val borderColor = if (isOn) WemoGreenPrimary.copy(alpha = 0.45f) else Color(0xFFF59E0B).copy(alpha = 0.65f)
    val contentColor = if (isOn) WemoGreenDark else Color(0xFF334155)
    val iconColor = if (isOn) WemoGreenDark else Color(0xFFD97706)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.9.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                val r = size.minDimension * 0.40f
                val c = Offset(size.width / 2f, size.height / 2f)
                val stroke = 1.6.dp.toPx()
                if (isOn) {
                    drawArc(
                        color = iconColor,
                        startAngle = -50f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r + 0.5.dp.toPx()),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(c.x, c.y - r - 1.dp.toPx()),
                        end = Offset(c.x, c.y + 0.5.dp.toPx()),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                } else {
                    // Distinct IEC "O" (Zero = OFF) closed standby circle
                    drawCircle(
                        color = iconColor,
                        radius = r,
                        center = c,
                        style = Stroke(width = stroke)
                    )
                }
            }
            Text(
                text = label,
                color = contentColor,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}
