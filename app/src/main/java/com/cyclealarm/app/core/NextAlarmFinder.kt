package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.data.SnoozeEntity
import java.time.Instant
import java.time.ZoneId

data class NextAlarm(
    val triggerAt: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val kind: NextAlarmKind,
    val isSnooze: Boolean = false,
    /** 供「跳过下一次」定位到具体闹钟；模拟/派生场景为 0。 */
    val alarmId: Int = 0
)

enum class NextAlarmKind { CYCLE, DATE, SNOOZE }

object NextAlarmFinder {
    /**
     * @param cycleTrigger 周期闹钟「生效触发点」解析器；默认按原始触发点算（纯逻辑/测试用）。
     *                     界面与小组件必须传 [EffectiveTriggerPolicy]，否则跳过/静音不生效。
     */
    fun find(
        now: Long,
        cycleAlarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>,
        snoozes: List<SnoozeEntity>,
        settings: AppSettingsEntity,
        holidays: List<HolidayEntity>,
        cycleTrigger: ((AlarmEntity, Long) -> Long?)? = null,
        dateTrigger: ((DateAlarmEntity, Long) -> Long?)? = null
    ): NextAlarm? {
        val candidates = buildList {
            cycleAlarms.filter { it.enabled }.forEach { alarm ->
                val triggerAt = cycleTrigger?.invoke(alarm, now) ?: AlarmScheduler.nextTriggerFor(
                    alarm = alarm,
                    settings = settings,
                    holidays = holidays,
                    now = now
                )
                if (triggerAt != null && triggerAt > now) {
                    add(
                        NextAlarm(
                            triggerAt = triggerAt,
                            hour = alarm.hour,
                            minute = alarm.minute,
                            label = alarm.label,
                            kind = NextAlarmKind.CYCLE,
                            alarmId = alarm.id
                        )
                    )
                }
            }
            dateAlarms.filter { it.enabled }.forEach { alarm ->
                val triggerAt = dateTrigger?.invoke(alarm, now) ?: AlarmScheduler.nextTriggerForDateAlarm(
                    alarm = alarm,
                    settings = settings,
                    holidays = holidays,
                    now = now
                )
                if (triggerAt != null && triggerAt > now) {
                    add(
                        NextAlarm(
                            triggerAt = triggerAt,
                            hour = alarm.hour,
                            minute = alarm.minute,
                            label = alarm.label,
                            kind = NextAlarmKind.DATE,
                            alarmId = alarm.id
                        )
                    )
                }
            }
            snoozes.filter { it.triggerAt > now }.forEach { snooze ->
                val target = Instant.ofEpochMilli(snooze.triggerAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                add(
                    NextAlarm(
                        triggerAt = snooze.triggerAt,
                        hour = target.hour,
                        minute = target.minute,
                        label = snooze.label,
                        kind = NextAlarmKind.SNOOZE,
                        isSnooze = true,
                        alarmId = snooze.alarmId.toInt()
                    )
                )
            }
        }
        return candidates.minByOrNull { it.triggerAt }
    }
}
