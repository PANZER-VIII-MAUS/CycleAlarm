package com.cyclealarm.app.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import com.cyclealarm.app.R

/** 平台 TimePickerDialog 跟随 App 的实际强制浅/深色，而不是只看系统夜间模式。 */
fun timePickerContext(context: Context): Context {
    val systemDark =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    val dark = ThemeManager.resolveDark(ThemeManager.mode.value, systemDark)
    return ContextThemeWrapper(
        context,
        if (dark) {
            R.style.ThemeOverlay_CycleAlarm_TimePicker_Dark
        } else {
            R.style.ThemeOverlay_CycleAlarm_TimePicker_Light
        }
    )
}
