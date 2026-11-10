package org.suppressingfire.wemi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WemoColorScheme = lightColorScheme(
    primary = WemoGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = WemoGreenSoft,
    onPrimaryContainer = WemoGreenDark,
    secondary = WemoGreenDark,
    onSecondary = Color.White,
    background = WemoBackgroundGray,
    onBackground = WemoTextPrimary,
    surface = WemoRowSurface,
    onSurface = WemoTextPrimary,
    surfaceVariant = WemoRowExpandedSurface,
    onSurfaceVariant = WemoTextSecondary,
    outline = WemoDividerGray,
    error = WemoErrorRed
)

@Composable
fun WeMiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WemoColorScheme,
        typography = Typography,
        content = content
    )
}
