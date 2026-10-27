package com.cyclealarm.app.importer

import java.time.LocalTime

/**
 * 时间归一化：把各种写法的上课时间解析成 LocalTime。
 * 任何解析不了的输入都返回 null，绝不抛异常。
 */
object TimeNormalizer {

    private val rangeSplitter = Regex("""\s*(?:[-–—~～]|至|到)\s*""")
    private val periodPattern =
        Regex("""(上午|早上|早晨|凌晨|中午|下午|傍晚|晚上|夜里|夜间)\s*(\d{1,2})(?:\s*[:：点时]\s*(\d{1,2}))?""")
    private val colonPattern = Regex("""(\d{1,2})\s*[:：]\s*(\d{1,2})""")
    private val dianPattern = Regex("""(\d{1,2})\s*[点时時]\s*(\d{1,2})?\s*分?""")

    fun parse(text: String): LocalTime? {
        val raw = text.trim()
        if (raw.isEmpty()) return null

        // 节次优先："第1-2节" / "1-2节"
        SectionTimeTable.parseSection(raw)?.let { section ->
            return SectionTimeTable.default()[section]
        }

        // 时间段只取开始："8:20-9:55" / "8:20~9:55"
        val head = rangeSplitter.split(raw).firstOrNull { it.isNotBlank() }?.trim() ?: raw

        // 中文时段："下午2:00" / "晚上8:00" / "上午8:20"
        periodPattern.find(head)?.let { m ->
            val period = m.groupValues[1]
            val hour = m.groupValues[2].toIntOrNull() ?: return null
            val minute = m.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            return toTime(adjustHour(period, hour), minute)
        }

        colonPattern.find(head)?.let { m ->
            return toTime(m.groupValues[1].toIntOrNull() ?: return null, m.groupValues[2].toIntOrNull() ?: return null)
        }

        dianPattern.find(head)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return null
            val minute = m.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            return toTime(hour, minute)
        }

        // 纯 Excel 时间序列（0 ≤ v < 1）
        head.toDoubleOrNull()?.let { return fromExcelSerial(it) }
        return null
    }

    /** Excel 时间序列 → LocalTime，四舍五入到分钟（不截断）。 */
    fun fromExcelSerial(value: Double): LocalTime? {
        if (value <= 0.0 || value >= 1.0) return null
        val totalMinutes = Math.round(value * 1440.0).toInt()
        val normalized = Math.floorMod(totalMinutes, 1440)
        return LocalTime.of(normalized / 60, normalized % 60)
    }

    private fun adjustHour(period: String, hour: Int): Int = when (period) {
        "下午", "傍晚", "晚上", "夜里", "夜间" -> if (hour < 12) hour + 12 else hour
        "中午" -> if (hour < 11) hour + 12 else hour
        else -> hour // 上午 / 早上 / 早晨 / 凌晨
    }

    private fun toTime(hour: Int, minute: Int): LocalTime? {
        val h = if (hour == 24 && minute == 0) 0 else hour
        return runCatching { LocalTime.of(h, minute) }.getOrNull()
    }
}
