package com.cyclealarm.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 0.6.11 复查 §3.2 的回归：单条闹钟排期失败必须被汇总上报，
 * 不能像以前那样「只写日志、整体返回成功」。
 *
 * 注入方式：id 超出 PendingIntent requestCode 命名空间 → schedulerId() 抛 IllegalArgumentException，
 * 该条计入 failed，其余正常闹钟不受影响。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulerRescheduleRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `rescheduleAll reports the failing alarm and still schedules the rest`() {
        val valid = AlarmEntity(id = 1, dayIndex = 0, hour = 7, minute = 0)
        val invalid = AlarmEntity(
            id = AlarmScheduler.MAX_ALARM_ID + 1,
            dayIndex = 0,
            hour = 8,
            minute = 0
        )

        val result = AlarmScheduler.rescheduleAll(
            context = context,
            alarms = listOf(valid, invalid),
            dateAlarms = emptyList(),
            settings = null,
            holidays = emptyList()
        )

        assertEquals(2, result.attempted)
        assertEquals(listOf("cycle:${invalid.id}"), result.failed)
        assertFalse(result.allSucceeded)
        // 正常那条必须仍然排上
        assertTrue(AlarmScheduler.isScheduled(context, AlarmScheduler.KIND_CYCLE, valid.id))
    }

    @Test
    fun `rescheduleAll reports success when every alarm schedules`() {
        val valid = AlarmEntity(id = 2, dayIndex = 0, hour = 9, minute = 30)
        val result = AlarmScheduler.rescheduleAll(
            context = context,
            alarms = listOf(valid),
            dateAlarms = emptyList(),
            settings = null,
            holidays = emptyList()
        )
        assertEquals(1, result.attempted)
        assertTrue(result.allSucceeded)
    }
}
