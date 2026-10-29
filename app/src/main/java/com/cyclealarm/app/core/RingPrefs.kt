package com.cyclealarm.app.core

import android.content.Context

/**
 * 响铃相关偏好（响铃时长、稍后提醒间隔）。
 * 存 SharedPreferences，不动 Room schema。
 */
object RingPrefs {
    private const val NAME = "ring_prefs"
    private const val KEY_DURATION = "ring_duration_sec"   // 0 = 一直响
    private const val KEY_SNOOZE = "snooze_minutes"

    private fun prefs(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 默认 120 秒（保持现在的行为）。 */
    fun durationSeconds(c: Context): Int = prefs(c).getInt(KEY_DURATION, 120)

    fun setDurationSeconds(c: Context, value: Int) =
        prefs(c).edit().putInt(KEY_DURATION, value).apply()

    /** 默认 10 分钟（保持现在的行为）。 */
    fun snoozeMinutes(c: Context): Int = prefs(c).getInt(KEY_SNOOZE, 10)

    fun setSnoozeMinutes(c: Context, value: Int) =
        prefs(c).edit().putInt(KEY_SNOOZE, value).apply()

    /** 响铃时长可选值（秒），0 = 一直响。 */
    val DURATION_OPTIONS = listOf(30, 60, 120, 180, 300, 600, 0)

    /** 重复间隔可选值（分钟）。 */
    val SNOOZE_OPTIONS = listOf(5, 10, 15, 20, 30)

    /** 「一直响」的硬上限：通知被拒时没有任何停止入口，必须有兜底。 */
    const val ALWAYS_RING_CAP_SECONDS = 15 * 60

    fun durationLabel(sec: Int): String = when {
        sec == 0 -> "一直响（15 分钟上限）"
        sec < 60 -> "${sec} 秒"
        else -> "${sec / 60} 分钟"
    }
}
