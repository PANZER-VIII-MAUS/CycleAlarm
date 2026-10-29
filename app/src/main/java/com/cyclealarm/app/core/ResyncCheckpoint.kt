package com.cyclealarm.app.core

import android.content.Context

/**
 * 重排检查点（SharedPreferences，不动 Room schema）。
 *
 * 用它替代「数据库加 lastExpectedAt 列」的方案：0.2.3 不引入 v8 迁移，
 * 漏响判定只需要知道「上次检查是什么时候」。
 */
object ResyncCheckpoint {
    private const val NAME = "resync_prefs"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val KEY_LAST_MISSED_AT = "last_missed_at"
    private const val KEY_LAST_MISSED_COUNT = "last_missed_count"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 上次完成重排检查的时间；0 = 从未检查过（首次启动不做漏响判定）。 */
    fun lastCheckAt(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_LAST_CHECK, 0L) }.getOrDefault(0L)

    fun markChecked(context: Context, now: Long) {
        runCatching {
            prefs(context).edit().putLong(KEY_LAST_CHECK, now).apply()
        }
    }

    fun recordMissed(context: Context, now: Long, count: Int) {
        runCatching {
            prefs(context).edit()
                .putLong(KEY_LAST_MISSED_AT, now)
                .putInt(KEY_LAST_MISSED_COUNT, count)
                .apply()
        }
    }

    fun lastMissedAt(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_LAST_MISSED_AT, 0L) }.getOrDefault(0L)

    fun lastMissedCount(context: Context): Int =
        runCatching { prefs(context).getInt(KEY_LAST_MISSED_COUNT, 0) }.getOrDefault(0)

    fun clearMissed(context: Context) {
        runCatching {
            prefs(context).edit()
                .remove(KEY_LAST_MISSED_AT)
                .remove(KEY_LAST_MISSED_COUNT)
                .apply()
        }
    }
}
