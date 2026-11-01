package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 快照 V3：A 记录补上 v10 的四个响铃字段；V1/V2 老快照仍可读。 */
class ImportSnapshotCodecV3Test {

    private val customAlarm = AlarmEntity(
        id = 9,
        dayIndex = 2,
        hour = 6,
        minute = 45,
        enabled = false,
        mutedDuringHoliday = false,
        label = "早班",
        planGenerated = true,
        ringtoneUri = "content://media/internal/audio/media/99",
        vibrate = false,
        rampVolume = true,
        preAlertMinutes = 15
    )

    private fun snapshot() = AlarmImportWriter.Snapshot(
        alarms = listOf(customAlarm),
        dateAlarms = emptyList(),
        settings = null,
        dayPlans = emptyList(),
        activityTypes = emptyList(),
        batchId = 1L
    )

    @Test
    fun `v10 ring options survive snapshot round trip`() {
        val decoded = ImportSnapshotCodec.decode(ImportSnapshotCodec.encode(snapshot()))

        assertEquals(1, decoded.alarms.size)
        val restored = decoded.alarms.first()
        assertEquals(customAlarm, restored)
        assertEquals("content://media/internal/audio/media/99", restored.ringtoneUri)
        assertEquals(false, restored.vibrate)
        assertEquals(true, restored.rampVolume)
        assertEquals(15, restored.preAlertMinutes)
    }

    @Test
    fun `v2 snapshot still decodes with default ring options`() {
        // V2 的 A 记录只有 9 段：宁可回退默认值，也不能因为字段变多就撤销失败
        val v2 = buildString {
            appendLine("CYCLE_ALARM_SNAPSHOT_V2")
            appendLine("B\t1700000000000")
            appendLine("A\t9\t2\t6\t45\t1\t1\t0\t5pep54mh") // 早班
        }

        val decoded = ImportSnapshotCodec.decode(v2)
        val restored = decoded.alarms.first()
        assertEquals(6, restored.hour)
        assertEquals(45, restored.minute)
        assertEquals(null, restored.ringtoneUri)
        assertEquals(true, restored.vibrate)
        assertEquals(false, restored.rampVolume)
        assertEquals(0, restored.preAlertMinutes)
    }
}
