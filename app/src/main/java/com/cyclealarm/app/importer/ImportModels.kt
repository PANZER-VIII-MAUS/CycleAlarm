package com.cyclealarm.app.importer

import java.time.LocalDate
import java.time.LocalTime

/** 导入结果的两种形态：周循环闹钟 / 按日期一次性闹钟。 */
enum class ScheduleKind { WEEKLY, DATED }

/**
 * 一条待导入的闹钟（识别结果，尚未写库）。
 * WEEKLY：weekday 为 1..7，epochDay 为 null；
 * DATED ：epochDay 为日期，weekday 可为 null。
 */
data class ImportedEntry(
    val kind: ScheduleKind,
    val weekday: Int?,
    val epochDay: Long?,
    val hour: Int,
    val minute: Int,
    val title: String = "",
    val sourceRef: String = ""
)

data class ImportWarning(val message: String, val sourceRef: String = "")

data class ImportResult(
    val entries: List<ImportedEntry>,
    val warnings: List<ImportWarning>
)

data class ImportOptions(
    /** 闹钟提前量：闹钟时间 = 上课/上班时间 - leadMinutes。 */
    val leadMinutes: Int = 20,
    val overwrite: Boolean = false,
    val sectionTimes: Map<Int, LocalTime> = SectionTimeTable.default(),
    /** 表格里没有时间列时使用的默认上课时间（按日期表格常见）。 */
    val fallbackTime: LocalTime = LocalTime.of(8, 20),
    val todayEpochDay: Long = LocalDate.now().toEpochDay()
)
