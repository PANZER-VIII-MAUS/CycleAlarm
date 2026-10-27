package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPrefsTest {

    @Test
    fun `24 hour format keeps midnight noon and afternoon unambiguous`() {
        assertEquals("00:00", DisplayPrefs.formatTime(true, 0, 0))
        assertEquals("12:00", DisplayPrefs.formatTime(true, 12, 0))
        assertEquals("12:30", DisplayPrefs.formatTime(true, 12, 30))
        assertEquals("23:05", DisplayPrefs.formatTime(true, 23, 5))
    }

    @Test
    fun `12 hour format distinguishes midnight and noon`() {
        assertEquals("上午 12:00", DisplayPrefs.formatTime(false, 0, 0))
        assertEquals("下午 12:00", DisplayPrefs.formatTime(false, 12, 0))
        assertEquals("下午 12:30", DisplayPrefs.formatTime(false, 12, 30))
        assertEquals("下午 11:05", DisplayPrefs.formatTime(false, 23, 5))
    }

    @Test
    fun `compact clock omits day period but uses correct hour`() {
        assertEquals("00:00", DisplayPrefs.formatClock(true, 0, 0))
        assertEquals("12:00", DisplayPrefs.formatClock(true, 12, 0))
        assertEquals("12:00", DisplayPrefs.formatClock(false, 0, 0))
        assertEquals("12:30", DisplayPrefs.formatClock(false, 12, 30))
    }
}
