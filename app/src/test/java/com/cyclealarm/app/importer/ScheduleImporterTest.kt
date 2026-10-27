package com.cyclealarm.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleImporterTest {

    private fun row(vararg texts: String): List<Cell> = texts.map { Cell(it) }

    @Test
    fun `weekly grid produces weekly entries`() {
        val sheet = Sheet(
            "课表",
            listOf(
                row("节次", "周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                row("第1节", "数学", "", "英语", "", "", "", ""),
                row("第3节", "", "物理", "", "", "", "", "")
            )
        )
        val result = ScheduleImporter.import(
            sheet,
            ImportOptions(leadMinutes = 20, sectionTimes = SectionTimeTable.default())
        )
        assertEquals(3, result.entries.size)
        val math = result.entries.first { it.title == "数学" }
        assertEquals(ScheduleKind.WEEKLY, math.kind)
        assertEquals(1, math.weekday)
        assertEquals(8, math.hour)
        assertEquals(0, math.minute)
        val physics = result.entries.first { it.title == "物理" }
        assertEquals(2, physics.weekday)
        assertEquals(9, physics.hour)
        assertEquals(50, physics.minute)
    }

    @Test
    fun `dated table skips rest and maps self study`() {
        val sheet = Sheet(
            "排班",
            listOf(
                row("第N天", "日期", "星期", "课程", "教室", "教师"),
                row("第33天", "2026-10-21", "星期三", "AI工程化基础", "济南505", "申航"),
                row("", "2026-10-22", "星期四", "", "", "自习"),
                row("", "2026-10-19", "星期一", "", "", "休息")
            )
        )
        val result = ScheduleImporter.import(
            sheet,
            ImportOptions(
                leadMinutes = 20,
                fallbackTime = LocalTime.of(8, 20),
                todayEpochDay = LocalDate.of(2026, 10, 1).toEpochDay()
            )
        )
        assertEquals(2, result.entries.size)
        val course = result.entries.first { it.title == "AI工程化基础" }
        assertEquals(ScheduleKind.DATED, course.kind)
        assertEquals(LocalDate.of(2026, 10, 21).toEpochDay(), course.epochDay)
        assertEquals(8, course.hour)
        assertEquals(0, course.minute)
        val selfStudy = result.entries.first { it.title == "自习" }
        assertEquals(LocalDate.of(2026, 10, 22).toEpochDay(), selfStudy.epochDay)
        assertTrue(result.warnings.any { it.message.contains("休息") })
    }

    @Test
    fun `past dated rows are skipped`() {
        val sheet = Sheet(
            "排班",
            listOf(
                row("日期", "课程", "教师"),
                row("2026-10-05", "AI工程化基础", "申航"),
                row("2026-10-06", "AI工程化基础", "申航"),
                row("2026-10-07", "AI工程化基础", "申航")
            )
        )
        val result = ScheduleImporter.import(
            sheet,
            ImportOptions(
                fallbackTime = LocalTime.of(8, 20),
                todayEpochDay = LocalDate.of(2026, 10, 26).toEpochDay()
            )
        )
        assertTrue(result.entries.isEmpty())
        assertTrue(result.warnings.any { it.message.contains("已过去") })
    }

    @Test
    fun `cross midnight weekly row is skipped`() {
        val sheet = Sheet(
            "课表",
            listOf(
                row("时间", "周一", "周二", "周三"),
                row("00:10", "晨读", "", "")
            )
        )
        val result = ScheduleImporter.import(sheet, ImportOptions(leadMinutes = 20))
        assertTrue(result.entries.isEmpty())
        assertTrue(result.warnings.any { it.message.contains("跨到前一天") })
    }

    @Test
    fun `needs manual mapping reports warning`() {
        val sheet = Sheet("怪表", listOf(row("a", "b"), row("c", "d")))
        val result = ScheduleImporter.import(sheet, ImportOptions())
        assertTrue(result.entries.isEmpty())
        assertTrue(result.warnings.any { it.message.contains("未能自动识别") })
    }
}
