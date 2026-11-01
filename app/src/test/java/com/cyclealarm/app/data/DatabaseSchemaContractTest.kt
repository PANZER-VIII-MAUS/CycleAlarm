package com.cyclealarm.app.data

import com.cyclealarm.app.core.DatabaseSelfCheck
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结构契约守卫：`DatabaseSelfCheck.EXPECTED` 必须与导出的 Room schema 一致。
 *
 * 之前自检里的期望值是手抄的，改表后容易忘记同步；这里直接从 `app/schemas/…/11.json`
 * 读真实 schema 做比对（四轮复审 §5.3 盲点）。
 */
class DatabaseSchemaContractTest {

    private val schemaFile = File(
        "schemas/com.cyclealarm.app.data.AppDatabase/11.json"
    )

    @Test
    fun `self check expectations match the exported room schema`() {
        assertTrue("找不到导出的 schema：${schemaFile.absolutePath}", schemaFile.isFile)
        val json = schemaFile.readText()

        EXPECTED.forEach { spec ->
            // 从 JSON 里截取该表的定义段，收集 createSql 之外的字段名
            val tableIdx = json.indexOf("\"tableName\": \"${spec.name}\"")
            assertTrue("schema 里应有表 ${spec.name}", tableIdx >= 0)
            val segment = json.substring(tableIdx, minOf(json.length, tableIdx + 4000))
            spec.columns.forEach { column ->
                assertTrue(
                    "自检期望里有 ${spec.name}.$column，但导出的 schema 里没有",
                    segment.contains("\"columnName\": \"$column\"")
                )
            }
        }
    }

    @Test
    fun `exports cover identical table set`() {
        val json = schemaFile.takeIf { it.isFile }?.readText() ?: return
        val tables = Regex("\"tableName\": \"([^\"]+)\"")
            .findAll(json)
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("androidx.room") }
            .toSet()
        assertEquals(
            "自检的期望表集合必须与导出 schema 的表集合一致",
            DatabaseSelfCheck.EXPECTED.map { it.name }.toSet(),
            tables
        )
    }
}

private val EXPECTED: List<DatabaseSelfCheck.TableSpec> get() = DatabaseSelfCheck.EXPECTED
