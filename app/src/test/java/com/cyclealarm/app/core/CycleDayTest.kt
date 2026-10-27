package com.cyclealarm.app.core

import com.cyclealarm.app.data.AppSettingsEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleDayTest {

    @Test
    fun `one day cycle always returns day one`() {
        val settings = settings(start = LocalDate.of(2026, 1, 1), length = 1)
        assertEquals(1, cycleDayOf(settings, LocalDate.of(2030, 5, 1).toEpochDay()))
    }

    @Test
    fun `three day cycle crosses month boundary`() {
        val settings = settings(start = LocalDate.of(2026, 1, 31), length = 3)
        assertEquals(2, cycleDayOf(settings, LocalDate.of(2026, 2, 1).toEpochDay()))
    }

    @Test
    fun `seven day cycle crosses year boundary`() {
        val settings = settings(start = LocalDate.of(2025, 12, 30), length = 7)
        assertEquals(3, cycleDayOf(settings, LocalDate.of(2026, 1, 1).toEpochDay()))
    }

    @Test
    fun `negative offset remains in one based cycle range`() {
        val settings = settings(start = LocalDate.of(2026, 1, 10), length = 3)
        val day = cycleDayOf(settings, LocalDate.of(2026, 1, 8).toEpochDay())

        assertEquals(2, day)
    }

    @Test
    fun `zero or negative stored length is coerced to one`() {
        val settings = settings(start = LocalDate.of(2026, 1, 10), length = 0)
        assertEquals(1, cycleDayOf(settings, LocalDate.of(2026, 1, 11).toEpochDay()))
    }

    private fun settings(start: LocalDate, length: Int) = AppSettingsEntity(
        cycleLength = length,
        cycleStartEpochDay = start.toEpochDay()
    )
}
