package com.cyclealarm.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TimeNormalizerTest {

    @Test
    fun `standard colon time`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("8:20"))
    }

    @Test
    fun `zero padded time`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("08:20"))
    }

    @Test
    fun `full width colon`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("8：20"))
    }

    @Test
    fun `chinese dian`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("8点20"))
    }

    @Test
    fun `am prefix`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("上午8:20"))
    }

    @Test
    fun `pm prefix converts to 24h`() {
        assertEquals(LocalTime.of(14, 0), TimeNormalizer.parse("下午2:00"))
    }

    @Test
    fun `evening prefix converts to 24h`() {
        assertEquals(LocalTime.of(20, 0), TimeNormalizer.parse("晚上8:00"))
    }

    @Test
    fun `morning prefix keeps hour`() {
        assertEquals(LocalTime.of(7, 30), TimeNormalizer.parse("早上7:30"))
    }

    @Test
    fun `noon 12 stays`() {
        assertEquals(LocalTime.of(12, 30), TimeNormalizer.parse("中午12:30"))
    }

    @Test
    fun `noon 1 becomes 13`() {
        assertEquals(LocalTime.of(13, 0), TimeNormalizer.parse("中午1:00"))
    }

    @Test
    fun `range takes start`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("8:20-9:55"))
    }

    @Test
    fun `range with tilde takes start`() {
        assertEquals(LocalTime.of(10, 10), TimeNormalizer.parse("10:10~11:45"))
    }

    @Test
    fun `section with prefix`() {
        assertEquals(LocalTime.of(10, 10), TimeNormalizer.parse("第3节"))
    }

    @Test
    fun `section range takes first`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("第1-2节"))
    }

    @Test
    fun `section range without prefix`() {
        assertEquals(LocalTime.of(8, 20), TimeNormalizer.parse("1-2节"))
    }

    @Test
    fun `excel serial to time`() {
        assertEquals(LocalTime.of(8, 15), TimeNormalizer.fromExcelSerial(0.34375))
    }

    @Test
    fun `excel serial no truncation`() {
        // 0.34375 * 1440 = 495.0 分钟；0.34305 约 494.0
        assertEquals(LocalTime.of(8, 14), TimeNormalizer.fromExcelSerial(0.34305))
    }

    @Test
    fun `excel serial out of range`() {
        assertNull(TimeNormalizer.fromExcelSerial(1.5))
        assertNull(TimeNormalizer.fromExcelSerial(-0.1))
        assertNull(TimeNormalizer.fromExcelSerial(0.0))
    }

    @Test
    fun `plain text returns null`() {
        assertNull(TimeNormalizer.parse("高等数学"))
    }

    @Test
    fun `empty returns null`() {
        assertNull(TimeNormalizer.parse(""))
        assertNull(TimeNormalizer.parse("   "))
    }

    @Test
    fun `invalid clock returns null`() {
        assertNull(TimeNormalizer.parse("25:99"))
    }

    @Test
    fun `pure number is not a time`() {
        assertNull(TimeNormalizer.parse("3"))
    }

    @Test
    fun `period only returns null`() {
        assertNull(TimeNormalizer.parse("下午"))
    }

    @Test
    fun `24 hours maps to midnight`() {
        assertEquals(LocalTime.of(0, 0), TimeNormalizer.parse("24:00"))
    }
}
