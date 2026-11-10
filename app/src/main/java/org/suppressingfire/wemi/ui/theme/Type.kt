package org.suppressingfire.wemi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.R

val ComfortaaFontFamily = FontFamily(
    Font(R.font.comfortaa, FontWeight.Normal),
    Font(R.font.comfortaa, FontWeight.Bold)
)

val WorkSansFontFamily = FontFamily(
    Font(R.font.work_sans, FontWeight.Light),
    Font(R.font.work_sans, FontWeight.Normal),
    Font(R.font.work_sans, FontWeight.Medium),
    Font(R.font.work_sans, FontWeight.SemiBold)
)

val Typography = Typography(
    displayMedium = TextStyle(
        fontFamily = ComfortaaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
