package com.cyclealarm.app.importer.csv

import com.cyclealarm.app.importer.Cell
import com.cyclealarm.app.importer.Sheet
import com.cyclealarm.app.importer.SheetReadException
import com.cyclealarm.app.importer.SheetReader
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * CSV 读取：中文 Excel 另存的 CSV 默认是 GBK，所以先试严格 UTF-8，失败再回退 GB18030。
 */
class CsvSheetReader : SheetReader {

    override fun read(input: InputStream, sheetName: String?): List<Sheet> {
        try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) throw SheetReadException("文件是空的")
            val text = decode(bytes)
            val delimiter = detectDelimiter(text)
            val rows = parseRows(text, delimiter)
            if (rows.isEmpty()) throw SheetReadException("CSV 里没有可读取的内容")
            return listOf(Sheet(sheetName?.takeIf { it.isNotBlank() } ?: "CSV", rows))
        } catch (e: SheetReadException) {
            throw e
        } catch (e: Exception) {
            throw SheetReadException("解析 CSV 失败：${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    private fun decode(bytes: ByteArray): String {
        val hasBom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val payload = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
        if (hasBom) return String(payload, Charsets.UTF_8)

        // 严格 UTF-8：出现非法字节就回退 GB18030（中文 Excel 的默认导出编码）
        val strictUtf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload))
                .toString()
        }
        strictUtf8.getOrNull()?.let { return it }
        return runCatching { String(payload, Charset.forName("GB18030")) }
            .getOrElse { throw SheetReadException("无法识别 CSV 的文件编码（支持 UTF-8 / GBK）") }
    }

    private fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence()
            .filter { it.isNotBlank() }
            .take(20)
            .joinToString("\n")
        val commas = sample.count { it == ',' }
        val tabs = sample.count { it == '\t' }
        val semicolons = sample.count { it == ';' }
        return when {
            tabs > commas && tabs >= semicolons -> '\t'
            semicolons > commas -> ';'
            else -> ','
        }
    }

    private fun parseRows(text: String, delimiter: Char): List<List<Cell>> {
        val rows = mutableListOf<List<Cell>>()
        var row = mutableListOf<Cell>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                inQuotes && ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index += 2
                    continue
                }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == delimiter -> {
                    row += toCell(field.toString())
                    field.setLength(0)
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row += toCell(field.toString())
                    field.setLength(0)
                    if (row.any { it.text.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += toCell(field.toString())
            if (row.any { it.text.isNotBlank() }) rows += row
        }
        return rows
    }

    private fun toCell(value: String): Cell {
        val text = value.trim()
        return Cell(text = text, numeric = text.toDoubleOrNull())
    }
}
