package com.cyclealarm.app.data

import com.cyclealarm.app.core.LocalizedFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val settings = AppSettingsEntity(
        id = 1,
        cycleLength = 8,
        cycleStartEpochDay = 20_500L,
        cycleType = CycleTypes.CUSTOM,
        holidayModeEnabled = true,
        plansSeeded = true
    )
    private val alarm = AlarmEntity(id = 1, dayIndex = 2, hour = 7, minute = 30, label = "早课")

    /** v10 新增的四个字段全部非默认值，用来验证 round-trip 不再丢设置。 */
    private val customizedAlarm = AlarmEntity(
        id = 7,
        dayIndex = 3,
        hour = 6,
        minute = 45,
        enabled = false,
        mutedDuringHoliday = false,
        label = "早班",
        planGenerated = true,
        ringtoneUri = "content://media/internal/audio/media/42",
        vibrate = false,
        rampVolume = true,
        preAlertMinutes = 10
    )
    private val dateAlarm = DateAlarmEntity(
        id = 2,
        epochDay = 20_510L,
        hour = 9,
        minute = 0,
        label = "考试"
    )
    private val type = ActivityTypeEntity(
        id = 3,
        name = "自习",
        hour = 8,
        minute = 15,
        rings = true,
        sortOrder = 1
    )
    private val plan = DayPlanEntity(dayIndex = 2, typeId = 3, hour = 8, minute = 15, enabled = true)
    private val holiday = HolidayEntity(
        id = 5,
        startEpochDay = 20_600L,
        endEpochDay = 20_607L,
        enabled = true,
        label = "国庆 2026"
    )
    private val snooze = SnoozeEntity(
        id = 9,
        kind = "CYCLE",
        alarmId = 1,
        dayIndex = 2,
        hour = 7,
        minute = 30,
        label = "早课",
        triggerAt = 1_800_000_000_000L,
        requestCode = 100_001
    )

    private fun backup(alarms: List<AlarmEntity> = listOf(alarm)) = BackupCodec.Backup(
        settings = settings,
        alarms = alarms,
        dateAlarms = listOf(dateAlarm),
        activityTypes = listOf(type),
        dayPlans = listOf(plan),
        holidays = listOf(holiday),
        snoozes = listOf(snooze),
        exportedAt = 1_700_000_000_000L
    )

    @Test
    fun `backup round trip keeps every table`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup()))

        assertEquals(1_700_000_000_000L, decoded.exportedAt)
        assertEquals(settings, decoded.settings)
        assertEquals(listOf(alarm), decoded.alarms)
        assertEquals(listOf(dateAlarm), decoded.dateAlarms)
        assertEquals(listOf(type), decoded.activityTypes)
        assertEquals(listOf(plan), decoded.dayPlans)
        assertEquals(listOf(holiday), decoded.holidays)
        assertEquals(listOf(snooze), decoded.snoozes)
    }

    @Test
    fun `v10 ring options survive the round trip`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup(listOf(alarm, customizedAlarm))))

        assertEquals(listOf(alarm, customizedAlarm), decoded.alarms)
        val restored = decoded.alarms[1]
        assertEquals("content://media/internal/audio/media/42", restored.ringtoneUri)
        assertEquals(false, restored.vibrate)
        assertEquals(true, restored.rampVolume)
        assertEquals(10, restored.preAlertMinutes)
    }

    @Test
    fun `empty backup keeps an explicit header`() {
        val payload = BackupCodec.encode(
            BackupCodec.Backup(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )

        assertTrue(payload.startsWith("CYCLE_ALARM_BACKUP_V2"))
        val decoded = BackupCodec.decode(payload)
        assertTrue(decoded.alarms.isEmpty())
        assertEquals(null, decoded.settings)
    }

    @Test
    fun `foreign file is rejected`() {
        val failed = runCatching { BackupCodec.decode("hello world") }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `truncated backup is rejected instead of restoring partial data`() {
        val payload = BackupCodec.encode(backup(listOf(alarm, customizedAlarm)))
        val lines = payload.lines()
        // 砍掉最后一行（尾记录）以及最后一条闹钟记录：模拟导出中途被杀
        val truncated = lines.dropLast(2).joinToString("\n") + "\n"

        val failed = runCatching { BackupCodec.decode(truncated) }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull() is LocalizedFailure)
        assertEquals(
            com.cyclealarm.app.R.string.err_backup_truncated,
            (failed.exceptionOrNull() as LocalizedFailure).messageRes
        )
    }

    @Test
    fun `tampered record is rejected by checksum`() {
        val payload = BackupCodec.encode(backup())
        // 改掉第一条闹钟记录里的「小时」字段，正文变化必须被校验和发现
        val tampered = payload.replace("A	1	2	7	30	", "A	1	2	7	31	")
        assertTrue(tampered != payload)

        val failed = runCatching { BackupCodec.decode(tampered) }
        assertTrue(failed.isFailure)
        assertEquals(
            com.cyclealarm.app.R.string.err_backup_checksum,
            (failed.exceptionOrNull() as LocalizedFailure).messageRes
        )
    }

    @Test
    fun `legacy v1 backup still decodes with default ring options`() {
        // 旧版本导出的格式：V1 头 + 9 段 A 记录 + 无尾记录
        val legacy = buildString {
            appendLine("CYCLE_ALARM_BACKUP_V1")
            appendLine("M\t1700000000000")
            appendLine("A\t1\t2\t7\t30\t1\t1\t0\t6Ieq5Lmg") // 自习
        }

        val decoded = BackupCodec.decode(legacy)
        assertEquals(1, decoded.alarms.size)
        val restored = decoded.alarms.first()
        assertEquals(7, restored.hour)
        assertEquals(1, restored.mutedDuringHoliday.flagForTest())
        assertEquals(null, restored.ringtoneUri)
        assertEquals(true, restored.vibrate)
        assertEquals(false, restored.rampVolume)
        assertEquals(0, restored.preAlertMinutes)
    }

    private fun Boolean.flagForTest(): Int = if (this) 1 else 0
}
