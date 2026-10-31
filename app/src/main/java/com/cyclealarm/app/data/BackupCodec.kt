package com.cyclealarm.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 备份文件编解码（自有文本格式，不依赖 JSON 库）。
 *
 * 设计目标：单文件、可人眼检查、跨版本可读。格式与导入快照同源（TAB 分隔 + 字符串 base64）。
 * 备份包含：设置 + 周期闹钟 + 按日期闹钟 + 安排类型 + 安排 + 假期 + 稍后提醒。
 */
object BackupCodec {
    private const val HEADER = "CYCLE_ALARM_BACKUP_V1"

    data class Backup(
        val settings: AppSettingsEntity?,
        val alarms: List<AlarmEntity>,
        val dateAlarms: List<DateAlarmEntity>,
        val activityTypes: List<ActivityTypeEntity>,
        val dayPlans: List<DayPlanEntity>,
        val holidays: List<HolidayEntity>,
        val snoozes: List<SnoozeEntity>,
        val exportedAt: Long = 0L
    )

    fun encode(backup: Backup): String = buildString {
        appendLine(HEADER)
        append("M")
        append('\t').append(backup.exportedAt)
        appendLine()

        backup.settings?.let { settings ->
            append("S")
            append('\t').append(settings.id)
            append('\t').append(settings.cycleLength)
            append('\t').append(settings.cycleStartEpochDay)
            append('\t').append(encodeString(settings.cycleType))
            append('\t').append(settings.holidayModeEnabled.flag())
            append('\t').append(settings.plansSeeded.flag())
            appendLine()
        }
        backup.alarms.forEach { alarm ->
            append("A")
            append('\t').append(alarm.id)
            append('\t').append(alarm.dayIndex)
            append('\t').append(alarm.hour)
            append('\t').append(alarm.minute)
            append('\t').append(alarm.enabled.flag())
            append('\t').append(alarm.mutedDuringHoliday.flag())
            append('\t').append(alarm.planGenerated.flag())
            append('\t').append(encodeString(alarm.label))
            appendLine()
        }
        backup.dateAlarms.forEach { alarm ->
            append("D")
            append('\t').append(alarm.id)
            append('\t').append(alarm.epochDay)
            append('\t').append(alarm.hour)
            append('\t').append(alarm.minute)
            append('\t').append(alarm.enabled.flag())
            append('\t').append(alarm.mutedDuringHoliday.flag())
            append('\t').append(encodeString(alarm.label))
            appendLine()
        }
        backup.activityTypes.forEach { type ->
            append("T")
            append('\t').append(type.id)
            append('\t').append(encodeString(type.name))
            append('\t').append(type.hour)
            append('\t').append(type.minute)
            append('\t').append(type.rings.flag())
            append('\t').append(type.sortOrder)
            appendLine()
        }
        backup.dayPlans.forEach { plan ->
            append("P")
            append('\t').append(plan.dayIndex)
            append('\t').append(plan.typeId)
            append('\t').append(plan.hour)
            append('\t').append(plan.minute)
            append('\t').append(plan.enabled.flag())
            appendLine()
        }
        backup.holidays.forEach { holiday ->
            append("H")
            append('\t').append(holiday.id)
            append('\t').append(holiday.startEpochDay)
            append('\t').append(holiday.endEpochDay)
            append('\t').append(holiday.enabled.flag())
            append('\t').append(encodeString(holiday.label))
            appendLine()
        }
        backup.snoozes.forEach { snooze ->
            append("Z")
            append('\t').append(snooze.id)
            append('\t').append(encodeString(snooze.kind))
            append('\t').append(snooze.alarmId)
            append('\t').append(snooze.dayIndex)
            append('\t').append(snooze.hour)
            append('\t').append(snooze.minute)
            append('\t').append(encodeString(snooze.label))
            append('\t').append(snooze.triggerAt)
            append('\t').append(snooze.requestCode)
            appendLine()
        }
    }

    fun decode(payload: String): Backup {
        val lines = payload.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "不是有效的 CycleAlarm 备份文件" }

