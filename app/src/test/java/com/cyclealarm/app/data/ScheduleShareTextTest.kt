package com.cyclealarm.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleShareTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val settings = AppSettingsEntity(
        id = 1,
        cycleLength = 4,
        cycleStartEpochDay = 20_000L,
        cycleType = CycleTypes.CUSTOM
    )

    @Test
    fun `header and cycle length are included`() {
        val text = ScheduleShareText.build(context, settings, emptyList(), emptyList())

        assertTrue(text.contains(context.getString(R.string.share_title_line)))
        assertTrue(text.contains(context.getString(R.string.share_cycle_days, 4)))
        assertTrue(text.contains(context.getString(R.string.share_no_cycle_alarms)))
    }

    @Test
    fun `cycle alarms are sorted by day and time`() {
        val alarms = listOf(
            AlarmEntity(dayIndex = 1, hour = 20, minute = 0, label = "夜班"),
            AlarmEntity(dayIndex = 0, hour = 8, minute = 0, label = "白班")
        )

        val text = ScheduleShareText.build(context, settings, alarms, emptyList())

        val whiteLine = context.getString(R.string.share_cycle_alarm_line, 1, "08:00") + " 白班"
        val nightLine = context.getString(R.string.share_cycle_alarm_line, 2, "20:00") + " 夜班"
        val whiteIndex = text.indexOf(whiteLine)
        val nightIndex = text.indexOf(nightLine)
        assertTrue(whiteIndex in 0 until nightIndex)
    }

    @Test
    fun `daily mode is described without cycle length`() {
        val text = ScheduleShareText.build(
            context,
            settings.copy(cycleType = CycleTypes.DAILY),
            emptyList(),
            emptyList()
        )

        assertTrue(
            text.contains(
                context.getString(
                    R.string.share_cycle_label,
                    context.getString(R.string.cycle_daily)
                )
            )
        )
    }

    @Test
    fun `date alarms are listed with dates`() {
        val dateAlarm = DateAlarmEntity(
            epochDay = java.time.LocalDate.of(2026, 11, 2).toEpochDay(),
            hour = 9,
            minute = 0,
            label = "考试"
        )
        val text = ScheduleShareText.build(context, settings, emptyList(), listOf(dateAlarm))

        assertTrue(text.contains("2026-11-02 09:00 考试"))
    }

    @Test
    fun `null settings yields a friendly note`() {
        val text = ScheduleShareText.build(context, null, emptyList(), emptyList())
        assertTrue(text.contains(context.getString(R.string.share_no_settings)))
    }
}
