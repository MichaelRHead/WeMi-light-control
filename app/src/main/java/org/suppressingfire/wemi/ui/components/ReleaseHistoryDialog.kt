package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.suppressingfire.wemi.BuildConfig
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.ui.theme.WeMiDeepSapphireBottom
import org.suppressingfire.wemi.ui.theme.WeMiDeepTealAccent
import org.suppressingfire.wemi.ui.theme.WeMiElectricCyanTop
import org.suppressingfire.wemi.ui.theme.WeMiOceanTealPrimary
import org.suppressingfire.wemi.ui.theme.WeMiSoftCyanContainer
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

@Composable
fun ReleaseHistoryDialog(
    currentVersionName: String = BuildConfig.VERSION_NAME,
    entries: List<ReleaseHistoryEntry> = WEMI_RELEASE_HISTORY,
    onDismiss: () -> Unit
) {
    val currentVersionTag = "v$currentVersionName"
    val titleText = stringResource(R.string.release_history_title)
    val subtitleText = stringResource(R.string.release_history_subtitle)
    val closeText = stringResource(R.string.action_close)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = WemoRowSurface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 620.dp)
                .testTag("release_history_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Ocean Teal gradient header matching WeMi's signature top bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    WeMiElectricCyanTop,
                                    WeMiOceanTealPrimary,
                                    WeMiDeepSapphireBottom
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(720f, 240f)
                            )
                        )
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = titleText,
                                        color = Color.White,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.22f)
                                    ) {
                                        Text(
                                            text = currentVersionTag,
                                            color = Color.White,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = subtitleText,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("close_release_history_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = closeText,
                                tint = Color.White
                            )
                        }
                    }
                }

                // Scrollable release list (newest first); weight(1f, fill = false) keeps header & footer visible on compact/landscape viewports
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .background(WemoBackgroundGray)
                        .testTag("release_history_list"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.version }) { entry ->
                        val isCurrent = entry.version.equals(currentVersionTag, ignoreCase = true)
                        ReleaseHistoryRowCard(
                            entry = entry,
                            isCurrent = isCurrent
                        )
                    }
                }

                HorizontalDivider(color = WemoDividerGray)

                // Footer close action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WemoRowSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WeMiOceanTealPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 96.dp, minHeight = 48.dp)
                            .testTag("close_release_history_button")
                    ) {
                        Text(
                            text = closeText,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseHistoryRowCard(
    entry: ReleaseHistoryEntry,
    isCurrent: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WemoRowSurface,
        tonalElevation = if (isCurrent) 2.dp else 0.dp,
        shadowElevation = if (isCurrent) 2.dp else 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("release_entry_${entry.version}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrent) WeMiOceanTealPrimary else WeMiSoftCyanContainer
                    ) {
                        Text(
                            text = entry.version,
                            color = if (isCurrent) Color.White else WeMiDeepTealAccent,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WeMiSoftCyanContainer
                        ) {
                            Text(
                                text = "Installed",
                                color = WeMiDeepTealAccent,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = entry.releaseDate,
                    color = WemoTextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.headline,
                color = WemoTextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = entry.summary,
                color = WemoTextSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
        }
    }
}
