package org.suppressingfire.wemi.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.ui.components.PowerStatePill
import org.suppressingfire.wemi.ui.theme.WeMiOceanTealPrimary
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

@Composable
internal fun WidgetEmptyPickerCard() {
    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, WemoDividerGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Smart Lights Saved Yet",
                color = WemoTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Open WeMi Switch Control to scan your local Wi-Fi network or add lights first, then return here to place widgets on your Home Screen.",
                color = WemoTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
internal fun WidgetSelectableCard(
    title: String,
    subtitle: String,
    isOn: Boolean,
    stateLabel: String,
    leadingIcon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = WemoRowSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.25.dp,
            color = if (isOn) WeMiOceanTealPrimary.copy(alpha = 0.5f) else WemoDividerGray
        ),
        shadowElevation = if (isOn) 2.dp else 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOn) WeMiOceanTealPrimary else Color(0xFFCBD5E1),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WemoTextPrimary,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = WemoTextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            PowerStatePill(
                isOn = isOn,
                label = stateLabel
            )
        }
    }
}
