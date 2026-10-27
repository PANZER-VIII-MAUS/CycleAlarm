package com.cyclealarm.app.importer

import java.time.LocalTime

/** 表格结构探测结果。 */
sealed interface DetectedLayout {
    /** 周课表 / 周排班：列 = 星期，行 = 节次 / 时间。 */
    data class WeeklyGrid(
        val headerRow: Int,
        val dayColumns: Map<Int, Int>,
        val timeColumn: Int?,
        val titleColumns: List<Int>,
        val dataStartRow: Int
    ) : DetectedLayout

    /** 按日期排班：某列是具体日期。 */
    data class Dated(
        val headerRow: Int,
        val dateColumn: Int,
        val weekdayColumn: Int?,
        val timeColumn: Int?,
        val titleColumns: List<Int>,
        val dataStartRow: Int
    ) : DetectedLayout

    /** 自动探测失败，交给用户手动指定。 */
    data object NeedsManual : DetectedLayout
}

/**
 * 表格结构探测：先找"一行里有 ≥3 个星期标签"（周课表），
 * 再找"一列里有 ≥3 个日期"（按日期排班表）；都找不到就返回 NeedsManual，绝不猜。
 */
object LayoutDetector {

    private val weekdayKeywords: Map<Int, List<String>> = mapOf(
        1 to listOf("周一", "星期一", "礼拜一", "周1", "星期1", "mon", "monday"),
        2 to listOf("周二", "星期二", "礼拜二", "周2", "星期2", "tue", "tuesday"),
        3 to listOf("周三", "星期三", "礼拜三", "周3", "星期3", "wed", "wednesday"),
        4 to listOf("周四", "星期四", "礼拜四", "周4", "星期4", "thu", "thursday"),
        5 to listOf("周五", "星期五", "礼拜五", "周5", "星期5", "fri", "friday"),
        6 to listOf("周六", "星期六", "礼拜六", "周6", "星期6", "sat", "saturday"),
        7 to listOf("周日", "周天", "星期日", "星期天", "礼拜日", "礼拜天", "周7", "星期7", "sun", "sunday")
    )

    fun weekdayOf(text: String): Int? {
        val t = text.trim().lowercase().replace(" ", "")
        if (t.isEmpty()) return null
        return weekdayKeywords.entries.firstOrNull { (_, keys) ->
            keys.any { key -> t == key || t.startsWith(key) }
        }?.key
    }

    fun detect(sheet: Sheet): DetectedLayout {
        detectWeekly(sheet)?.let { return it }
        detectDated(sheet)?.let { return it }
        return DetectedLayout.NeedsManual
    }

    private fun detectWeekly(sheet: Sheet): DetectedLayout.WeeklyGrid? {
        val scanRows = minOf(sheet.rowCount, 15)
        for (row in 0 until scanRows) {
            val dayColumns = linkedMapOf<Int, Int>()
            for (col in 0 until sheet.colCount) {
                val weekday = weekdayOf(sheet.textAt(row, col)) ?: continue
                if (!dayColumns.containsKey(weekday)) dayColumns[weekday] = col
            }
            if (dayColumns.size >= 3) {
                val dataStart = row + 1
                val timeColumn = (0 until sheet.colCount).firstOrNull { col ->
                    !dayColumns.containsValue(col) && countTimeCells(sheet, col, dataStart) >= 1
                }
                val titleColumns = (0 until sheet.colCount).filter { col ->
                    col != timeColumn && !dayColumns.containsValue(col)
                }
                return DetectedLayout.WeeklyGrid(
                    headerRow = row,
                    dayColumns = dayColumns,
                    timeColumn = timeColumn,
                    titleColumns = titleColumns,
                    dataStartRow = dataStart
                )
            }
        }
        return null
    }

    private fun detectDated(sheet: Sheet): DetectedLayout.Dated? {
        val scanCols = minOf(sheet.colCount, 30)
        val scanRows = minOf(sheet.rowCount, 300)
        for (col in 0 until scanCols) {
            val dateRows = (0 until scanRows).filter { row ->
                DateNormalizer.fromCell(sheet.cellAt(row, col)) != null
            }
            if (dateRows.size < 3) continue
            val dataStart = dateRows.first()
            val headerRow = (dataStart - 1).coerceAtLeast(0)
            val weekdayColumn = (0 until sheet.colCount).firstOrNull { c ->
                c != col && countWeekdayCells(sheet, c, dataStart) >= 2
            }
            val timeColumn = (0 until sheet.colCount).firstOrNull { c ->
                c != col && c != weekdayColumn && countTimeCells(sheet, c, dataStart) >= 2
            }
            val titleColumns = (0 until sheet.colCount).filter { c ->
                c != col && c != weekdayColumn && c != timeColumn
            }
            return DetectedLayout.Dated(
                headerRow = headerRow,
                dateColumn = col,
                weekdayColumn = weekdayColumn,
                timeColumn = timeColumn,
                titleColumns = titleColumns,
                dataStartRow = dataStart
            )
        }
        return null
    }

    internal fun countWeekdayCells(sheet: Sheet, col: Int, fromRow: Int): Int =
        (fromRow until sheet.rowCount).count { row -> weekdayOf(sheet.textAt(row, col)) != null }

    internal fun countTimeCells(sheet: Sheet, col: Int, fromRow: Int): Int =
        (fromRow until sheet.rowCount).count { row ->
            val cell = sheet.cellAt(row, col) ?: return@count false
            parseTimeCell(cell) != null
        }

    /** 单元格 → 时间：文本时间 / 节次（按默认节次表）/ Excel 时间序列。 */
    internal fun parseTimeCell(cell: Cell): LocalTime? {
        TimeNormalizer.parse(cell.text)?.let { return it }
        cell.numeric?.let { value -> TimeNormalizer.fromExcelSerial(value)?.let { return it } }
        return null
    }
}
