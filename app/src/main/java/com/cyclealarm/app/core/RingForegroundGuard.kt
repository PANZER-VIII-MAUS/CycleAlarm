package com.cyclealarm.app.core

import android.content.Context

/**
 * 前台化（startForeground）的兜底。
 *
 * `startForeground` 在服务进程内执行，可能因 ROM 限制、缺少前台服务类型权限、
 * 后台启动限制而抛出 SecurityException / MissingForegroundServiceTypeException 等；
 * 这些异常不会经过调用方（AlarmFireCoordinator）的 try/catch，会让进程直接崩溃。
 * 这里统一捕获为可处理的结果，并留下可观测记录（供下次打开 App 提示）。
 */
object RingForegroundGuard {

    /** 一次前台化尝试的结果。 */
    data class Outcome(val foregrounded: Boolean, val error: Throwable?)

    /**
     * 执行前台化动作。
     * 捕获所有 `Exception`（含 SecurityException / RuntimeException 子类）并返回失败结果；
     * `Error`（OOM、StackOverflow 等）不吞，交给系统处理。
     */
    fun attempt(block: () -> Unit): Outcome = try {
        block()
        Outcome(foregrounded = true, error = null)
    } catch (e: Exception) {
        Outcome(foregrounded = false, error = e)
    }

    /** 记录失败时间与原因（SharedPreferences，不动 Room schema）。 */
    fun recordFailure(context: Context, error: Throwable) {
        runCatching {
            prefs(context).edit()
                .putLong(KEY_LAST_FAILURE_AT, System.currentTimeMillis())
                .putString(KEY_LAST_FAILURE_REASON, error.javaClass.simpleName)
                .apply()
        }
    }

    /** 最近一次前台化失败的时间（0 = 从未失败）。 */
    fun lastFailureAt(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_LAST_FAILURE_AT, 0L) }.getOrDefault(0L)

    /** 最近一次失败原因（异常类名），无则为空串。 */
    fun lastFailureReason(context: Context): String =
        runCatching { prefs(context).getString(KEY_LAST_FAILURE_REASON, "").orEmpty() }
            .getOrDefault("")

    fun clearFailure(context: Context) {
        runCatching {
            prefs(context).edit()
                .remove(KEY_LAST_FAILURE_AT)
                .remove(KEY_LAST_FAILURE_REASON)
                .apply()
        }
    }

    private const val PREFS_NAME = "ring_prefs"
    private const val KEY_LAST_FAILURE_AT = "last_fg_failure_at"
    private const val KEY_LAST_FAILURE_REASON = "last_fg_failure_reason"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
