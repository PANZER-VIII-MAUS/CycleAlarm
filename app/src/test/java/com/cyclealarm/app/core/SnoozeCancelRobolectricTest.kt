package com.cyclealarm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.data.SnoozeEntity
import com.cyclealarm.app.receiver.AlarmReceiver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * 0.6.10 审查 Critical 的回归：恢复备份时必须能取消旧 snooze 的系统排期。
 *
 * 这里只验证「取消逻辑本身」——用与调度器相同的 requestCode/action 武装一个 PendingIntent，
 * 再调用 [AlarmScheduler.cancelStoredSnooze]，确认系统里已经查不到它。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnoozeCancelRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun armedPendingIntent(kind: String, alarmId: Int): PendingIntent? {
        val requestCode = AlarmScheduler.snoozeRequestCode(kind, alarmId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_FIRE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Test
    fun `cancelStoredSnooze removes the armed pending intent`() {
        val kind = AlarmScheduler.KIND_CYCLE
        val alarmId = 42
        val requestCode = AlarmScheduler.snoozeRequestCode(kind, alarmId)
        val created = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        assertNotNull(created)
        assertNotNull("武装后应能查到 PendingIntent", armedPendingIntent(kind, alarmId))

        val snooze = SnoozeEntity(
            id = 1,
            kind = kind,
            alarmId = alarmId.toLong(),
            dayIndex = 0,
            hour = 7,
            minute = 0,
            label = "",
            triggerAt = System.currentTimeMillis() + 60_000,
            requestCode = requestCode
        )
        AlarmScheduler.cancelStoredSnooze(context, snooze)
        assertNull("取消后系统里不应再存在该 PendingIntent", armedPendingIntent(kind, alarmId))
    }

    @Test
    fun `cancelStoredSnooze ignores invalid alarm id`() {
        val invalid = SnoozeEntity(
            id = 1,
            kind = AlarmScheduler.KIND_CYCLE,
            alarmId = -1,
            dayIndex = 0,
            hour = 7,
            minute = 0,
            label = "",
            triggerAt = System.currentTimeMillis() + 60_000,
            requestCode = 0
        )
        // 不应抛异常
        AlarmScheduler.cancelStoredSnooze(context, invalid)
    }
}
