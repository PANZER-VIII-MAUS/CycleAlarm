package com.cyclealarm.app.data

import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class ChinaHolidaysProviderTest {

    @Test
    fun `official holiday list is consistent`() {
        val holidays = ChinaHolidaysProvider.all()

        assertTrue("应当至少有一条官方假期", holidays.isNotEmpty())
        holidays.forEach { holiday ->
            assertTrue("标签不能为空", holiday.label.isNotBlank())
            assertTrue(
                "起止日期顺序错误：${holiday.label}",
                holiday.startEpochDay <= holiday.endEpochDay
            )
            assertTrue("假期默认应生效", holiday.enabled)
        }
    }

    @Test
    fun `upcoming or active only returns future ranges`() {
        val today = LocalDate.of(2026, 11, 1)
        val upcoming = ChinaHolidaysProvider.upcomingOrActive(today)

        upcoming.forEach { holiday ->
            assertTrue(
                "已过去的假期不应返回：${holiday.label}",
                holiday.endEpochDay >= today.toEpochDay()
            )
        }
    }
}
