package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.WeMoBottomTab
import com.example.ui.theme.ComfortaaFontFamily
import com.example.ui.theme.WemoGreenBottom
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenTop
import com.example.ui.theme.WemoTextSecondary
import com.example.ui.theme.WorkSansFontFamily

@Composable
fun WeMoTopHeader(
    isRefreshing: Boolean,
    isEditMode: Boolean,
    useWeMiWordmark: Boolean,
    onRefreshClick: () -> Unit,
    onEditClick: () -> Unit,
    onAddClick: () -> Unit,
    onWordmarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(WemoGreenTop, WemoGreenBottom)
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Row: Refresh icon on left, Edit button on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("refresh_devices_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .rotate(if (isRefreshing) rotation else 0f)
                )
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("edit_devices_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEditMode) stringResource(R.string.action_done) else stringResource(R.string.action_edit),
                    color = Color.White,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = if (isEditMode) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 19.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Second Row: "wemo" / "wemi" wordmark on left, white rounded square "+" button on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (useWeMiWordmark) {
                    stringResource(R.string.header_brand_wemi)
                } else {
                    stringResource(R.string.header_brand_wemo)
                },
                color = Color.White,
                fontFamily = ComfortaaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = (-1.0).sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onWordmarkClick)
                    .padding(horizontal = 4.dp)
                    .testTag("brand_wordmark_text")
            )

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onAddClick)
                    .testTag("add_device_button"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_add_device),
                            tint = WemoGreenBottom,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Classic WeMo bottom navigation bar with thin green top border and 3 tabs:
 * Devices (plug icon), Rules (calendar + clock icon), More (three dots icon).
 */
@Composable
fun WeMoBottomNavigationBar(
    selectedTab: WeMoBottomTab,
    onSelectTab: (WeMoBottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Signature thin green top border line from screenshot
            HorizontalDivider(
                thickness = 1.5.dp,
                color = WemoGreenPrimary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Devices
                val devicesActive = selectedTab == WeMoBottomTab.DEVICES
                val devicesColor = if (devicesActive) WemoGreenPrimary else WemoTextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectTab(WeMoBottomTab.DEVICES) }
                        .padding(vertical = 6.dp)
                        .testTag("nav_tab_devices"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    WeMoPlugTabIcon(tint = devicesColor)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tab_devices),
                        color = devicesColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (devicesActive) FontWeight.Medium else FontWeight.Normal
                    )
                }

                // Tab 2: Rules
                val rulesActive = selectedTab == WeMoBottomTab.RULES
                val rulesColor = if (rulesActive) WemoGreenPrimary else WemoTextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectTab(WeMoBottomTab.RULES) }
                        .padding(vertical = 6.dp)
                        .testTag("nav_tab_rules"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    WeMoRulesCalendarClockIcon(tint = rulesColor)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tab_rules),
                        color = rulesColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (rulesActive) FontWeight.Medium else FontWeight.Normal
                    )
                }

                // Tab 3: More
                val moreActive = selectedTab == WeMoBottomTab.MORE
                val moreColor = if (moreActive) WemoGreenPrimary else WemoTextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectTab(WeMoBottomTab.MORE) }
                        .padding(vertical = 6.dp)
                        .testTag("nav_tab_more"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = stringResource(R.string.tab_more),
                        tint = moreColor,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tab_more),
                        color = moreColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (moreActive) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Custom Canvas icon matching the "Rules" calendar + clock badge icon in the screenshot.
 */
@Composable
fun WeMoRulesCalendarClockIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Calendar outer frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.58f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.08f, h * 0.08f),
            style = Stroke(width = stroke)
        )
        // Top header bar of calendar
        drawLine(
            color = tint,
            start = Offset(w * 0.14f, h * 0.30f),
            end = Offset(w * 0.72f, h * 0.30f),
            strokeWidth = stroke
        )
        // Two small binder rings at top
        drawLine(
            color = tint,
            start = Offset(w * 0.28f, h * 0.08f),
            end = Offset(w * 0.28f, h * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.56f, h * 0.08f),
            end = Offset(w * 0.56f, h * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Small clock badge in bottom-right
        val clockCenter = Offset(w * 0.70f, h * 0.68f)
        val clockRadius = w * 0.20f
        drawCircle(
            color = Color.White,
            radius = clockRadius + stroke * 0.5f,
            center = clockCenter
        )
        drawCircle(
            color = tint,
            radius = clockRadius,
            center = clockCenter,
            style = Stroke(width = stroke)
        )
        // Clock hands
        drawLine(
            color = tint,
            start = clockCenter,
            end = Offset(clockCenter.x, clockCenter.y - clockRadius * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = clockCenter,
            end = Offset(clockCenter.x + clockRadius * 0.5f, clockCenter.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
