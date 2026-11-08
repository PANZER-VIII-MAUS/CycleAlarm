package com.cyclealarm.app.data

import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmScheduler

/**
 * 备份的业务约束校验（0.6.10 审查 Important）。
 *
 * 解码器只保证「字段能解析、校验和正确」；本校验保证「语义可被应用安全消费」：
 * 时间范围、周期天数、主键唯一、跨表引用、假期起止关系、snooze 类型。
 *
 * 校验必须在数据库事务**之前**执行——非法备份不允许写入库。
 */
object BackupValidator {

    /** 首个不合法项；null 表示通过。 */
    enum class Issue(val messageRes: Int) {
        TIME(R.string.err_backup_invalid_time),
        DAY_INDEX(R.string.err_backup_invalid_day),
        CYCLE_LENGTH(R.string.err_backup_invalid_cycle),
        DUPLICATE_ID(R.string.err_backup_duplicate_id),
        ID_RANGE(R.string.err_backup_invalid_id),
        PLAN_TYPE_REF(R.string.err_backup_invalid_plan_ref),
        DATE_RANGE(R.string.err_backup_invalid_date),
        HOLIDAY_RANGE(R.string.err_backup_invalid_holiday),
        SNOOZE(R.string.err_backup_invalid_snooze)
    }

    /** 允许的 epochDay 范围（1970-01-01 ~ 2100-01-01）。 */
    private val MIN_EPOCH_DAY = 0L

    private val MAX_EPOCH_DAY = java.time.LocalDate.of(2100, 1, 1).toEpochDay()

    fun firstIssue(backup: BackupCodec.Backup): Issue? {
        val settings = backup.settings
        val cycleLength = settings?.cycleLength ?: 7
        if (cycleLength !in 1..31) return Issue.CYCLE_LENGTH
        if (settings != null) {
            if (settings.cycleStartEpochDay !in MIN_EPOCH_DAY..MAX_EPOCH_DAY) return Issue.DATE_RANGE
            if (settings.cycleType !in setOf(CycleTypes.DAILY, CycleTypes.WEEKLY, CycleTypes.CUSTOM)) {
                return Issue.CYCLE_LENGTH
            }
        }

        if (backup.alarms.any { it.hour !in 0..23 || it.minute !in 0..59 }) return Issue.TIME
        if (backup.alarms.any { it.dayIndex !in 0 until cycleLength }) return Issue.DAY_INDEX
        if (backup.alarms.any { it.id < 0 || it.id > AlarmScheduler.MAX_ALARM_ID }) return Issue.ID_RANGE
        if (backup.alarms.map { it.id }.toSet().size != backup.alarms.size) return Issue.DUPLICATE_ID

        if (backup.dateAlarms.any { it.hour !in 0..23 || it.minute !in 0..59 }) return Issue.TIME
        if (backup.dateAlarms.any { it.epochDay !in MIN_EPOCH_DAY..MAX_EPOCH_DAY }) return Issue.DATE_RANGE
        if (backup.dateAlarms.any { it.id < 0 || it.id > AlarmScheduler.MAX_ALARM_ID }) return Issue.ID_RANGE
        if (backup.dateAlarms.map { it.id }.toSet().size != backup.dateAlarms.size) return Issue.DUPLICATE_ID

        if (backup.activityTypes.any { it.hour !in 0..23 || it.minute !in 0..59 }) return Issue.TIME
        if (backup.activityTypes.map { it.id }.toSet().size != backup.activityTypes.size) return Issue.DUPLICATE_ID

        if (backup.dayPlans.any { it.hour !in 0..23 || it.minute !in 0..59 }) return Issue.TIME
        if (backup.dayPlans.any { it.dayIndex !in 0 until cycleLength }) return Issue.DAY_INDEX
        if (backup.dayPlans.map { it.dayIndex }.toSet().size != backup.dayPlans.size) return Issue.DUPLICATE_ID
        val typeIds = backup.activityTypes.map { it.id }.toSet()
        if (backup.dayPlans.any { it.typeId !in typeIds }) return Issue.PLAN_TYPE_REF

        if (backup.holidays.any { it.startEpochDay > it.endEpochDay }) return Issue.HOLIDAY_RANGE
        if (backup.holidays.any {
                it.startEpochDay !in MIN_EPOCH_DAY..MAX_EPOCH_DAY ||
                    it.endEpochDay !in MIN_EPOCH_DAY..MAX_EPOCH_DAY
            }
        ) return Issue.DATE_RANGE
        if (backup.holidays.map { it.id }.toSet().size != backup.holidays.size) return Issue.DUPLICATE_ID

        if (backup.snoozes.any {
                it.kind !in setOf(AlarmScheduler.KIND_CYCLE, AlarmScheduler.KIND_DATE) ||
                    it.triggerAt <= 0L ||
                    it.alarmId <= 0L ||
                    it.alarmId > AlarmScheduler.MAX_ALARM_ID
            }
        ) return Issue.SNOOZE
        if (backup.snoozes.map { it.kind to it.alarmId }.toSet().size != backup.snoozes.size) {
            return Issue.DUPLICATE_ID
        }
        return null
    }
}
