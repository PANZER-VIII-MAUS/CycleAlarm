package com.cyclealarm.app.receiver

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** AlarmReceiver 的胶水行为：幽灵触发被拦、提前提醒发通知。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmReceiverRobolectricTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `ghost fire for a missing alarm does not start the ring service`() {
        val receiver = AlarmReceiver()
        val intent = Intent(app, AlarmReceiver::class.java)
            .setAction(AlarmScheduler.ACTION_FIRE)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, 999_999)
            .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, AlarmScheduler.KIND_CYCLE)
            .putExtra(AlarmScheduler.EXTRA_HOUR, 7)
            .putExtra(AlarmScheduler.EXTRA_MINUTE, 30)

        receiver.onReceive(app, intent)
        shadowOf(app.mainLooper).idle()

        // 数据库里没有这条闹钟 → 不应拉起响铃服务（幽灵触发已被拦下）
        assertTrue(shadowOf(app).nextStartedService == null)
    }

    @Test
    fun `pre alert posts a notification`() {
        NotificationChannels.createAll(app)
        val receiver = AlarmReceiver()
        val intent = Intent(app, AlarmReceiver::class.java)
            .setAction(AlarmScheduler.ACTION_PRE_ALERT)
            .putExtra(AlarmScheduler.EXTRA_HOUR, 7)
            .putExtra(AlarmScheduler.EXTRA_MINUTE, 30)
            .putExtra(AlarmScheduler.EXTRA_LABEL, "早课")
            .putExtra(AlarmScheduler.EXTRA_PRE_ALERT_MINUTES, 10)

        receiver.onReceive(app, intent)

        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
            .allNotifications
        assertEquals(1, notifications.size)
        assertNotNull(notifications.first().extras.getCharSequence("android.title"))
        assertTrue(
            notifications.first().extras.getCharSequence("android.title").toString().contains("闹钟")
        )
    }
}
