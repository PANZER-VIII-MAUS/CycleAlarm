package com.cyclealarm.app.importer.xlsx

import com.cyclealarm.app.importer.Cell
import com.cyclealarm.app.importer.Sheet
import com.cyclealarm.app.importer.SheetReadException
import com.cyclealarm.app.importer.SheetReader
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * 零依赖 xlsx 解析：把 xlsx 当成 zip，逐个读取需要用到的 XML，
 * 用 JDK 自带的 SAX 解析（可跑纯 JVM 单测，不用 Apache POI）。
 */
class XlsxSheetReader : SheetReader {

    private companion object {
        const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
        const val MAX_ROWS = 5000
        const val MAX_COLS = 100
        const val MAX_MERGE_AREA = 20_000L
        val BUILT_IN_TIME_FORMAT_IDS = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47)
        val CELL_REF = Regex("""([A-Za-z]+)(\d+)""")
    }

    override fun read(input: InputStream, sheetName: String?): List<Sheet> {
        try {
            val entries = readZipEntries(input)
            val workbookBytes = entries["xl/workbook.xml"]
                ?: throw SheetReadException("文件缺少 workbook.xml，可能不是有效的 xlsx")
            val sharedStrings = entries["xl/sharedStrings.xml"]
                ?.let { parseSharedStrings(it) }
                ?: emptyList()
            val timeStyles = entries["xl/styles.xml"]
                ?.let { parseTimeStyles(it) }
                ?: emptySet()
            val relationships = entries["xl/_rels/workbook.xml.rels"]
                ?.let { parseRelationships(it) }
                ?: emptyMap()

            val sheetInfos = parseWorkbook(workbookBytes)
            val targets = sheetInfos.mapNotNull { info ->
                val target = relationships[info.relId] ?: guessTarget(entries, info.name)
                    ?: return@mapNotNull null
                val path = target.removePrefix("/").let { if (it.startsWith("xl/")) it else "xl/$it" }
                if (entries.containsKey(path)) info.name to path else null
            }
            val selected = if (sheetName.isNullOrBlank()) targets else targets.filter { it.first == sheetName }
            if (selected.isEmpty()) {
                throw SheetReadException(
                    if (sheetName.isNullOrBlank()) "未在文件中找到工作表" else "未找到工作表「$sheetName」"
                )
            }
            return selected.take(20).map { (name, path) ->
                parseSheet(name, entries.getValue(path), sharedStrings, timeStyles)
            }
        } catch (e: SheetReadException) {
            throw e
        } catch (e: Exception) {
            throw SheetReadException("解析 xlsx 失败：${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    // ------------------------------------------------------------------
    // zip
    // ------------------------------------------------------------------

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val wanted = name == "xl/workbook.xml" ||
                    name == "xl/_rels/workbook.xml.rels" ||
                    name == "xl/sharedStrings.xml" ||
                    name == "xl/styles.xml" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                if (wanted && !entry.isDirectory) {
                    result[name] = zip.readLimited(MAX_ENTRY_BYTES)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (result.isEmpty()) throw SheetReadException("文件里没有可读取的表格数据，可能已损坏")
        return result
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw SheetReadException("表格内容过大，请精简后再导入")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private data class SheetInfo(val name: String, val relId: String)

    private fun parseWorkbook(bytes: ByteArray): List<SheetInfo> {
        val result = mutableListOf<SheetInfo>()
        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (qName == "sheet" || localName == "sheet") {
                    val name = attributes.getValue("name") ?: return
                    val relId = attributes.getValue("r:id")
                        ?: attributes.getValue("id")
                        ?: return
                    result += SheetInfo(name, relId)
                }
            }
        })
        return result
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (qName == "Relationship" || localName == "Relationship") {
                    val id = attributes.getValue("Id") ?: return
                    val target = attributes.getValue("Target") ?: return
                    result[id] = target
                }
            }
        })
        return result
    }

    private fun guessTarget(entries: Map<String, ByteArray>, sheetName: String): String? {
        val candidates = entries.keys.filter { it.startsWith("xl/worksheets/") }.sorted()
        if (candidates.size == 1) return candidates.first()
        // 没有 rels 时：按 workbook 里 sheet 的次序退回 sheet1.xml、sheet2.xml…
        val index = sheetName.takeLastWhile { it.isDigit() }.toIntOrNull()
        if (index != null) return candidates.getOrNull(index - 1)
        return null
    }

    // ------------------------------------------------------------------
    // sharedStrings / styles
    // ------------------------------------------------------------------

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        parseXml(bytes, object : DefaultHandler() {
            private val current = StringBuilder()
            private var inItem = false
            private var inText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                when {
                    qName == "si" || localName == "si" -> {
                        inItem = true
                        current.setLength(0)
                    }
                    (qName == "t" || localName == "t") && inItem -> inText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) current.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when {
                    qName == "t" || localName == "t" -> inText = false
                    qName == "si" || localName == "si" -> {
                        inItem = false
                        result += current.toString()
                    }
                }
            }
        })
        return result
    }

    /** 返回"时间格式"的样式下标集合（cellXfs 的索引）。 */
    private fun parseTimeStyles(bytes: ByteArray): Set<Int> {
        val customTimeIds = mutableSetOf<Int>()
        val xfNumFmtIds = mutableListOf<Int>()
        parseXml(bytes, object : DefaultHandler() {
            private var inCellXfs = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                when {
                    qName == "numFmt" || localName == "numFmt" -> {
                        val id = attributes.getValue("numFmtId")?.toIntOrNull() ?: return
                        val code = attributes.getValue("formatCode").orEmpty().lowercase()
                        if (code.contains('h') || code.contains('m') || code.contains('s')) {
                            customTimeIds += id
                        }
                    }
                    qName == "cellXfs" || localName == "cellXfs" -> inCellXfs = true
                    (qName == "xf" || localName == "xf") && inCellXfs -> {
                        xfNumFmtIds += attributes.getValue("numFmtId")?.toIntOrNull() ?: 0
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (qName == "cellXfs" || localName == "cellXfs") inCellXfs = false
            }
        })
        return xfNumFmtIds.withIndex()
            .filter { (_, numFmtId) -> numFmtId in BUILT_IN_TIME_FORMAT_IDS || numFmtId in customTimeIds }
            .map { it.index }
            .toSet()
    }

    // ------------------------------------------------------------------
    // worksheet
    // ------------------------------------------------------------------

    private class RawCell(
        val row: Int,
        val col: Int,
        val type: String?,
        val styleIndex: Int?,
        val value: String,
        val inlineText: String
    )

    private class RawSheet {
        val cells = mutableMapOf<Long, RawCell>()
        val merges = mutableListOf<String>()
    }

    private fun parseSheet(
        name: String,
        bytes: ByteArray,
        sharedStrings: List<String>,
        timeStyles: Set<Int>
    ): Sheet {
        val raw = RawSheet()
        parseXml(bytes, object : DefaultHandler() {
            private var currentRow = -1
            private var currentCol = -1
            private var cellType: String? = null
            private var cellStyle: Int? = null
            private val valueText = StringBuilder()
            private val inlineText = StringBuilder()
            private var inValue = false
            private var inInline = false
            private var rowSequence = 0
            private var colSequence = 0

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                when {
                    qName == "row" || localName == "row" -> {
                        val r = attributes.getValue("r")?.toIntOrNull()
                        currentRow = if (r != null && r > 0) r - 1 else rowSequence
                        rowSequence = currentRow + 1
                        colSequence = 0
                    }
                    qName == "c" || localName == "c" -> {
                        val ref = attributes.getValue("r")
                        val parsed = ref?.let { parseRef(it) }
                        currentCol = parsed?.second ?: colSequence
                        colSequence = currentCol + 1
                        cellType = attributes.getValue("t")
                        cellStyle = attributes.getValue("s")?.toIntOrNull()
                        valueText.setLength(0)
                        inlineText.setLength(0)
                    }
                    qName == "v" || localName == "v" -> inValue = true
                    qName == "is" || localName == "is" -> inInline = true
                    qName == "t" || localName == "t" -> if (inInline) inValue = true
                    qName == "mergeCell" || localName == "mergeCell" -> {
                        attributes.getValue("ref")?.let { raw.merges += it }
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) {
                    if (inInline) inlineText.append(ch, start, length) else valueText.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when {
                    qName == "v" || localName == "v" -> inValue = false
                    qName == "t" || localName == "t" -> if (inInline) inValue = false
                    qName == "is" || localName == "is" -> inInline = false
                    qName == "c" || localName == "c" -> {
                        if (currentRow >= 0 && currentCol >= 0 &&
                            currentRow < MAX_ROWS && currentCol < MAX_COLS
                        ) {
                            val key = currentRow.toLong() * MAX_COLS + currentCol
                            raw.cells[key] = RawCell(
                                row = currentRow,
                                col = currentCol,
                                type = cellType,
                                styleIndex = cellStyle,
                                value = valueText.toString(),
                                inlineText = inlineText.toString()
                            )
                        }
                    }
                }
            }
        })

        val cells = mutableMapOf<Long, Cell>()
        raw.cells.forEach { (key, rawCell) ->
            cells[key] = convertCell(rawCell, sharedStrings, timeStyles)
        }
        expandMerges(raw.merges, cells)
        return Sheet(name, toRows(cells))
    }

    private fun convertCell(raw: RawCell, sharedStrings: List<String>, timeStyles: Set<Int>): Cell {
        val rawValue = raw.value.trim()
        return when (raw.type) {
            "s" -> {
                val index = rawValue.toIntOrNull()
                Cell(text = index?.let { sharedStrings.getOrNull(it) }.orEmpty())
            }
            "inlineStr" -> Cell(text = raw.inlineText)
            "str" -> Cell(text = rawValue)
            "b" -> Cell(text = if (rawValue == "1") "TRUE" else "FALSE")
            "d" -> Cell(text = rawValue)
            else -> {
                val numeric = rawValue.toDoubleOrNull()
                Cell(
                    text = rawValue,
                    numeric = numeric,
                    isTimeFormatted = raw.styleIndex != null && raw.styleIndex in timeStyles
                )
            }
        }
    }

    private fun expandMerges(merges: List<String>, cells: MutableMap<Long, Cell>) {
        merges.forEach { ref ->
            val parts = ref.split(':')
            if (parts.size != 2) return@forEach
            val (startRow, startCol) = parseRef(parts[0]) ?: return@forEach
            val (endRow, endCol) = parseRef(parts[1]) ?: return@forEach
            if (startRow < 0 || startCol < 0 || endRow >= MAX_ROWS || endCol >= MAX_COLS) return@forEach
            val area = (endRow - startRow + 1).toLong() * (endCol - startCol + 1)
            if (area <= 0 || area > MAX_MERGE_AREA) return@forEach
            val sourceKey = startRow.toLong() * MAX_COLS + startCol
            val source = cells[sourceKey] ?: return@forEach
            for (row in startRow..endRow) {
                for (col in startCol..endCol) {
                    val key = row.toLong() * MAX_COLS + col
                    if (!cells.containsKey(key)) cells[key] = source
                }
            }
        }
    }

    private fun toRows(cells: Map<Long, Cell>): List<List<Cell>> {
        if (cells.isEmpty()) return emptyList()
        val maxRow = cells.keys.maxOf { it / MAX_COLS }.toInt()
        val maxCol = cells.keys.maxOf { it % MAX_COLS }.toInt()
        return (0..maxRow).map { row ->
            (0..maxCol).map { col -> cells[row.toLong() * MAX_COLS + col] ?: Cell("") }
        }
    }

    private fun parseRef(ref: String): Pair<Int, Int>? {
        val m = CELL_REF.matchEntire(ref.trim()) ?: return null
        val col = columnIndex(m.groupValues[1])
        val row = m.groupValues[2].toIntOrNull()?.minus(1) ?: return null
        if (row < 0 || col < 0) return null
        return row to col
    }

    private fun columnIndex(letters: String): Int {
        var value = 0
        letters.uppercase().forEach { ch ->
            if (ch !in 'A'..'Z') return -1
            value = value * 26 + (ch - 'A' + 1)
        }
        return value - 1
    }

    private fun parseXml(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }
}
