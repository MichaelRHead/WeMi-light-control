package org.suppressingfire.wemi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.BuildConfig
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.ui.theme.WemoGreenBottom
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary
import org.suppressingfire.wemi.ui.theme.WemoGreenTop

@Composable
fun TopHeader(
    isRefreshing: Boolean,
    isEditMode: Boolean,
    onRefreshClick: () -> Unit,
    onEditClick: () -> Unit,
    onAddClick: () -> Unit,
    activeOnCount: Int = 0,
    onAllOffClick: () -> Unit = {},
    versionName: String = BuildConfig.VERSION_NAME,
    onVersionClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isStandardPhoneWidth = screenWidthDp >= 360
    val isWideHeaderWidth = screenWidthDp >= 380

    val brandText = stringResource(R.string.header_brand_wemi)
    val versionLabel = "v$versionName"
    val versionContentDesc = stringResource(R.string.content_desc_app_version_history, versionName)
    val allOffLabel = stringResource(R.string.action_all_off)
    val refreshLabel = stringResource(R.string.action_refresh)
    val editLabel = if (isEditMode) {
        stringResource(R.string.action_done)
    } else {
        stringResource(R.string.action_edit)
    }
    val addDeviceLabel = stringResource(R.string.action_add_device)

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
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Single compact row: "wemi" wordmark + subtle version button on left; All Off, Refresh, Edit/Done, and "+" buttons untouched on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp)
            ) {
                val hasInlineVersionGap = maxWidth >= 116.dp
                if (hasInlineVersionGap) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "$brandText brand header"
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("brand_wordmark_text")
                        ) {
                            Text(
                                text = brandText,
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                letterSpacing = (-0.8).sp
                            )
                        }

                        // Small, low-contrast version pill tucked in the unused teal space beside "wemi"
                        Box(
                            contentAlignment = Alignment.BottomStart,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 36.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClick = onVersionClick)
                                .semantics { contentDescription = versionContentDesc }
                                .padding(start = 2.dp, end = 2.dp, bottom = 9.dp)
                                .testTag("app_version_button")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.09f)
                            ) {
                                Text(
                                    text = versionLabel,
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Compact narrow viewports: preserve exact 48.dp row height and tuck version under descenderless "wemi"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.TopStart,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "$brandText brand header"
                                }
                                .padding(start = 4.dp, end = 4.dp, top = 1.dp)
                                .testTag("brand_wordmark_text")
                        ) {
                            Text(
                                text = brandText,
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 27.sp,
                                lineHeight = 27.sp,
                                letterSpacing = (-0.8).sp
                            )
                        }

                        Box(
                            contentAlignment = Alignment.BottomStart,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button, onClick = onVersionClick)
                                .semantics { contentDescription = versionContentDesc }
                                .padding(start = 4.dp, top = 2.dp, end = 6.dp, bottom = 1.dp)
                                .testTag("app_version_button")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color.White.copy(alpha = 0.09f)
                            ) {
                                Text(
                                    text = versionLabel,
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 9.5.sp,
                                    lineHeight = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isWideHeaderWidth) 6.dp else 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button, onClick = onAllOffClick)
                        .semantics { contentDescription = allOffLabel }
                        .testTag("all_off_devices_button")
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (activeOnCount > 0) {
                            Color.White.copy(alpha = 0.24f)
                        } else {
                            Color.White.copy(alpha = 0.14f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(
                                horizontal = if (isWideHeaderWidth) 10.dp else 8.dp,
                                vertical = 6.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = allOffLabel,
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = if (isStandardPhoneWidth) 48.dp else 36.dp, height = 48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onRefreshClick)
                        .semantics { contentDescription = refreshLabel }
                        .testTag("refresh_devices_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (isRefreshing) rotation else 0f)
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button, onClick = onEditClick)
                        .semantics { contentDescription = editLabel }
                        .testTag("edit_devices_button")
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isEditMode) Color.White else Color.White.copy(alpha = 0.16f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(
                                horizontal = if (isWideHeaderWidth) 12.dp else 10.dp,
                                vertical = 6.dp
                            )
                        ) {
                            Text(
                                text = editLabel,
                                color = if (isEditMode) WemoGreenBottom else Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(width = if (isStandardPhoneWidth) 48.dp else 34.dp, height = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onAddClick)
                        .semantics { contentDescription = addDeviceLabel }
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
                                contentDescription = null,
                                tint = WemoGreenDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
