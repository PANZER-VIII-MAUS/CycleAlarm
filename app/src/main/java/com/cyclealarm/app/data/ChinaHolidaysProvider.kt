package com.cyclealarm.app.data

import java.time.LocalDate

/**
 * 中国法定节假日数据（国务院办公厅发布的放假安排，含调休后的连休日期）。
 *
 * 只有官方正式公布的年份才写入；新一年通知发布后（通常是 11 月底前后），
 * 在 [official] 里追加即可。导入时 [upcomingOrActive] 会过滤掉已经过去的假期，
 * 所以历史年份的数据不会出现在用户列表里。
 */
object ChinaHolidaysProvider {

    private data class Official(
        val label: String,
        val start: LocalDate,
        val end: LocalDate
    )

    /**
     * 官方放假安排（含调休连休）：
     * - 2025：国办发明电〔2024〕16 号
     * - 2026：国办发明电〔2025〕7 号（2025-11-04 发布）
     * - 2027 年安排尚未公布，公布后追加
     */
    private val official: List<Official> = listOf(
        // 2025
        Official("元旦 2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)),
        Official("春节 2025", LocalDate.of(2025, 1, 28), LocalDate.of(2025, 2, 4)),
        Official("清明 2025", LocalDate.of(2025, 4, 4), LocalDate.of(2025, 4, 6)),
        Official("劳动节 2025", LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 5)),
        Official("端午 2025", LocalDate.of(2025, 5, 31), LocalDate.of(2025, 6, 2)),
        Official("中秋国庆 2025", LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 8)),
        // 2026
        Official("元旦 2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)),
        Official("春节 2026", LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23)),
        Official("清明 2026", LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6)),
        Official("劳动节 2026", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)),
        Official("端午 2026", LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21)),
        Official("中秋 2026", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27)),
        Official("国庆 2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))
    )

    /** 全部官方假期（未过滤，含历史年份）。 */
    fun all(): List<HolidayEntity> = official.map { o ->
        HolidayEntity(
            startEpochDay = o.start.toEpochDay(),
            endEpochDay = o.end.toEpochDay(),
            enabled = true,
            label = o.label
        )
    }

    /** 只保留还没完全过去的假期（endEpochDay >= 今天）。 */
    fun upcomingOrActive(today: LocalDate = LocalDate.now()): List<HolidayEntity> =
        all().filter { it.endEpochDay >= today.toEpochDay() }
}
