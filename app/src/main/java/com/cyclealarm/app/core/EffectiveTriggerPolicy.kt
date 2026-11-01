package com.cyclealarm.app.core

import android.content.Context
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity

/**
 * 「生效触发点」的唯一真相源。
 *
 * 之前「跳过下一次 / 今天临时静音」只在 [AlarmScheduler] 排期时生效，
 * 而漏响判定、对账、首页「下次响铃」、桌面小组件都还按**原始触发点**算，
 * 结果是：用户点一次静音 → 被静音的那次被判「错过闹钟」→ 对账再判「系统排期偏晚」反复重排。
 *
 * 现在所有读「下一次什么时候响」的地方都走这里，保证同一个闹钟只有一份生效触发点。
 */
object EffectiveTriggerPolicy {

    /** 周期闹钟的生效触发点：原始触发点 + 跳过列表 + 临时静音日期逐次跳过。 */
    fun nextCycleTrigger(
        context: Context,
        alarm: AlarmEntity,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>,
        now: Long = System.currentTimeMillis()
    ): Long? = SkipPolicy.effectiveTrigger(
        firstTrigger = AlarmScheduler.nextTriggerFor(alarm, settings, holidays, now),
        skips = TemporaryMuteStore.skipsFor(context, AlarmScheduler.KIND_CYCLE, alarm.id),
        mutedDays = TemporaryMuteStore.mutedDays(context),
        nextAfter = { after -> AlarmScheduler.nextTriggerFor(alarm, settings, holidays, after + 1) }
    )

    /** 按日期闹钟的生效触发点（一次性：被跳过/静音就等于不会再响）。 */
    fun nextDateTrigger(
        context: Context,
        alarm: DateAlarmEntity,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>,
        now: Long = System.currentTimeMillis()
    ): Long? = SkipPolicy.effectiveTrigger(
        firstTrigger = AlarmScheduler.nextTriggerForDateAlarm(alarm, settings, holidays, now),
        skips = TemporaryMuteStore.skipsFor(context, AlarmScheduler.KIND_DATE, alarm.id),
        mutedDays = TemporaryMuteStore.mutedDays(context),
        nextAfter = { null }
    )

    /** 供纯函数（审计/对账）用的 lambda 形式，避免把 Context 传进 policy。 */
    fun cycleTriggerResolver(
        context: Context,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ): (AlarmEntity, Long) -> Long? = { alarm, from ->
        nextCycleTrigger(context, alarm, settings, holidays, from)
    }

    fun dateTriggerResolver(
        context: Context,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ): (DateAlarmEntity, Long) -> Long? = { alarm, from ->
        nextDateTrigger(context, alarm, settings, holidays, from)
    }
}
