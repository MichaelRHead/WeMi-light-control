package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.ui.BottomTab
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Teal & Cyan bottom navigation bar with active pill highlights (`Devices`, `Rules`, `More`).
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
                BottomNavTabItem(
                    label = stringResource(R.string.tab_devices),
                    selected = selectedTab == BottomTab.DEVICES,
                    testTag = "nav_tab_devices",
                    onClick = { onSelectTab(BottomTab.DEVICES) }
                ) { tint ->
                    PlugTabIcon(tint = tint)
                }

                BottomNavTabItem(
                    label = stringResource(R.string.tab_rules),
                    selected = selectedTab == BottomTab.RULES,
                    testTag = "nav_tab_rules",
                    onClick = { onSelectTab(BottomTab.RULES) }
                ) { tint ->
                    RulesCalendarClockIcon(tint = tint)
                }

                BottomNavTabItem(
                    label = stringResource(R.string.tab_more),
                    selected = selectedTab == BottomTab.MORE,
                    testTag = "nav_tab_more",
                    onClick = { onSelectTab(BottomTab.MORE) }
                ) { tint ->
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomNavTabItem(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val tint = if (selected) WemoGreenDark else WemoTextSecondary
    Column(
        modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) WemoGreenSoft else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            icon(tint)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
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
