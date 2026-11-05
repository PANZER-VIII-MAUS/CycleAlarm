package com.cyclealarm.app.data

import com.cyclealarm.app.R
import com.cyclealarm.app.core.LocalizedFailure
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 备份 schema 版本校验（五轮复审待办）：比当前解析器更新的备份必须拒绝，
 * 否则旧版本会把新格式读成半截数据。
 */
class BackupCodecVersionTest {

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun v2Payload(version: Int): String {
        val body = listOf("M\t123\t$version")
        val bodyText = body.joinToString("\n", postfix = "\n")
        return buildString {
            appendLine("CYCLE_ALARM_BACKUP_V2")
            body.forEach { appendLine(it) }
            appendLine("E\t${body.size}\t${sha256(bodyText)}")
        }
    }

    @Test
    fun `current schema version is supported`() {
        assertTrue(BackupCodec.supportsSchemaVersion(1))
        assertTrue(BackupCodec.supportsSchemaVersion(10))
        assertFalse(BackupCodec.supportsSchemaVersion(11))
        assertFalse(BackupCodec.supportsSchemaVersion(99))
    }

    @Test
    fun `decode rejects backups from a newer app version`() {
        try {
            BackupCodec.decode(v2Payload(99))
            fail("更新版本的备份必须被拒绝")
        } catch (expected: LocalizedFailure) {
            assertTrue(
                "应给出「请升级应用」提示",
                expected.messageRes == R.string.err_backup_newer_version
            )
        }
    }

    @Test
    fun `decode accepts a same-version empty backup`() {
        // 只有 M 行 + 尾记录：内容为空但是合法备份，不应抛异常
        val backup = BackupCodec.decode(v2Payload(10))
        assertTrue(backup.alarms.isEmpty())
    }
}
