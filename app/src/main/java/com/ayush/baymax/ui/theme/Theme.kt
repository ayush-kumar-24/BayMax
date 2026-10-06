package com.ayush.baymax.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Baymax design tokens. Mirrors design/UI_SPEC.md section 2. */
@Immutable
data class BaymaxColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val stageCenter: Color,
    val stageEdge: Color,
    val emergencyStageCenter: Color,
    val emergencyStageEdge: Color,
    val isDark: Boolean,
) {
    // Character and accent colors are the same in both themes: Baymax stays white.
    val vinyl1 = Color(0xFFFFFFFF)
    val vinyl2 = Color(0xFFF1F2F5)
    val vinyl3 = Color(0xFFDFE2E7)
    val vinyl4 = Color(0xFFCDD2D9)
    val face = Color(0xFF111317)
    val red = Color(0xFFD7262E)
    val redLight = Color(0xFFE8434A)
    val redDeep = Color(0xFFA8161D)
    val green = Color(0xFF2FA86B)
    val amber = Color(0xFFE3A21A)
    val glass = Color(0xFF0E1822)
    val cyan = Color(0xFF5FD8FF)
    val cyanDim = Color(0x405FD8FF)
    val glassText = Color(0xFFDFF6FF)
    val glassMuted = Color(0xFF9CC7D8)
}

val LightBaymaxColors = BaymaxColors(
    bg = Color(0xFFF3F1ED),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF7F6F3),
    ink = Color(0xFF1B1D22),
    muted = Color(0xFF7A7F89),
    line = Color(0xFFE4E2DD),
    stageCenter = Color(0xFFFBFAF8),
    stageEdge = Color(0xFFE9E6E0),
    emergencyStageCenter = Color(0xFFFFE9E9),
    emergencyStageEdge = Color(0xFFF4C6C6),
    isDark = false,
)

val DarkBaymaxColors = BaymaxColors(
    bg = Color(0xFF0D1015),
    surface = Color(0xFF161A21),
    surface2 = Color(0xFF1C212A),
    ink = Color(0xFFEEF0F3),
    muted = Color(0xFF8B93A1),
    line = Color(0xFF262B34),
    stageCenter = Color(0xFF1F2733),
    stageEdge = Color(0xFF0D1015),
    emergencyStageCenter = Color(0xFF3A1418),
    emergencyStageEdge = Color(0xFF14080A),
    isDark = true,
)

val LocalBaymaxColors = staticCompositionLocalOf { LightBaymaxColors }

object BaymaxTheme {
    val colors: BaymaxColors
        @Composable get() = LocalBaymaxColors.current
}

@Composable
fun BaymaxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkBaymaxColors else LightBaymaxColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.ink, onPrimary = colors.surface,
            secondary = colors.red, onSecondary = Color.White,
            error = colors.red,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
            outline = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.ink, onPrimary = colors.surface,
            secondary = colors.red, onSecondary = Color.White,
            error = colors.red,
            background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
            outline = colors.line,
        )
    }
    CompositionLocalProvider(LocalBaymaxColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = baymaxTypography(fontFamily),
            content = content,
        )
    }
}
