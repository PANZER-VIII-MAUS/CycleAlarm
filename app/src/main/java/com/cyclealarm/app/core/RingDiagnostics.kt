package com.cyclealarm.app.core

import android.content.Context

/**
 * 响铃诊断：记录「上一次响铃界面到底是怎么显示出来的」。
 *
 * 为什么需要它：ColorOS 等 ROM 会屏蔽第三方应用的 logcat 输出，真机排障时
 * `adb logcat` 里看不到应用自己的日志。把结论落进 SharedPreferences，
 * 设置页就能直接显示，用户反馈问题时也有据可查。
 */
object RingDiagnostics {

    enum class Outcome {
        /** 响铃 Activity 真的显示出来了（标准路径） */
        ACTIVITY_SHOWN,

        /** Activity 被系统拦掉，用悬浮窗兜底界面顶上 */
        OVERLAY_FALLBACK,

        /** 两者都没成功，只有通知（用户可能只听到铃声） */
        NOTIFICATION_ONLY
    }

    private const val PREFS = "app_prefs"
    private const val KEY_OUTCOME = "ring_last_outcome"
    private const val KEY_AT = "ring_last_outcome_at"

    fun record(context: Context, outcome: Outcome, at: Long = System.currentTimeMillis()) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_OUTCOME, outcome.name)
                .putLong(KEY_AT, at)
                .apply()
        }
    }

    fun lastOutcome(context: Context): Outcome? = runCatching {
        val name = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OUTCOME, null)
            ?: return null
        Outcome.entries.firstOrNull { it.name == name }
    }.getOrNull()

    fun lastAt(context: Context): Long = runCatching {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_AT, 0L)
    }.getOrDefault(0L)
}
