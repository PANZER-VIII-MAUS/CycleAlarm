package com.cyclealarm.app.importer

import com.cyclealarm.app.importer.xlsx.XlsxSheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxSheetReaderTest {

    @Test
    fun `reads shared strings and numeric cells`() {
        val sheets = read(buildXlsx(sheetXml = sheetWithSharedAndNumeric()))
        val rows = sheets.single().rows
        assertEquals("高等数学", rows[1][1].text)
        assertEquals(0.34375, rows[0][0].numeric!!, 0.000001)
    }

    @Test
    fun `rich text shared string is concatenated`() {
        val sheets = read(buildXlsx(sheetXml = sheetReferencingRichString()))
        assertEquals("AB", sheets.single().rows[0][0].text)
    }

    @Test
    fun `inline string is supported`() {
        val sheets = read(buildXlsx(sheetXml = sheetWithInlineString()))
        assertEquals("自习", sheets.single().rows[2][1].text)
    }

    @Test
    fun `merged cells are expanded`() {
        val sheets = read(buildXlsx(sheetXml = sheetWithMergedCells()))
        val rows = sheets.single().rows
        assertEquals("合并", rows[3][0].text)
        assertEquals("合并", rows[3][1].text)
    }

    @Test
    fun `skipped refs keep positions`() {
        val sheets = read(buildXlsx(sheetXml = sheetWithSkippedRefs()))
        val rows = sheets.single().rows
        assertTrue(rows.size > 5)
        assertEquals("跳号", rows[5][2].text)
    }

    @Test
    fun `multiple sheets can be selected by name`() {
        val bytes = buildXlsx(sheetXml = sheetWithInlineString(), secondSheet = true)
        val all = XlsxSheetReader().read(ByteArrayInputStream(bytes))
        assertEquals(2, all.size)
        val onlySecond = XlsxSheetReader().read(ByteArrayInputStream(bytes), "Sheet2")
        assertEquals("Sheet2", onlySecond.single().name)
    }

    // ------------------------------------------------------------------

    private fun read(bytes: ByteArray): List<Sheet> =
        XlsxSheetReader().read(ByteArrayInputStream(bytes))

    private fun sheetWithSharedAndNumeric(): String = """
        <worksheet><sheetData>
          <row r="1"><c r="A1"><v>0.34375</v></c></row>
          <row r="2"><c r="B2" t="s"><v>0</v></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    private fun sheetReferencingRichString(): String = """
        <worksheet><sheetData>
          <row r="1"><c r="A1" t="s"><v>1</v></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    private fun sheetWithInlineString(): String = """
        <worksheet><sheetData>
          <row r="3"><c r="B3" t="inlineStr"><is><t>自习</t></is></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    private fun sheetWithMergedCells(): String = """
        <worksheet><sheetData>
          <row r="4"><c r="A4" t="inlineStr"><is><t>合并</t></is></c></row>
        </sheetData>
        <mergeCells><mergeCell ref="A4:B4"/></mergeCells></worksheet>
    """.trimIndent()

    private fun sheetWithSkippedRefs(): String = """
        <worksheet><sheetData>
          <row r="6"><c r="C6" t="inlineStr"><is><t>跳号</t></is></c></row>
        </sheetData></worksheet>
    """.trimIndent()

    private fun buildXlsx(sheetXml: String, secondSheet: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val sheetsXml = buildString {
                append("""<sheet name="Sheet1" sheetId="1" r:id="rId1"/>""")
                if (secondSheet) append("""<sheet name="Sheet2" sheetId="2" r:id="rId2"/>""")
            }
            put(
                "xl/workbook.xml",
                """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheetsXml</sheets></workbook>"""
            )
            val rels = buildString {
                append("""<Relationship Id="rId1" Target="worksheets/sheet1.xml"/>""")
                if (secondSheet) append("""<Relationship Id="rId2" Target="worksheets/sheet2.xml"/>""")
            }
            put("xl/_rels/workbook.xml.rels", "<Relationships>$rels</Relationships>")
            put(
                "xl/sharedStrings.xml",
                """<sst><si><t>高等数学</t></si><si><r><t>A</t></r><r><t>B</t></r></si></sst>"""
            )
            put("xl/worksheets/sheet1.xml", sheetXml)
            if (secondSheet) put("xl/worksheets/sheet2.xml", sheetWithInlineString())
        }
        return out.toByteArray()
    }
}
