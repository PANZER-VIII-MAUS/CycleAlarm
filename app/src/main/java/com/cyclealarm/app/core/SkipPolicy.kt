package com.cyclealarm.app.core

import java.time.Instant
import java.time.ZoneId

/**
 * 「跳过下一次」与「今天临时静音」的纯逻辑。
 *
 * 调度器算出原始触发点后，用这里把「用户明确跳过的具体触发点」和「被临时静音的日期」
 * 逐次跳过，再拿最终结果去排期。纯函数，便于单测。
 */
object SkipPolicy {

    /** 防御异常数据造成的长循环（最多连续跳过这么多次）。 */
    const val MAX_SKIPS = 10

    fun epochDayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /**
     * @param firstTrigger 调度器算出的原始下一次触发；null 表示本来就没有
     * @param skips 用户「跳过下一次」标记的具体时间点
     * @param mutedDays 被「临时静音」的日期（按天）
     * @param nextAfter 给定「已经跳过的触发点」，算出再下一次
     */
    fun effectiveTrigger(
        firstTrigger: Long?,
        skips: Set<Long>,
        mutedDays: Set<Long>,
        nextAfter: (Long) -> Long?,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        var cursor = firstTrigger ?: return null
        var guard = 0
        while (guard < MAX_SKIPS) {
            val skipped = cursor in skips
            val muted = mutedDays.isNotEmpty() && epochDayOf(cursor, zone) in mutedDays
            if (!skipped && !muted) return cursor
            cursor = nextAfter(cursor) ?: return null
            guard++
        }
        return null
    }
}
