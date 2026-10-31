package com.cyclealarm.app.data

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

    fun encode(snapshot: AlarmImportWriter.Snapshot): String = buildString {
        appendLine(HEADER_V2)
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
        require(header == HEADER_V1 || header == HEADER_V2) { "不支持的导入快照" }
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
                    require(parts.size == 2) { "导入快照批次格式错误" }
                    batchId = parts[1].toLong()
                }

                "S" -> {
                    require(parts.size == 7) { "导入快照设置格式错误" }
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
                    require(parts.size == 9) { "导入快照周期闹钟格式错误" }
                    alarms += AlarmEntity(
                        id = parts[1].toInt(),
                        dayIndex = parts[2].toInt(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag(),
                        mutedDuringHoliday = parts[6].toBooleanFlag(),
                        label = decodeString(parts[8]),
                        planGenerated = parts[7].toBooleanFlag()
                    )
                }

                "D" -> {
                    require(parts.size == 8) { "导入快照日期闹钟格式错误" }
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
                    require(parts.size == 7) { "导入快照安排类型格式错误" }
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
                    require(parts.size == 6) { "导入快照安排格式错误" }
                    dayPlans += DayPlanEntity(
                        dayIndex = parts[1].toInt(),
                        typeId = parts[2].toInt(),
                        hour = parts[3].toInt(),
                        minute = parts[4].toInt(),
                        enabled = parts[5].toBooleanFlag()
                    )
                }

                else -> error("未知的导入快照记录")
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
        else -> error("导入快照布尔值格式错误")
    }
}
