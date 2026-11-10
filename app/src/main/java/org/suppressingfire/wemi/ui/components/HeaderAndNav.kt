package org.suppressingfire.wemi.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.draw.drawBehind
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
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.ui.BottomTab
import org.suppressingfire.wemi.ui.theme.ComfortaaFontFamily
import org.suppressingfire.wemi.ui.theme.WemoGreenBottom
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoGreenTop
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary
import org.suppressingfire.wemi.ui.theme.WorkSansFontFamily

@Composable
fun TopHeader(
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
                brush = Brush.linearGradient(
                    colors = listOf(
                        WemoGreenTop,
                        WemoGreenPrimary,
                        WemoGreenBottom
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 450f)
                )
            )
            .drawBehind {
                // Atmospheric cyan-teal radial glow in upper-right header
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x4422D3EE),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.2f),
                        radius = size.width * 0.55f
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Row: Refresh button on left, TV Remote Mode + Edit pills on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .testTag("refresh_devices_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(if (isRefreshing) rotation else 0f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isEditMode) Color.White else Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onEditClick)
                        .testTag("edit_devices_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isEditMode) stringResource(R.string.action_done) else stringResource(R.string.action_edit),
                            color = if (isEditMode) WemoGreenBottom else Color.White,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Second Row: "wemi" wordmark + subtitle on left, glowing cyan/white "+" button on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onWordmarkClick)
                    .padding(horizontal = 4.dp)
                    .testTag("brand_wordmark_text")
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
                    fontSize = 35.sp,
                    letterSpacing = (-0.8).sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    color = Color(0x3322D3EE),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "LAN SWITCHES",
                        color = Color(0xFFE0F7FA),
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onAddClick)
                    .testTag("add_device_button"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_add_device),
                            tint = WemoGreenDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Teal & Cyan bottom navigation bar with active pill highlights.
 */
@Composable
fun BottomNavigationBar(
    selectedTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Signature electric cyan-teal top accent border
            HorizontalDivider(
                thickness = 2.dp,
                color = WemoGreenPrimary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Devices
                val devicesActive = selectedTab == BottomTab.DEVICES
                val devicesColor = if (devicesActive) WemoGreenDark else WemoTextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectTab(BottomTab.DEVICES) }
                        .padding(vertical = 6.dp)
                        .testTag("nav_tab_devices"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (devicesActive) WemoGreenSoft else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PlugTabIcon(tint = devicesColor)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tab_devices),
                        color = devicesColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (devicesActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Tab 2: Rules
                val rulesActive = selectedTab == BottomTab.RULES
                val rulesColor = if (rulesActive) WemoGreenDark else WemoTextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectTab(BottomTab.RULES) }
                        .padding(vertical = 6.dp)
                        .testTag("nav_tab_rules"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (rulesActive) WemoGreenSoft else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RulesCalendarClockIcon(tint = rulesColor)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tab_rules),
                        color = rulesColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (rulesActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // Tab 3: More
                val moreActive = selectedTab == BottomTab.MORE
                val moreColor = if (moreActive) WemoGreenDark else WemoTextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectTab(BottomTab.MORE) }
                        .padding(vertical = 6.dp)
                        .testTag("nav_tab_more"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (moreActive) WemoGreenSoft else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = stringResource(R.string.tab_more),
                            tint = moreColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tab_more),
                        color = moreColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (moreActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun RulesCalendarClockIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.58f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.08f, h * 0.08f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.14f, h * 0.30f),
            end = Offset(w * 0.72f, h * 0.30f),
            strokeWidth = stroke
        )
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
