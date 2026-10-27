package com.cyclealarm.app.importer

/**
 * 一个单元格的解析结果。
 * numeric 非空表示原始值就是数字（Excel 里日期/时间本质都是数字序列）。
 */
data class Cell(
    val text: String,
    val numeric: Double? = null,
    val isTimeFormatted: Boolean = false
)

/** 一个工作表；CSV 也视为只有一个 sheet。 */
data class Sheet(val name: String, val rows: List<List<Cell>>) {
    val rowCount: Int get() = rows.size
    val colCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    fun cellAt(row: Int, col: Int): Cell? = rows.getOrNull(row)?.getOrNull(col)

    fun textAt(row: Int, col: Int): String = cellAt(row, col)?.text?.trim().orEmpty()
}
