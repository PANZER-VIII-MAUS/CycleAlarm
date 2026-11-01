package com.cyclealarm.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「跳过下一次 / 今天临时静音」必须与排期、审计、对账、首页、小组件同源。
 * 这组测试锁住「生效触发点」的行为，防止再出现「点了静音还被判漏响」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EffectiveTriggerPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneId.systemDefault()

    /** 每天一轮，07:30 响。 */
    private val settings = AppSettingsEntity(
        id = 1,
        cycleLength = 1,
        cycleStartEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
        cycleType = CycleTypes.DAILY,
        holidayModeEnabled = false
    )
    private val alarm = AlarmEntity(id = 42, dayIndex = 0, hour = 7, minute = 30, label = "早课")

    private fun millisOf(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun clearStores() {
        // 每个用例前清掉跳过/静音，避免相互影响
        context.getSharedPreferences("temporary_mute_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        clearStores()
    }

    @Test
    fun `plain trigger is today when the alarm has not fired yet`() {
        val today = LocalDate.of(2026, 11, 2)
        val now = millisOf(today, 6, 0)

        val effective = EffectiveTriggerPolicy.nextCycleTrigger(context, alarm, settings, emptyList(), now)

        assertEquals(millisOf(today, 7, 30), effective)
    }

    @Test
    fun `skip next moves the effective trigger to the following day`() {
        val today = LocalDate.of(2026, 11, 2)
        val now = millisOf(today, 6, 0)
        val todayTrigger = millisOf(today, 7, 30)
        TemporaryMuteStore.skipNext(context, AlarmScheduler.KIND_CYCLE, alarm.id, todayTrigger)

        val effective = EffectiveTriggerPolicy.nextCycleTrigger(context, alarm, settings, emptyList(), now)

        assertEquals(millisOf(today.plusDays(1), 7, 30), effective)
    }

    @Test
    fun `temporary mute moves the effective trigger past the muted day`() {
        val today = LocalDate.of(2026, 11, 2)
        val now = millisOf(today, 6, 0)
        TemporaryMuteStore.muteDay(context, today.toEpochDay())

        val effective = EffectiveTriggerPolicy.nextCycleTrigger(context, alarm, settings, emptyList(), now)

        assertEquals(millisOf(today.plusDays(1), 7, 30), effective)
    }

    @Test
    fun `muted alarms are not reported as missed`() {
        val today = LocalDate.of(2026, 11, 2)
        val now = millisOf(today, 8, 0)
        val lastCheck = millisOf(today, 6, 0)
        TemporaryMuteStore.muteDay(context, today.toEpochDay())

        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = listOf(alarm),
            dateAlarms = emptyList(),
            settings = settings,
            holidays = emptyList(),
            firedAts = emptyList(),
            cycleNext = EffectiveTriggerPolicy.cycleTriggerResolver(context, settings, emptyList()),
            mutedDays = TemporaryMuteStore.mutedDays(context)
        )

        assertTrue(missed.isEmpty())
    }

    @Test
    fun `skipped alarms are not reported as missed`() {
        val today = LocalDate.of(2026, 11, 2)
        val now = millisOf(today, 8, 0)
        val lastCheck = millisOf(today, 6, 0)
        TemporaryMuteStore.skipNext(
            context,
            AlarmScheduler.KIND_CYCLE,
            alarm.id,
            millisOf(today, 7, 30)
        )

        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = listOf(alarm),
            dateAlarms = emptyList(),
            settings = settings,
            holidays = emptyList(),
            firedAts = emptyList(),
            cycleNext = EffectiveTriggerPolicy.cycleTriggerResolver(context, settings, emptyList()),
            mutedDays = TemporaryMuteStore.mutedDays(context)
        )

        assertTrue(missed.isEmpty())
    }

    @Test
    fun `unmuted alarm that did not fire is still reported`() {
        val today = LocalDate.of(2026, 11, 2)
        val now = millisOf(today, 8, 0)
        val lastCheck = millisOf(today, 6, 0)

        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = listOf(alarm),
            dateAlarms = emptyList(),
            settings = settings,
            holidays = emptyList(),
            firedAts = emptyList(),
            cycleNext = EffectiveTriggerPolicy.cycleTriggerResolver(context, settings, emptyList()),
            mutedDays = TemporaryMuteStore.mutedDays(context)
        )

        assertEquals(1, missed.size)
        assertEquals(millisOf(today, 7, 30), missed.first().expectedAt)
    }

    @Test
    fun `date alarm has no effective trigger once its date is muted`() {
        val today = LocalDate.of(2026, 11, 2)
        TemporaryMuteStore.muteDay(context, today.toEpochDay())
        val dateAlarm = com.cyclealarm.app.data.DateAlarmEntity(
            id = 7,
            epochDay = today.toEpochDay(),
            hour = 9,
            minute = 0,
            label = "考试"
        )

        val effective = EffectiveTriggerPolicy.nextDateTrigger(
            context,
            dateAlarm,
            settings,
            emptyList(),
            millisOf(today, 6, 0)
        )

        assertNull(effective)
    }
}
