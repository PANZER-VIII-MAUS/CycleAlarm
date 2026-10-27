package com.cyclealarm.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 卡片 24dp、对话框 28dp；Material3 的 AlertDialog 用的是 extraLarge。 */
val CycleAlarmShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CycleAlarmTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    remember { ThemeManager.init(context) }
    val mode by ThemeManager.mode.collectAsStateWithLifecycle()
    val darkTheme = ThemeManager.resolveDark(mode, isSystemInDarkTheme())
    CompositionLocalProvider(LocalExtraColors provides extraColorsFor(darkTheme)) {
        // 系统栏图标深浅：按 App 实际主题（ThemeManager 三档）而不是系统深浅，
        // 否则「App 强制浅色 + 系统深色」时图标会变白看不见。
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val activity = view.context.findActivity() ?: return@SideEffect
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
        MaterialTheme(
            colorScheme = if (darkTheme) CycleDarkColors else CycleLightColors,
            typography = CycleAlarmTypography,
            shapes = CycleAlarmShapes,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
