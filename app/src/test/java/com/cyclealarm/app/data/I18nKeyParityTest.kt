package com.cyclealarm.app.data

import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * i18n key 集合一致性：中英资源必须一一对应。
 *
 * 之前只抽查了十几条字符串；缺翻译时 aapt 会回退到默认语言（中文），
 * 用户在英文界面会看到中文，而构建与测试都不会报错（四轮复审 §5.3）。
 */
class I18nKeyParityTest {

    private val values = File("src/main/res/values/strings.xml")
    private val valuesEn = File("src/main/res/values-en/strings.xml")

    private fun pluralKeys(file: File): Set<String> {
        val text = file.readText()
        val matcher = Pattern.compile("<plurals name=\"([^\"]+)\"").matcher(text)
        val result = mutableSetOf<String>()
        while (matcher.find()) result += matcher.group(1)
        return result
    }

    private fun keys(file: File): Set<String> {
        val text = file.readText()
        val matcher = Pattern.compile("<string name=\"([^\"]+)\"").matcher(text)
        val result = mutableSetOf<String>()
        while (matcher.find()) result += matcher.group(1)
        return result
    }

    @Test
    fun `chinese and english string keys match`() {
        assertTrue("缺少中文资源：${values.absolutePath}", values.isFile)
        assertTrue("缺少英文资源：${valuesEn.absolutePath}", valuesEn.isFile)

        val zh = keys(values)
        val en = keys(valuesEn)

        // <plurals> 也必须中英一一对应（六轮复审 §6.2：新资源不能漏）
        val zhPlurals = pluralKeys(values)
        val enPlurals = pluralKeys(valuesEn)
        assertTrue("中文 plurals 数应 > 0", zhPlurals.isNotEmpty())
        assertEquals("中英 plurals 集合必须一致", zhPlurals, enPlurals)

        val onlyZh = (zh - en).sorted()
        val onlyEn = (en - zh).sorted()
        assertTrue("英文资源缺失这些 key：$onlyZh", onlyZh.isEmpty())
        assertTrue("英文资源多出这些 key（中文缺失）：$onlyEn", onlyEn.isEmpty())
        assertEquals("两套资源条目数应一致", zh.size, en.size)
    }
}
