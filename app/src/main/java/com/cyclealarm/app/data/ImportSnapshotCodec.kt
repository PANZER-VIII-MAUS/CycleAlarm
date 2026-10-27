package com.cyclealarm.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Encodes import snapshots without adding a JSON dependency. */
object ImportSnapshotCodec {
    private const val HEADER = "CYCLE_ALARM_SNAPSHOT_V1"

    fun encode(snapshot: AlarmImportWriter.Snapshot): String = buildString {
        appendLine(HEADER)
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
    }

    fun decode(payload: String): AlarmImportWriter.Snapshot {
        val lines = payload.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "不支持的导入快照" }
        var settings: AppSettingsEntity? = null
        val alarms = mutableListOf<AlarmEntity>()
        val dateAlarms = mutableListOf<DateAlarmEntity>()

        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
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

                else -> error("未知的导入快照记录")
            }
        }

        return AlarmImportWriter.Snapshot(alarms, dateAlarms, settings)
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
