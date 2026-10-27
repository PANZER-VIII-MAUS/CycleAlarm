package com.cyclealarm.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------
// 设计令牌（取自设计稿逐像素采样的真实值，不要改）
// 注意：outline 仅用于描边/分割线，不用于正文；正文级说明统一使用 onSurfaceVariant。
// ---------------------------------------------------------------------

// 浅色
val LightPrimary = Color(0xFF038C85)
val LightPrimaryDark = Color(0xFF027A74)
val LightBackground = Color(0xFFF2F4F8)
val LightSurface = Color(0xFFFFFFFF)
val LightInk = Color(0xFF101418)
val LightInkSecondary = Color(0xFF5F6672)
val LightNavy = Color(0xFF0A3B5F)
val LightDanger = Color(0xFFC84239)
val LightSwitchOffTrack = Color(0xFFD6DBE1)

// 深色（同色系推导）
val DarkPrimary = Color(0xFF4ED0C6)
val DarkPrimaryDark = Color(0xFF3FBDB2)
val DarkBackground = Color(0xFF14161A)
val DarkSurface = Color(0xFF1C1F24)
val DarkInk = Color(0xFFECEEF1)
val DarkInkSecondary = Color(0xFF9AA1AC)
val DarkNavy = Color(0xFF8CC6E8)
val DarkDanger = Color(0xFFFF6B61)
val DarkSwitchOffTrack = Color(0xFF3A3F47)

/**
 * ColorScheme 装不下的三个令牌：对话框大时间、关闭态开关轨道、主色按下态。
 * 通过 [LocalExtraColors] 提供给各处使用，避免把色值散落在 UI 里。
 */
data class ExtraColors(
    val navy: Color,
    val switchOffTrack: Color,
    val primaryDark: Color
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(
        navy = LightNavy,
        switchOffTrack = LightSwitchOffTrack,
        primaryDark = LightPrimaryDark
    )
}

val CycleLightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF2EE),
    onPrimaryContainer = Color(0xFF00312E),
    secondary = LightPrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F2),
    onSecondaryContainer = Color(0xFF13202E),
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = Color(0xFFE7EBF1),
    onSurfaceVariant = LightInkSecondary,
    outline = Color(0xFFC3C9D2),
    outlineVariant = Color(0xFFDDE2E8),
    error = LightDanger,
    onError = Color.White
)

val CycleDarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF00312E),
    primaryContainer = Color(0xFF0E3B38),
    onPrimaryContainer = Color(0xFFC8FFF6),
    secondary = DarkPrimaryDark,
    onSecondary = Color(0xFF00312E),
    secondaryContainer = Color(0xFF23303D),
    onSecondaryContainer = Color(0xFFD6E6F5),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = Color(0xFF262A31),
    onSurfaceVariant = DarkInkSecondary,
    outline = Color(0xFF4A505A),
    outlineVariant = Color(0xFF3A3F47),
    error = DarkDanger,
    onError = Color(0xFF3B0A06)
)

fun extraColorsFor(darkTheme: Boolean): ExtraColors = if (darkTheme) {
    ExtraColors(DarkNavy, DarkSwitchOffTrack, DarkPrimaryDark)
} else {
    ExtraColors(LightNavy, LightSwitchOffTrack, LightPrimaryDark)
}
