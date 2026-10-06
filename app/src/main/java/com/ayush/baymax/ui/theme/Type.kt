package com.ayush.baymax.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** All sizes are in sp so they follow the system font size (NFR-13). */
fun baymaxTypography(family: FontFamily): Typography {
    val base = TextStyle(fontFamily = family)
    return Typography(
        // Screen titles: "Health log", "Settings"
        headlineSmall = base.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.02).em),
        // Baymax's caption line
        titleLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.01).em),
        titleMedium = base.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodyLarge = base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        labelLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
        // Uppercase overlines on the chest panel and in settings
        labelSmall = base.copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.15.em),
    )
}
