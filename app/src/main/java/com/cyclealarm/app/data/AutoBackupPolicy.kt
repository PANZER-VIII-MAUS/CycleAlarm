package com.cyclealarm.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 每日自动备份的命名 / 保留策略（纯函数）。
 *
 * 为什么需要：用户数据只存在应用私有目录，卸载 / 清数据就没了（真机上已经发生过一次）。
 * 每天首次打开应用时自动导出一份，保留最近若干份；设置页提供「恢复自动备份」入口。
 */
object AutoBackupPolicy {

    /** 每天一份，文件名按日期，便于判断「今天是否已备份」。 */
    fun fileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return "daily-$date.txt"
    }

    fun dateOf(name: String): LocalDate? {
        if (!name.startsWith("daily-") || !name.endsWith(".txt")) return null
        return runCatching { LocalDate.parse(name.removePrefix("daily-").removeSuffix(".txt")) }.getOrNull()
    }

    /** 今天是否还没有自动备份。 */
    fun shouldWriteToday(existingNames: List<String>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        fileName(now, zone) !in existingNames

    /** 备份体积的人类可读文本（小于 1KB 时显示字节，避免出现「0 KB」）。 */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1L shl 10 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** 导出到系统「下载」目录的文件名（用户可在文件管理器里直接取回）。 */
    fun downloadFileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return "CycleAlarm-auto-backup-$date.txt"
    }

    /** 每日备份文件前缀。 */
    const val DAILY_PREFIX = "daily-"

    /** 恢复前快照前缀（0.6.9 起单独目录 + 单独保留策略）。 */
    const val RESTORE_PREFIX = "restore-"

    /** 旧版恢复前快照前缀（历史文件迁移用）。 */
    const val LEGACY_RESTORE_PREFIX = "backup-before-restore-"

    /** 恢复前快照文件名。 */
    fun restoreFileName(now: Long): String = "$RESTORE_PREFIX$now.txt"

    /**
     * 按前缀分别淘汰：只在一批同前缀文件里保留最近 [keep] 份。
     * （修复六轮复审 §4.1：以前恢复前快照会把同目录的每日备份一起计入并删掉）
     */
    fun expiredByPrefix(names: List<String>, prefix: String, keep: Int): List<String> {
        val matched = names.filter { it.startsWith(prefix) }.sortedDescending()
        return matched.drop(keep.coerceAtLeast(1))
    }

    /** 保留最近 [keep] 份（按文件名里的日期倒序），返回应删除的文件名。 */
    fun expired(existingNames: List<String>, keep: Int = 5): List<String> {
        val dated = existingNames.mapNotNull { name -> dateOf(name)?.let { name to it } }
            .sortedByDescending { it.second }
        return dated.drop(keep.coerceAtLeast(1)).map { it.first }
    }
}
