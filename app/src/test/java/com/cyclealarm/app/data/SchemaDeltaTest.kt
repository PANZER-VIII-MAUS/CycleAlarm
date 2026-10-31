package com.cyclealarm.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schema 差异守卫：导出 JSON 是迁移正确性的第一道证据。
 *
 * 不用 instrumentation（不需要 device），直接比对导出文件：
 * - 新版本不允许删表；
 * - 既有表的实体块必须逐字不变（等价于「结构没被改动」）。
 *
 * Room 2.6 的导出文件里 identityHash 只有数据库级一个，实体级没有，
 * 所以这里比对实体块文本，而不是逐表 hash。
 */
class SchemaDeltaTest {

    private fun schemaJson(version: Int): String {
        val relative = "schemas/com.cyclealarm.app.data.AppDatabase/$version.json"
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Schema JSON not found: ${candidates.joinToString()}")
    }

    private fun firstString(text: String): String? {
        val start = text.indexOf('"')
        if (start < 0) return null
        val end = text.indexOf('"', start + 1)
        return if (end > start) text.substring(start + 1, end) else null
    }

    private fun tableNames(json: String): List<String> =
        json.split("\"tableName\"").drop(1).mapNotNull { chunk ->
            val colon = chunk.indexOf(':')
            if (colon < 0) null else firstString(chunk.substring(colon + 1))
        }

    private fun tables(json: String): Set<String> = tableNames(json).toSet()

    private fun entityBlock(json: String, table: String): String {
        val marker = "\"tableName\": \"$table\""
        val start = json.indexOf(marker)
        if (start < 0) error("table $table not found")
        // 只比对到 foreignKeys 数组结束：去掉实体之间的分隔符差异
        val foreignKeys = json.indexOf("\"foreignKeys\"", start)
        if (foreignKeys < 0) error("foreignKeys not found for $table")
        val bracket = json.indexOf(']', foreignKeys)
        val stop = if (bracket > foreignKeys) bracket + 1 else foreignKeys
        return json.substring(start, stop)
    }

    @Test
    fun `v9 does not drop any v8 table`() {
        val v8 = tables(schemaJson(8))
        val v9 = tables(schemaJson(9))

        assertTrue("v9 缺少 v8 的表：${v8 - v9}", v9.containsAll(v8))
    }

    @Test
    fun `v9 adds exactly the ring events table`() {
        val added = tables(schemaJson(9)) - tables(schemaJson(8))

        assertEquals(setOf("ring_events"), added)
    }

    @Test
    fun `existing tables keep their exact definition`() {
        val v8Json = schemaJson(8)
        val v9Json = schemaJson(9)

        tables(v8Json).forEach { table ->
            assertEquals(
                "表 $table 的结构被改动了",
                entityBlock(v8Json, table),
                entityBlock(v9Json, table)
            )
        }
    }
}
