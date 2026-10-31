package com.cyclealarm.app.ui.theme

import android.app.Activity
import android.content.Context
import com.cyclealarm.app.R
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

    /** 读取已保存的主题模式（可在 ThemeManager.init 之前调用，用于启动时选主题）。 */
    fun readStoredMode(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, MODE_SYSTEM) ?: MODE_SYSTEM

    fun init(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = prefs.getString(KEY, MODE_SYSTEM) ?: MODE_SYSTEM
        initialized = true
    }

    /**
     * 在 `super.onCreate` 之前按 App 主题设置启动窗口背景。
     *
     * 主题可能被强制为深色而系统仍是浅色，此时若只靠 `values-night` 会在启动瞬间闪白。
     * 三个 Activity 都调用这里，保证主界面/设置/导入的启动表现一致。
     */
    fun applyStartupTheme(activity: Activity) {
        val mode = readStoredMode(activity)
        val systemDark = (activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (resolveDark(mode, systemDark)) {
            activity.setTheme(R.style.Theme_CycleAlarm_OpaqueDark)
        }
    }

    fun set(context: Context, mode: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode)
            .apply()
        _mode.value = mode
    }

    fun resolveDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        MODE_LIGHT -> false
        MODE_DARK -> true
        else -> systemDark
    }
}
