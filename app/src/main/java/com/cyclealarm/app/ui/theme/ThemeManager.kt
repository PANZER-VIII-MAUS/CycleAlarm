package com.cyclealarm.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 主题选择：跟随系统 / 浅色 / 深色。
 * 存 SharedPreferences，并通过 StateFlow 实时通知界面切换。
 */
object ThemeManager {
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREFS = "app_prefs"
    private const val KEY = "theme_mode"

    private val _mode = MutableStateFlow(MODE_SYSTEM)
    val mode: StateFlow<String> = _mode

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = prefs.getString(KEY, MODE_SYSTEM) ?: MODE_SYSTEM
        initialized = true
    }

    fun set(context: Context, mode: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode)
            .apply()
        _mode.value = mode
    }
}
