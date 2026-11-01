package com.cyclealarm.app.data

import com.cyclealarm.app.core.LocalizedFailure
import com.cyclealarm.app.R
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * 备份文件编解码（自有文本格式，不依赖 JSON 库）。
 *
 * 设计目标：单文件、可人眼检查、跨版本可读、**能发现被截断或损坏的文件**。
 *
 * 格式 V2：
 * ```
 * CYCLE_ALARM_BACKUP_V2
 * M	<exportedAt>
 * S/A/D/T/P/H/Z ...        ← 每条记录一行，TAB 分隔
 * E	<记录条数>	<正文 sha256>
 * ```
 * 校验和覆盖「E 记录之前的全部正文行」，所以导出中途被杀、文件被截断、
 * 或人为改动任何一行都会在解码阶段被拒绝，数据库保持原样。
 *
 * 兼容：仍能读旧版 `CYCLE_ALARM_BACKUP_V1`（无校验和、A 记录只有 9 段），
 * 旧文件里的闹钟会按默认值补齐 V2 新增的四个字段（铃声/振动/渐强/提前提醒）。
 */
object BackupCodec {
    private const val HEADER_V1 = "CYCLE_ALARM_BACKUP_V1"
    private const val HEADER_V2 = "CYCLE_ALARM_BACKUP_V2"

    /** v10 的实体结构版本；备份格式将来变化时递增。 */
    private const val SCHEMA_VERSION = 10

    private const val A_SEGMENTS_V1 = 9
    private const val A_SEGMENTS_V2 = 13

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

    fun encode(backup: Backup): String {
        val body = buildString {
            append("M")
            append('\t').append(backup.exportedAt)
            append('\t').append(SCHEMA_VERSION)
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
                append('\t').append(encodeString(alarm.ringtoneUri.orEmpty()))
                append('\t').append(alarm.vibrate.flag())
                append('\t').append(alarm.rampVolume.flag())
                append('\t').append(alarm.preAlertMinutes)
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

        val recordCount = body.lineSequence().count { it.isNotBlank() }
        return buildString {
            appendLine(HEADER_V2)
            append(body)
            append("E")
            append('\t').append(recordCount)
            append('\t').append(sha256(body))
            appendLine()
        }
    }

    fun decode(payload: String): Backup {
        val allLines = payload.lineSequence().filter { it.isNotBlank() }.toList()
        val header = allLines.firstOrNull() ?: throw LocalizedFailure(R.string.err_not_backup)

        val recordLines: List<String> = when (header) {
            HEADER_V1 -> allLines.drop(1)

            HEADER_V2 -> {
                if (allLines.size < 2) throw LocalizedFailure(R.string.err_backup_head)
                val trailer = allLines.last()
                val trailerParts = trailer.split('\t')
                if (trailerParts.firstOrNull() != "E" || trailerParts.size != 3) {
                    throw LocalizedFailure(R.string.err_backup_truncated)
                }
                val declaredCount = trailerParts[1].toIntOrNull()
                    ?: throw LocalizedFailure(R.string.err_backup_truncated)
                val body = allLines.subList(1, allLines.size - 1)
                if (body.size != declaredCount) throw LocalizedFailure(R.string.err_backup_truncated)
                val actualSum = sha256(body.joinToString("\n", postfix = "\n"))
                if (!actualSum.equals(trailerParts[2], ignoreCase = true)) {
                    throw LocalizedFailure(R.string.err_backup_checksum)
                }
                body
            }

            else -> throw LocalizedFailure(R.string.err_not_backup)
        }

        var settings: AppSettingsEntity? = null
        var exportedAt = 0L
        val alarms = mutableListOf<AlarmEntity>()
        val dateAlarms = mutableListOf<DateAlarmEntity>()
        val activityTypes = mutableListOf<ActivityTypeEntity>()
        val dayPlans = mutableListOf<DayPlanEntity>()
        val holidays = mutableListOf<HolidayEntity>()
        val snoozes = mutableListOf<SnoozeEntity>()

        recordLines.forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "M" -> {
                    if (parts.size < 2) throw LocalizedFailure(R.string.err_backup_head)
                    exportedAt = parts[1].toLongOrNull()
                        ?: throw LocalizedFailure(R.string.err_backup_head)
                }

                "S" -> {
                    if (parts.size != 7) throw LocalizedFailure(R.string.err_backup_settings)
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
                    if (parts.size != A_SEGMENTS_V2 && parts.size != A_SEGMENTS_V1) {
                        throw LocalizedFailure(R.string.err_backup_cycle_alarms)
                    }
                    alarms += AlarmEntity(
                        id = parts[1].toInt(),
                        dayIndex = parts[2].toInt(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag(),
                        mutedDuringHoliday = parts[6].toBooleanFlag(),
                        planGenerated = parts[7].toBooleanFlag(),
                        label = decodeString(parts[8]),
                        // 以下四个字段只有 V2 才有，旧文件按默认值补齐
                        ringtoneUri = if (parts.size > 9) {
                            decodeString(parts[9]).ifBlank { null }
                        } else {
                            null
                        },
                        vibrate = if (parts.size > 10) parts[10].toBooleanFlag() else true,
                        rampVolume = if (parts.size > 11) parts[11].toBooleanFlag() else false,
                        preAlertMinutes = if (parts.size > 12) parts[12].toInt() else 0
                    )
                }

                "D" -> {
                    if (parts.size != 8) throw LocalizedFailure(R.string.err_backup_date_alarms)
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
                    if (parts.size != 7) throw LocalizedFailure(R.string.err_backup_types)
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
                    if (parts.size != 6) throw LocalizedFailure(R.string.err_backup_plans)
                    dayPlans += DayPlanEntity(
                        dayIndex = parts[1].toInt(),
                        typeId = parts[2].toInt(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag()
                    )
                }

                "H" -> {
                    if (parts.size != 6) throw LocalizedFailure(R.string.err_backup_holidays)
                    holidays += HolidayEntity(
                        id = parts[1].toInt(),
                        startEpochDay = parts[2].toLong(),
                        endEpochDay = parts[3].toLong(),
                        enabled = parts[4].toBooleanFlag(),
                        label = decodeString(parts[5])
                    )
                }

                "Z" -> {
                    if (parts.size != 10) throw LocalizedFailure(R.string.err_backup_snoozes)
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

                else -> throw LocalizedFailure(R.string.err_backup_unknown_record)
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

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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
        else -> throw LocalizedFailure(R.string.err_backup_bool)
    }
}
