package com.cyclealarm.app.core

import androidx.core.content.edit
import android.content.Context
import com.cyclealarm.app.R

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
    private const val KEY_RECENT_FIRES = "recent_fire_ats"

    private const val KEY_SELF_WRITE_AT = "last_self_write_at"
    private const val KEY_DIAG_AT = "last_diagnostic_at"
    private const val KEY_DIAG_REASON = "last_diagnostic_reason"

    /** 响铃时间戳的保留窗口与条数上限。 */
    private const val FIRE_HISTORY_WINDOW_MS = 24 * 60 * 60_000L
    private const val MAX_RECENT_FIRES = 24

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 上次完成重排检查的时间；0 = 从未检查过（首次启动不做漏响判定）。 */
    fun lastCheckAt(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_LAST_CHECK, 0L) }.getOrDefault(0L)

    fun markChecked(context: Context, now: Long) {
        runCatching {
            prefs(context).edit { putLong(KEY_LAST_CHECK, now) }
        }
    }

    fun recordMissed(context: Context, now: Long, count: Int) {
        runCatching {
            prefs(context).edit { putLong(KEY_LAST_MISSED_AT, now); putInt(KEY_LAST_MISSED_COUNT, count) }
        }
    }

    /**
     * 记录一次真实响铃的时间点（通常传「原定触发时间」，便于和漏响判定对齐）。
     *
     * 单值时间戳在「用了稍后提醒 / 同一天多条闹钟」时会漏豁免（三轮复审 §3.1），
     * 这里改为保留最近 24 小时内的多个时间点。
     */
    fun markFired(context: Context, at: Long) {
        runCatching {
            val keep = recentFires(context, at)
            val merged = (keep + at).distinct().sorted().takeLast(MAX_RECENT_FIRES)
            prefs(context).edit { putString(KEY_RECENT_FIRES, merged.joinToString(",")) }
        }
    }

    /** 最近 24 小时内的响铃时间点（升序）。 */
    fun recentFires(context: Context, now: Long): List<Long> {
        val raw = runCatching {
            prefs(context).getString(KEY_RECENT_FIRES, null)
        }.getOrNull() ?: return emptyList()
        val floor = now - FIRE_HISTORY_WINDOW_MS
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it in floor..now }
            .sorted()
            .takeLast(MAX_RECENT_FIRES)
    }

    /** 本 App 刚改过系统排期：短时间内的 NEXT_ALARM_CLOCK_CHANGED 是自己触发的，不必再对账。 */
    fun markSelfWrite(context: Context, now: Long) {
        runCatching { prefs(context).edit { putLong(KEY_SELF_WRITE_AT, now) } }
    }

    fun lastSelfWriteAt(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_SELF_WRITE_AT, 0L) }.getOrDefault(0L)

    /** 记录一次排期对账的诊断结果（只保留最近一次，供设置页展示）。 */
    fun recordDiagnostic(context: Context, at: Long, reason: String) {
        runCatching {
            prefs(context).edit { putLong(KEY_DIAG_AT, at); putString(KEY_DIAG_REASON, reason) }
        }
    }

    /** 诊断原因码：存储用码，展示时翻译，避免把界面语言写进数据。 */
    object DiagnosticCode {
        const val OK = "ok"
        const val MISSING = "missing"
        const val EMPTY = "empty"
        const val LATER = "later"
        const val STALE = "stale"
        const val SELF_CHECK_OK = "self_check_ok"
        const val SELF_CHECK_FAIL = "self_check_fail"
    }

    /** 最近一次对账诊断，null 表示没记录过（或一直对得上）。 */
    fun lastDiagnostic(context: Context): Pair<Long, String>? {
        val at = runCatching { prefs(context).getLong(KEY_DIAG_AT, 0L) }.getOrDefault(0L)
        if (at <= 0L) return null
        val reason = runCatching { prefs(context).getString(KEY_DIAG_REASON, null) }
            .getOrNull() ?: return null
        return at to reason
    }

    /** 设置页展示用的短标签（原因码在此翻译成当前界面语言）。 */
    fun lastDiagnosticLabel(context: Context): String {
        val diagnostic = lastDiagnostic(context)
            ?: return context.getString(R.string.diagnosis_ok)
        val formatter = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        val text = diagnosticLabel(context, diagnostic.second)
        return "${formatter.format(java.util.Date(diagnostic.first))} $text"
    }

    private fun diagnosticLabel(context: Context, codeOrText: String): String = when (codeOrText) {
        DiagnosticCode.OK -> context.getString(R.string.diagnosis_ok)
        DiagnosticCode.MISSING -> context.getString(R.string.diagnosis_missing)
        DiagnosticCode.EMPTY -> context.getString(R.string.diagnosis_empty)
        DiagnosticCode.LATER -> context.getString(R.string.diagnosis_later)
        DiagnosticCode.STALE -> context.getString(R.string.diagnosis_stale)
        DiagnosticCode.SELF_CHECK_OK -> context.getString(R.string.diagnosis_self_check_ok)
        DiagnosticCode.SELF_CHECK_FAIL -> context.getString(
            R.string.diagnosis_self_check_fail,
            codeOrText.removePrefix(DiagnosticCode.SELF_CHECK_FAIL + ":").trim()
        )
        // 兼容旧版本已写入的中文文本
        else -> codeOrText
    }
}