        var settings: AppSettingsEntity? = null
        var exportedAt = 0L
        val alarms = mutableListOf<AlarmEntity>()
        val dateAlarms = mutableListOf<DateAlarmEntity>()
        val activityTypes = mutableListOf<ActivityTypeEntity>()
        val dayPlans = mutableListOf<DayPlanEntity>()
        val holidays = mutableListOf<HolidayEntity>()
        val snoozes = mutableListOf<SnoozeEntity>()

        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "M" -> {
                    require(parts.size == 2) { "备份头格式错误" }
                    exportedAt = parts[1].toLong()
                }

                "S" -> {
                    require(parts.size == 7) { "备份设置格式错误" }
                    settings = AppSettingsEntity(
                        id = parts[1].toInt(),
                        cycleLength = parts[2].toInt(),
                        cycleStartEpochDay = parts[3].toLong(),
                        cycleType = decodeString(parts[4]),
                        holidayModeEnabled = parts[5].toBooleanFlag(),
                        plansSeeded = parts[6].toBooleanFlag()
                    )
                }

                "A" -> {
                    require(parts.size == 9) { "备份周期闹钟格式错误" }
                    alarms += AlarmEntity(
                        id = parts[1].toInt(),
                        dayIndex = parts[2].toInt(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag(),
                        mutedDuringHoliday = parts[6].toBooleanFlag(),
                        planGenerated = parts[7].toBooleanFlag(),
                        label = decodeString(parts[8])
                    )
                }

                "D" -> {
                    require(parts.size == 8) { "备份日期闹钟格式错误" }
                    dateAlarms += DateAlarmEntity(
                        id = parts[1].toInt(),
                        epochDay = parts[2].toLong(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag(),
                        mutedDuringHoliday = parts[6].toBooleanFlag(),
                        label = decodeString(parts[7])
                    )
                }

                "T" -> {
                    require(parts.size == 7) { "备份安排类型格式错误" }
                    activityTypes += ActivityTypeEntity(
                        id = parts[1].toInt(),
                        name = decodeString(parts[2]),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        rings = parts[5].toBooleanFlag(),
                        sortOrder = parts[6].toInt()
                    )
                }

                "P" -> {
                    require(parts.size == 6) { "备份安排格式错误" }
                    dayPlans += DayPlanEntity(
                        dayIndex = parts[1].toInt(),
                        typeId = parts[2].toInt(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag()
                    )
                }

                "H" -> {
                    require(parts.size == 6) { "备份假期格式错误" }
                    holidays += HolidayEntity(
                        id = parts[1].toInt(),
                        startEpochDay = parts[2].toLong(),
                        endEpochDay = parts[3].toLong(),
                        enabled = parts[4].toBooleanFlag(),
                        label = decodeString(parts[5])
                    )
                }

                "Z" -> {
                    require(parts.size == 10) { "备份稍后提醒格式错误" }
                    snoozes += SnoozeEntity(
                        id = parts[1].toLong(),
                        kind = decodeString(parts[2]),
                        alarmId = parts[3].toLong(),
                        dayIndex = parts[4].toInt(),
                        hour = parts[5].toInt(),
                        minute = parts[6].toInt(),
                        label = decodeString(parts[7]),
                        triggerAt = parts[8].toLong(),
                        requestCode = parts[9].toInt()
                    )
                }

                else -> error("未知的备份记录")
            }
        }

        return Backup(
            settings = settings,
            alarms = alarms,
            dateAlarms = dateAlarms,
            activityTypes = activityTypes,
            dayPlans = dayPlans,
            holidays = holidays,
            snoozes = snoozes,
            exportedAt = exportedAt
        )
    }

    private fun encodeString(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeString(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private fun Boolean.flag(): String = if (this) "1" else "0"

    private fun String.toBooleanFlag(): Boolean = when (this) {
        "1" -> true
        "0" -> false
        else -> error("备份布尔值格式错误")
    }
}
