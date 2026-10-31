package com.cyclealarm.app.core

/**
 * 排期对账的决策（纯函数）。
 *
 * 输入：
 * - missingCount：已算出「应该有下一次」但系统里找不到对应 PendingIntent 的闹钟数
 * - expected/actual：App 计算的下一次触发 vs 系统 getNextAlarmClock() 的触发
 *
 * 输出要执行的动作，便于单测与日志/诊断文案统一。
 */
object ReconcilePolicy {

    enum class Action {
        /** 系统里缺了已排好的闹钟 → 立即重排。 */
        RESCHEDULE_MISSING,

        /** 系统里一个 alarm clock 都没有 → 立即重排。 */
        RESCHEDULE_EMPTY,

        /** 系统排期比我们算的晚 → 立即重排。 */
        RESCHEDULE_LATER,

        /** 系统里有更早的历史闹钟 → 只记日志（反复重排也清不掉）。 */
        LOG_STALE,

        /** 一切正常。 */
        NONE
    }

    fun decide(
        expected: Long?,
        actual: Long?,
        missingCount: Int
    ): Action = when {
        expected == null -> Action.NONE
        missingCount > 0 -> Action.RESCHEDULE_MISSING
        actual == null -> Action.RESCHEDULE_EMPTY
        actual > expected -> Action.RESCHEDULE_LATER
        actual < expected -> Action.LOG_STALE
        else -> Action.NONE
    }

    fun diagnosticText(action: Action): String? = when (action) {
        Action.RESCHEDULE_MISSING -> "系统缺少已排期的闹钟，已重排"
        Action.RESCHEDULE_EMPTY -> "系统无闹钟，已重排"
        Action.RESCHEDULE_LATER -> "系统排期偏晚，已重排"
        Action.LOG_STALE -> "存在更早的历史闹钟（未动排期）"
        Action.NONE -> null
    }
}
