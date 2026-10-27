package com.cyclealarm.app.importer

import com.cyclealarm.app.importer.csv.CsvSheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class CsvSheetReaderTest {

    @Test
    fun `utf8 with bom`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = "周一,8:20,高等数学".toByteArray(Charsets.UTF_8)
        val sheets = CsvSheetReader().read(ByteArrayInputStream(bom + content))
        val rows = sheets.single().rows
        assertEquals("周一", rows[0][0].text)
        assertEquals("高等数学", rows[0][2].text)
    }

    @Test
    fun `gb18030 fallback`() {
        val bytes = "周二,9:15,大学英语".toByteArray(Charset.forName("GB18030"))
        val sheets = CsvSheetReader().read(ByteArrayInputStream(bytes))
        val rows = sheets.single().rows
        assertEquals("周二", rows[0][0].text)
        assertEquals("大学英语", rows[0][2].text)
    }

    @Test
    fun `quoted comma and escaped quote`() {
        val content = "\"a,b\",\"c\"\"d\"\n1,2"
        val sheets = CsvSheetReader().read(ByteArrayInputStream(content.toByteArray()))
        val rows = sheets.single().rows
        assertEquals("a,b", rows[0][0].text)
        assertEquals("c\"d", rows[0][1].text)
        assertEquals("1", rows[1][0].text)
    }

    @Test
    fun `tab separated`() {
        val content = "周一\t8:20\t自习\n周二\t9:15\t休息"
        val sheets = CsvSheetReader().read(ByteArrayInputStream(content.toByteArray()))
        val rows = sheets.single().rows
        assertEquals("周一", rows[0][0].text)
        assertEquals("自习", rows[0][2].text)
    }

    @Test
    fun `numbers are captured as numeric`() {
        val sheets = CsvSheetReader().read(ByteArrayInputStream("0.34375,x".toByteArray()))
        assertTrue(sheets.single().rows[0][0].numeric == 0.34375)
    }
}
