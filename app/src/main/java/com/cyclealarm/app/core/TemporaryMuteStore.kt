package com.cyclealarm.app.core

import android.content.Context

/**
 * 「跳过下一次」与「今天临时静音」的持久化（SharedPreferences，不动数据库）。
 *
 * - 跳过下一次：按 (kind, alarmId) 记录被跳过的具体触发时间点
 * - 今天临时静音：记录被静音的日期（epochDay），过期的自动清理
 */
object TemporaryMuteStore {
    private const val NAME = "temporary_mute_prefs"
    private const val KEY_MUTED_DAYS = "muted_days"
    private const val KEY_SKIP_PREFIX = "skip:"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---------- 跳过下一次 ----------

    fun skipNext(context: Context, kind: String, alarmId: Int, triggerAt: Long) {
        runCatching {
            val merged = (skipsFor(context, kind, alarmId) + triggerAt)
                .sorted()
                .takeLast(8)
            prefs(context).edit()
                .putString(KEY_SKIP_PREFIX + kind + ":" + alarmId, merged.joinToString(","))
                .apply()
        }
    }

    fun skipsFor(context: Context, kind: String, alarmId: Int): Set<Long> {
        val raw = runCatching {
            prefs(context).getString(KEY_SKIP_PREFIX + kind + ":" + alarmId, null)
        }.getOrNull() ?: return emptySet()
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    fun clearSkip(context: Context, kind: String, alarmId: Int, triggerAt: Long) {
        runCatching {
            val remain = skipsFor(context, kind, alarmId) - triggerAt
            prefs(context).edit()
                .putString(KEY_SKIP_PREFIX + kind + ":" + alarmId, remain.joinToString(","))
                .apply()
        }
    }

    // ---------- 今天临时静音 ----------

    fun muteDay(context: Context, epochDay: Long) {
        runCatching {
            val merged = (mutedDays(context) + epochDay).sorted().takeLast(7)
            prefs(context).edit()
                .putString(KEY_MUTED_DAYS, merged.joinToString(","))
                .apply()
        }
    }

    fun mutedDays(context: Context): Set<Long> {
        val raw = runCatching { prefs(context).getString(KEY_MUTED_DAYS, null) }
            .getOrNull() ?: return emptySet()
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    fun clearMutedDay(context: Context, epochDay: Long) {
        runCatching {
            val remain = mutedDays(context) - epochDay
            prefs(context).edit().putString(KEY_MUTED_DAYS, remain.joinToString(",")).apply()
        }
    }

    /** 清掉已经过去的静音日期，避免无限增长。 */
    fun prune(context: Context, todayEpochDay: Long) {
        runCatching {
            val remain = mutedDays(context).filter { it >= todayEpochDay }
            prefs(context).edit().putString(KEY_MUTED_DAYS, remain.joinToString(",")).apply()
        }
    }
}
