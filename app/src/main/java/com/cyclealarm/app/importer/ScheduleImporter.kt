package com.cyclealarm.app.importer

import java.time.LocalDate
import java.time.LocalTime

/**
 * 编排：Sheet + 结构 → ImportedEntry 列表 + warnings。
 * 这里只做"识别与归一化"，绝不写库、绝不猜结构。
 */
object ScheduleImporter {

    private val weekHintPattern = Regex("""单周|双周|第?\s*\d{1,2}\s*[-–—~至]\s*\d{1,2}\s*周|\d{1,2}\s*周""")
    private val restKeywords = listOf("休息", "放假", "无课")
    private val metadataPattern = Regex("""^第?\s*\d{1,3}\s*天?$|^星期[一二三四五六日天]$|^周[一二三四五六日天]$""")

    fun import(
        sheet: Sheet,
        options: ImportOptions,
        layout: DetectedLayout? = null
    ): ImportResult {
        val warnings = mutableListOf<ImportWarning>()
        val detected = layout ?: LayoutDetector.detect(sheet)
        val raw = when (detected) {
            is DetectedLayout.WeeklyGrid -> importWeekly(sheet, detected, options, warnings)
            is DetectedLayout.Dated -> importDated(sheet, detected, options, warnings)
            DetectedLayout.NeedsManual -> {
                warnings += ImportWarning("未能自动识别表格结构，请手动指定表头行、星期列与时间列")
                emptyList()
            }
        }
        return ImportResult(dedupe(raw), warnings)
    }

    // ------------------------------------------------------------------
    // 周课表 / 周排班
    // ------------------------------------------------------------------

    private fun importWeekly(
        sheet: Sheet,
        layout: DetectedLayout.WeeklyGrid,
        options: ImportOptions,
        warnings: MutableList<ImportWarning>
    ): List<ImportedEntry> {
        val result = mutableListOf<ImportedEntry>()
        for (row in layout.dataStartRow until sheet.rowCount) {
            val rowText = rowText(sheet, row)
            if (rowText.isBlank()) continue
            val start = resolveRowTime(sheet, row, layout.timeColumn, options)
            if (start == null) {
                if (layout.dayColumns.values.any { sheet.textAt(row, it).isNotBlank() }) {
                    warnings += ImportWarning(
                        "第 ${row + 1} 行未识别到时间，已跳过",
                        sourceRef(sheet, row, layout.dayColumns.values.firstOrNull() ?: 0)
                    )
                }
                continue
            }
            val alarmMinutes = start.hour * 60 + start.minute - options.leadMinutes
            if (alarmMinutes < 0) {
                warnings += ImportWarning(
                    "第 ${row + 1} 行 ${start} 提前 ${options.leadMinutes} 分钟后跨到前一天，已跳过",
                    sourceRef(sheet, row, layout.timeColumn ?: 0)
                )
                continue
            }
            val alarmTime = LocalTime.of(alarmMinutes / 60, alarmMinutes % 60)
            layout.dayColumns.forEach { (weekday, col) ->
                val text = firstLine(sheet.textAt(row, col))
                if (text.isEmpty()) return@forEach
                val ref = sourceRef(sheet, row, col)
                if (isRest(text)) {
                    warnings += ImportWarning("第 ${row + 1} 行「$text」已跳过", ref)
                    return@forEach
                }
                if (weekHintPattern.containsMatchIn(text)) {
                    warnings += ImportWarning("检测到周次限制「$text」，本期按每周重复导入", ref)
                }
                result += ImportedEntry(
                    kind = ScheduleKind.WEEKLY,
                    weekday = weekday,
                    epochDay = null,
                    hour = alarmTime.hour,
                    minute = alarmTime.minute,
                    title = text,
                    sourceRef = ref
                )
            }
        }
        return result
    }

    // ------------------------------------------------------------------
    // 按日期排班
    // ------------------------------------------------------------------

