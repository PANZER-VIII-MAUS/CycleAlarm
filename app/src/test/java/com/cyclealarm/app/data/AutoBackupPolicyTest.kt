package com.cyclealarm.app.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每日自动备份的命名 / 保留策略（真机上「误删应用导致数据全丢」的补救能力）。
 */
class AutoBackupPolicyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        LocalDate.of(y, m, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `file name uses local date`() {
        assertEquals("daily-2026-11-05.txt", AutoBackupPolicy.fileName(at(2026, 11, 5), zone))
    }

    @Test
    fun `date can be parsed back`() {
        assertEquals(LocalDate.of(2026, 11, 5), AutoBackupPolicy.dateOf("daily-2026-11-05.txt"))
        assertNull(AutoBackupPolicy.dateOf("backup-before-restore-1793879000000.txt"))
        assertNull(AutoBackupPolicy.dateOf("garbage.txt"))
    }

    @Test
    fun `should write only once per day`() {
        val now = at(2026, 11, 5)
        assertTrue(AutoBackupPolicy.shouldWriteToday(emptyList(), now, zone))
        assertTrue(AutoBackupPolicy.shouldWriteToday(listOf("daily-2026-11-04.txt"), now, zone))
        assertFalse(AutoBackupPolicy.shouldWriteToday(listOf("daily-2026-11-05.txt"), now, zone))
    }

    @Test
    fun `keeps newest entries and drops older ones`() {
        val names = (1..7).map { day -> String.format("daily-2026-11-%02d.txt", day) }
        val expired = AutoBackupPolicy.expired(names, keep = 5)
        assertEquals(
            setOf("daily-2026-11-01.txt", "daily-2026-11-02.txt"),
            expired.toSet()
        )
    }

    @Test
    fun `non dated files are never expired`() {
        val names = listOf("backup-before-restore-1793879000000.txt") + (1..6).map {
            String.format("daily-2026-11-%02d.txt", it)
        }
        val expired = AutoBackupPolicy.expired(names, keep = 5)
        assertFalse("backup-before-restore-1793879000000.txt" in expired)
        assertEquals(1, expired.size)
    }
    @Test
    fun `size is human readable and never shows zero kilobytes`() {
        assertEquals("612 B", AutoBackupPolicy.formatSize(612))
        assertEquals("0 B", AutoBackupPolicy.formatSize(0))
        assertEquals("1.5 KB", AutoBackupPolicy.formatSize(1536))
        assertEquals("2.0 MB", AutoBackupPolicy.formatSize(2L * 1024 * 1024))
    }
    @Test
    fun `download file name is dated and readable`() {
        assertEquals(
            "CycleAlarm-auto-backup-2026-11-05.txt",
            AutoBackupPolicy.downloadFileName(at(2026, 11, 5), zone)
        )
    }
    @Test
    fun `per-prefix retention keeps daily and restore independent`() {
        val names = listOf(
            "daily-2026-11-01.txt", "daily-2026-11-02.txt", "daily-2026-11-03.txt",
            "daily-2026-11-04.txt", "daily-2026-11-05.txt", "daily-2026-11-06.txt",
            "restore-100.txt", "restore-200.txt", "restore-300.txt", "restore-400.txt"
        )
        // 每日保留 5 → 删最旧 1 个；恢复快照保留 3 → 删最旧 1 个
        assertEquals(
            listOf("daily-2026-11-01.txt"),
            AutoBackupPolicy.expiredByPrefix(names, AutoBackupPolicy.DAILY_PREFIX, 5)
        )
        assertEquals(
            listOf("restore-100.txt"),
            AutoBackupPolicy.expiredByPrefix(names, AutoBackupPolicy.RESTORE_PREFIX, 3)
        )
    }

    @Test
    fun `restore snapshot prefix never touches daily files`() {
        val names = listOf("daily-2026-11-05.txt", "restore-1.txt")
        assertTrue(
            AutoBackupPolicy.expiredByPrefix(names, AutoBackupPolicy.RESTORE_PREFIX, 5).isEmpty()
        )
    }
    @Test
    fun `legacy restore prefix shares retention with new restore prefix`() {
        val names = listOf(
            "restore-300.txt", "restore-200.txt", "restore-100.txt",
            "backup-before-restore-400.txt", "backup-before-restore-50.txt"
        )
        val expired = AutoBackupPolicy.expiredByPrefixes(
            names,
            listOf(AutoBackupPolicy.RESTORE_PREFIX, AutoBackupPolicy.LEGACY_RESTORE_PREFIX),
            keep = 3
        )
        assertEquals(2, expired.size)
    }

    @Test
    fun `isRestoreName recognizes both prefixes only`() {
        assertTrue(AutoBackupPolicy.isRestoreName("restore-1.txt"))
        assertTrue(AutoBackupPolicy.isRestoreName("backup-before-restore-1.txt"))
        assertTrue(!AutoBackupPolicy.isRestoreName("daily-2026-11-05.txt"))
    }
}
