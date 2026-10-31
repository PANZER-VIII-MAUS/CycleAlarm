package com.cyclealarm.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 循环方式常量。 */
object CycleTypes {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val CUSTOM = "CUSTOM"
}

/**
 * 周期内某一天的闹钟。同一天可设置多条（不同时间）。
 * dayIndex：0..N-1，对应周期里的第 dayIndex+1 天。
 * mutedDuringHoliday：假期模式开启时该闹钟是否静音（默认 true）。
 */
@Entity(
    tableName = "alarms",
    indices = [Index(value = ["dayIndex", "hour", "minute"])]
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayIndex: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val mutedDuringHoliday: Boolean = true,
    val label: String = "",
    /** true = 这条闹钟由「循环安排」生成；false = 手动添加或课表导入。 */
    val planGenerated: Boolean = false
)

/**
 * 安排类型（上课 / 自习 / 休息 …）。
 * rings=false 表示不响铃（如休息）；hour/minute 是该类型的默认响铃时间。
 */
@Entity(tableName = "activity_types")
data class ActivityTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val hour: Int,
    val minute: Int,
    val rings: Boolean = true,
    val sortOrder: Int = 0
)

/**
 * 某个周期日的安排：一天最多一个。
 * hour/minute 在分配时取类型默认值，之后允许单日调整。
 */
@Entity(tableName = "day_plans")
data class DayPlanEntity(
    @PrimaryKey val dayIndex: Int,
    val typeId: Int,
    val hour: Int,
    val minute: Int,
    /** 临时停掉这一天：保留安排记录，但不生成闹钟。 */
    val enabled: Boolean = true
)

/**
 * 假期表：多段假期，每行一段（如“春节 2026”“国庆节 2026”或自定义区间）。
 * 日期用 epochDay 表示（自 1970-01-01 起的天数），便于跨时区计算。
 * enabled：这一段假期是否生效；全局“假期模式”开关会批量启停所有行。
 */
@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val enabled: Boolean = true,
    val label: String = ""
)

/**
 * 按具体日期的一次性闹钟（课表/排班表按日期导入时使用）。
 * epochDay 为 LocalDate.toEpochDay()；触发后时间点已过，自然不再排期。
 */
@Entity(tableName = "date_alarms")
data class DateAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val epochDay: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val mutedDuringHoliday: Boolean = true,
    val label: String = ""
)

/**
 * 应用设置：周期天数 N 与周期对齐锚点。
 * 周期第 dayIndex 天 = cycleStartEpochDay + dayIndex + k*N（k 为任意整数）。
 */
@Entity(tableName = "settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val cycleLength: Int = 6,
    val cycleStartEpochDay: Long,
    /** 循环方式：DAILY（每天）/ WEEKLY（每周）/ CUSTOM（自定义 N 天）。 */
    val cycleType: String = CycleTypes.CUSTOM,
    /** 假期模式总开关：关闭时所有假期段都不生效。 */
    val holidayModeEnabled: Boolean = true,
    /** v6 的一次性 seed 标记：只有 false 时才执行「清空旧闹钟 + 建默认计划」。 */
    val plansSeeded: Boolean = false
)

/** 稍后提醒的持久化记录，用于重启后重建尚未到期的 snooze。 */
@Entity(
    tableName = "snoozes",
    indices = [
        Index(value = ["triggerAt"]),
        Index(value = ["alarmId"])
    ]
)
data class SnoozeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val alarmId: Long,
    val dayIndex: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val triggerAt: Long,
    val requestCode: Int
)

/** 导入前快照（单行），用于跨进程重启后撤销最近一次导入。 */
@Entity(tableName = "import_snapshots")
data class ImportSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val payload: String,
    val createdAt: Long,
    val expiresAt: Long
)

/**
 * 响铃事件（v9 新增）：每条「本该响的闹钟」一条记录。
 *
 * - expectedAt：原定触发时间（可按它精确判定漏响，不再依赖启发式时间戳）
 * - rungAt：真正开始响的时间（null = 没响成 → 漏响）
 * - ackedAt：用户处理时间（停止 / 稍后 / 关闭）
 */
@Entity(
    tableName = "ring_events",
    indices = [
        Index(value = ["expectedAt"]),
        Index(value = ["alarmKind", "alarmId"])
    ]
)
data class RingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmKind: String,
    val alarmId: Int,
    val label: String = "",
    val expectedAt: Long,
    val rungAt: Long? = null,
    val ackedAt: Long? = null
)
