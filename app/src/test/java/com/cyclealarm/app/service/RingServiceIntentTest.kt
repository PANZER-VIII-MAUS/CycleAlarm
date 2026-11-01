package com.cyclealarm.app.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.core.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 响铃服务 Intent 契约：新字段必须随 Intent 传给服务。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RingServiceIntentTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `ring intent carries per alarm ring options`() {
        val intent = RingService.ringIntent(
            context = app,
            alarmId = 7,
            dayIndex = 2,
            hour = 7,
            minute = 30,
            label = "早课",
            snoozed = false,
            ringtoneUri = "content://alarms/1",
            vibrate = false,
            rampVolume = true
        )

        assertEquals("content://alarms/1", intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI))
        assertEquals(false, intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true))
        assertTrue(intent.getBooleanExtra(AlarmScheduler.EXTRA_RAMP_VOLUME, false))
    }

    @Test
    fun `defaults keep vibration on and ramp off`() {
        val intent = RingService.ringIntent(
            context = app,
            alarmId = 7,
            dayIndex = 0,
            hour = 8,
            minute = 0,
            label = "",
            snoozed = false
        )

        assertEquals(true, intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, false))
        assertEquals(false, intent.getBooleanExtra(AlarmScheduler.EXTRA_RAMP_VOLUME, true))
    }
}
