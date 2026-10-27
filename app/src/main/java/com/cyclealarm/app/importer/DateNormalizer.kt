package com.cyclealarm.app.importer

import java.time.LocalDate

/** 日期归一化：文本日期与 Excel 日期序列 → LocalDate。 */
object DateNormalizer {

    private val textPattern =
        Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?""")

    /** Excel 日期序列基准（1899-12-30，兼容 Excel 的 1900 闰年 bug）。 */
    private val excelEpoch: LocalDate = LocalDate.of(1899, 12, 30)

    fun parse(text: String): LocalDate? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val m = textPattern.find(t) ?: return null
        val (y, mo, d) = m.destructured
        val year = y.toIntOrNull() ?: return null
        val month = mo.toIntOrNull() ?: return null
        val day = d.toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    fun fromExcelSerial(value: Double): LocalDate? {
        if (value < 20000.0) return null
        return runCatching { excelEpoch.plusDays(value.toLong()) }.getOrNull()
    }

    /** 单元格 → 日期（优先文本，其次 Excel 数字序列）。 */
    fun fromCell(cell: Cell?): LocalDate? {
        if (cell == null) return null
        return parse(cell.text) ?: cell.numeric?.let { fromExcelSerial(it) }
    }
}
