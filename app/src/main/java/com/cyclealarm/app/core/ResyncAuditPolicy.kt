package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity

/** 一条被错过的闹钟。 */
data class MissedAlarm(
    val label: String,
    val expectedAt: Long,
    val kind: String
)

/**
 * 漏响判定（纯函数）：
 * 设备关机 / 被强杀 / 系统丢闹钟时，触发点会静默跨过。这里用「上次检查时间 → 现在」
 * 的区间反推应该响过的闹钟，供上层发提醒。
 */
object ResyncAuditPolicy {

    /** 距上次检查超过该阈值才判定，避免每次回前台都扫。 */
    const val CHECK_INTERVAL_MS = 10 * 60_000L

    /** 超过该回溯窗口不判定（关机很久后不逐条补报）。 */
    const val MAX_LOOKBACK_MS = 24 * 60 * 60_000L

    /** 单条闹钟在窗口内最多报几次，防御异常配置导致的长循环。 */
    const val MAX_HITS_PER_ALARM = 4

    /** 两次整轮重排之间的最小间隔：防止任何来源的广播回环。 */
    const val FULL_RESYNC_MIN_GAP_MS = 5_000L

    /** 距上次完整重排太近时跳过（返回 true = 跳过）。 */
    fun shouldSkipFullResync(lastCheckAt: Long, now: Long): Boolean =
        lastCheckAt > 0L && now > lastCheckAt && now - lastCheckAt < FULL_RESYNC_MIN_GAP_MS

    fun shouldAudit(lastCheckAt: Long, now: Long): Boolean {
        if (lastCheckAt <= 0L || now <= lastCheckAt) return false
        val delta = now - lastCheckAt
        return delta >= CHECK_INTERVAL_MS && delta <= MAX_LOOKBACK_MS
    }

    /** 真实响铃时间与「应有触发」在这个窗口内视为同一个闹钟，不算漏响。 */
    const val FIRED_TOLERANCE_MS = 2 * 60_000L

    fun findMissed(
        now: Long,
        lastCheckAt: Long,
        cycleAlarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>,
        firedAts: List<Long> = emptyList()
    ): List<MissedAlarm> {
        if (settings == null || !shouldAudit(lastCheckAt, now)) return emptyList()
        val missed = mutableListOf<MissedAlarm>()

        cycleAlarms.filter { it.enabled }.forEach { alarm ->
            var cursor = lastCheckAt
            var hits = 0
            while (hits < MAX_HITS_PER_ALARM) {
                val next = AlarmScheduler.nextTriggerFor(
                    alarm = alarm,
                    settings = settings,
                    holidays = holidays,
                    now = cursor
                ) ?: break
                if (next > now) break
                missed += MissedAlarm(alarm.label, next, AlarmScheduler.KIND_CYCLE)
                cursor = next + 1
                hits++
            }
        }

        // 按日期闹钟直接用自己的 epochDay + 时间算触发点：
        // nextTriggerForDateAlarm 对过去日期返回 null，会把「跨天的日期闹钟」全部漏报（三轮复审 §3.1）。
        val zone = java.time.ZoneId.systemDefault()
        dateAlarms.filter { it.enabled }.forEach { alarm ->
            val expected = runCatching {
                java.time.LocalDate.ofEpochDay(alarm.epochDay)
                    .atTime(alarm.hour, alarm.minute)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull() ?: return@forEach
            if (expected <= lastCheckAt || expected > now) return@forEach
            val mutedByHoliday = alarm.mutedDuringHoliday &&
                settings.holidayModeEnabled &&
                holidays.any { it.enabled && alarm.epochDay in it.startEpochDay..it.endEpochDay }
            if (!mutedByHoliday) {
                missed += MissedAlarm(alarm.label, expected, AlarmScheduler.KIND_DATE)
            }
        }

        // 正常响过的闹钟不能算漏响：与任一已响时间点落在容差内即豁免
        // （用列表而不是单个时间戳，才能同时覆盖「稍后提醒」与「同一天多条闹钟」）
        val notFired = missed.filter { candidate ->
            firedAts.none { fired ->
                kotlin.math.abs(candidate.expectedAt - fired) <= FIRED_TOLERANCE_MS
            }
        }
        return notFired.sortedBy { it.expectedAt }
    }
}