    private fun importDated(
        sheet: Sheet,
        layout: DetectedLayout.Dated,
        options: ImportOptions,
        warnings: MutableList<ImportWarning>
    ): List<ImportedEntry> {
        val result = mutableListOf<ImportedEntry>()
        for (row in layout.dataStartRow until sheet.rowCount) {
            val date = DateNormalizer.fromCell(sheet.cellAt(row, layout.dateColumn)) ?: continue
            val rowText = rowText(sheet, row)
            val ref = sourceRef(sheet, row, layout.dateColumn)

            if (isRest(rowText)) {
                val hint = restKeywords.firstOrNull { rowText.contains(it) } ?: "休息"
                warnings += ImportWarning("$date $hint，已跳过", ref)
                continue
            }

            val selfStudy = rowText.contains("自习")
            val start = if (selfStudy) {
                options.fallbackTime
            } else {
                resolveRowTime(sheet, row, layout.timeColumn, options) ?: options.fallbackTime
            }

            var alarmDate = date
            var alarmMinutes = start.hour * 60 + start.minute - options.leadMinutes
            if (alarmMinutes < 0) {
                // 跨零点：日期退一天
                alarmMinutes += 24 * 60
                alarmDate = date.minusDays(1)
            }
            val alarmTime = LocalTime.of(alarmMinutes / 60, alarmMinutes % 60)

            if (alarmDate.toEpochDay() < options.todayEpochDay) {
                warnings += ImportWarning("$date 已过去，已跳过", ref)
                continue
            }

            val title = if (selfStudy) {
                "自习"
            } else {
                firstTitle(sheet, row, layout.titleColumns).ifBlank { "课程" }
            }
            if (weekHintPattern.containsMatchIn(rowText)) {
                warnings += ImportWarning("$date 检测到周次限制，按单次导入", ref)
            }

            result += ImportedEntry(
                kind = ScheduleKind.DATED,
                weekday = null,
                epochDay = alarmDate.toEpochDay(),
                hour = alarmTime.hour,
                minute = alarmTime.minute,
                title = title,
                sourceRef = ref
            )
        }
        return result
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun resolveRowTime(
        sheet: Sheet,
        row: Int,
        timeColumn: Int?,
        options: ImportOptions
    ): LocalTime? {
        if (timeColumn != null) {
            val cell = sheet.cellAt(row, timeColumn) ?: return null
            sectionTime(cell.text, options)?.let { return it }
            LayoutDetector.parseTimeCell(cell)?.let { return it }
        }
        // 没有时间列时，整行找节次 / 时间
        for (col in 0 until sheet.colCount) {
            val cell = sheet.cellAt(row, col) ?: continue
            sectionTime(cell.text, options)?.let { return it }
            LayoutDetector.parseTimeCell(cell)?.let { return it }
        }
        return null
    }

    private fun sectionTime(text: String, options: ImportOptions): LocalTime? {
        val section = SectionTimeTable.parseSection(text) ?: return null
        return options.sectionTimes[section] ?: SectionTimeTable.default()[section]
    }

    private fun firstTitle(sheet: Sheet, row: Int, titleColumns: List<Int>): String {
        titleColumns.forEach { col ->
            val text = firstLine(sheet.textAt(row, col))
            if (text.isNotEmpty() && !metadataPattern.matches(text)) return text
        }
        return ""
    }

    private fun rowText(sheet: Sheet, row: Int): String =
        (0 until sheet.colCount).joinToString(" ") { sheet.textAt(row, it) }

    private fun isRest(text: String): Boolean = restKeywords.any { text.contains(it) }

    private fun firstLine(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    private fun sourceRef(sheet: Sheet, row: Int, col: Int): String =
        "${sheet.name}!${columnName(col)}${row + 1}"

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.append(('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.reverse().toString()
    }

    private fun dedupe(entries: List<ImportedEntry>): List<ImportedEntry> {
        val map = LinkedHashMap<String, ImportedEntry>()
        entries.forEach { entry ->
            val key = "${entry.kind}|${entry.weekday ?: entry.epochDay ?: 0}|${entry.hour}|${entry.minute}"
            val old = map[key]
            map[key] = if (old == null) entry else old.copy(title = old.title.ifBlank { entry.title })
        }
        return map.values.toList()
    }
}
