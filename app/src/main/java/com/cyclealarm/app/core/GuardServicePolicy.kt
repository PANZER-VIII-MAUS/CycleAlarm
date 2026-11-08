package com.cyclealarm.app.core

import androidx.core.content.edit
import android.content.Context

/**
 * 常驻保活（AlarmGuardService）的开关与决策。
 *
 * 部分用户不需要常驻通知，或系统限制了 specialUse 前台服务；
 * 提供开关后，关闭时不再启动服务，闹钟仍由 AlarmManager 精确触发。
 */
object GuardServicePolicy {

    private const val PREFS = "app_prefs"
    private const val KEY = "guard_service_enabled"

    /** 需要执行的动作。 */
    enum class Action { START, STOP, KEEP }

    fun actionFor(enabled: Boolean, currentlyRunning: Boolean): Action = when {
        enabled && !currentlyRunning -> Action.START
        !enabled && currentlyRunning -> Action.STOP
        else -> Action.KEEP
    }

    /** 当前 flavor 是否真的有 AlarmGuardService（play 包在 Manifest 里删掉了，开关必须隐藏）。 */
    val isAvailable: Boolean
        get() = com.cyclealarm.app.BuildConfig.GUARD_SERVICE_AVAILABLE

    /** 未设置过时的默认值：直装包默认开，Play 上架包默认关。 */
    private val defaultEnabled: Boolean
        get() = com.cyclealarm.app.BuildConfig.GUARD_SERVICE_DEFAULT_ON

    fun isEnabled(context: Context): Boolean = runCatching {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, defaultEnabled)
    }.getOrDefault(defaultEnabled)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY, enabled) }
    }
}
