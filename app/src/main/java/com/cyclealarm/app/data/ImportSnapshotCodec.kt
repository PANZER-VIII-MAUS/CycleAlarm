package com.cyclealarm.app.data

import com.cyclealarm.app.core.LocalizedFailure
import com.cyclealarm.app.R
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 导入快照编解码（无 JSON 依赖）。
 *
 * - V1：alarms / date_alarms / settings
 * - V2：在 V1 基础上增加 day_plans、activity_types 与 batchId（同一批导入的标识）
 *
 * 解码同时接受 V1（老快照升级上来的）：缺失列表按空处理，撤销时不会去覆盖
 * 用户当前的安排数据。
 */
object ImportSnapshotCodec {
    private const val HEADER_V1 = "CYCLE_ALARM_SNAPSHOT_V1"
    private const val HEADER_V2 = "CYCLE_ALARM_SNAPSHOT_V2"

    /**
     * V3：A 记录补上 v10 的四个字段（铃声 / 振动 / 渐强 / 提前提醒）。
     * 没有它的话，撤销一次导入会把所有闹钟的响铃设置重置回默认值。
     */
    private const val HEADER_V3 = "CYCLE_ALARM_SNAPSHOT_V3"

    private const val A_SEGMENTS_V1 = 9
    private const val A_SEGMENTS_V3 = 13

    fun encode(snapshot: AlarmImportWriter.Snapshot): String = buildString {
        appendLine(HEADER_V3)
        append("B")
        append('\t').append(snapshot.batchId)
        appendLine()
        snapshot.settings?.let { settings ->
            append("S")
            append('\t').append(settings.id)
            append('\t').append(settings.cycleLength)
            append('\t').append(settings.cycleStartEpochDay)
            append('\t').append(encodeString(settings.cycleType))
            append('\t').append(settings.holidayModeEnabled.flag())
            append('\t').append(settings.plansSeeded.flag())
            appendLine()
        }
        snapshot.alarms.forEach { alarm ->
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
        snapshot.dateAlarms.forEach { alarm ->
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
        snapshot.activityTypes.forEach { type ->
            append("T")
            append('\t').append(type.id)
            append('\t').append(encodeString(type.name))
            append('\t').append(type.hour)
            append('\t').append(type.minute)
            append('\t').append(type.rings.flag())
            append('\t').append(type.sortOrder)
            appendLine()
        }
        snapshot.dayPlans.forEach { plan ->
            append("P")
            append('\t').append(plan.dayIndex)
            append('\t').append(plan.typeId)
            append('\t').append(plan.hour)
            append('\t').append(plan.minute)
            append('\t').append(plan.enabled.flag())
            appendLine()
        }
    }

    fun decode(payload: String): AlarmImportWriter.Snapshot {
        val lines = payload.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()
        if (header != HEADER_V1 && header != HEADER_V2 && header != HEADER_V3) {
            throw LocalizedFailure(R.string.err_snapshot_unsupported)
        }
        var settings: AppSettingsEntity? = null
        var batchId = 0L
        val alarms = mutableListOf<AlarmEntity>()
        val dateAlarms = mutableListOf<DateAlarmEntity>()
        val activityTypes = mutableListOf<ActivityTypeEntity>()
        val dayPlans = mutableListOf<DayPlanEntity>()

        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "B" -> {
                    if (parts.size != 2) throw LocalizedFailure(R.string.err_snapshot_batch)
                    batchId = parts[1].toLong()
                }

                "S" -> {
                    if (parts.size != 7) throw LocalizedFailure(R.string.err_snapshot_settings)
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
                    if (parts.size != A_SEGMENTS_V3 && parts.size != A_SEGMENTS_V1) {
                        throw LocalizedFailure(R.string.err_snapshot_cycle_alarms)
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
                        // V1/V2 快照没有这四个字段 → 用默认值补齐
                        ringtoneUri = if (parts.size > 9) decodeString(parts[9]).ifBlank { null } else null,
                        vibrate = if (parts.size > 10) parts[10].toBooleanFlag() else true,
                        rampVolume = if (parts.size > 11) parts[11].toBooleanFlag() else false,
                        preAlertMinutes = if (parts.size > 12) parts[12].toInt() else 0
                    )
                }

                "D" -> {
                    if (parts.size != 8) throw LocalizedFailure(R.string.err_snapshot_date_alarms)
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
                    if (parts.size != 7) throw LocalizedFailure(R.string.err_snapshot_types)
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
                    if (parts.size != 6) throw LocalizedFailure(R.string.err_snapshot_plans)
                    dayPlans += DayPlanEntity(
                        dayIndex = parts[1].toInt(),
                        typeId = parts[2].toInt(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag()
                    )
                }

                else -> throw LocalizedFailure(R.string.err_snapshot_unknown_record)
            }
        }

        return AlarmImportWriter.Snapshot(
            alarms = alarms,
            dateAlarms = dateAlarms,
            settings = settings,
            dayPlans = dayPlans,
            activityTypes = activityTypes,
            batchId = batchId
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
        else -> throw LocalizedFailure(R.string.err_snapshot_bool)
    }
}
