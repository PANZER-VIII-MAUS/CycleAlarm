package com.cyclealarm.app.receiver

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.NotificationChannels
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppDatabase
import kotlinx.coroutines.runBlocking
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
        // 0.6.11 复查起：提前提醒会先校验父闹钟仍然存在且启用，所以测试要先落一条闹钟。
        // 这里不 reset 数据库单例：其它测试类可能还在用同一个 Room 连接（reset 会让它们
        // 拿到已关闭的连接，出现 "Illegal connection pointer" 的跨测试污染）。
        // 固定 id，避免依赖自增返回值
        val parentId = 7001
        val parent = AlarmEntity(id = parentId, dayIndex = 0, hour = 7, minute = 30, label = "早课")
        runBlocking { AppDatabase.get(app).alarmDao().upsert(parent) }
        NotificationChannels.createAll(app)
        val receiver = AlarmReceiver()
        val intent = Intent(app, AlarmReceiver::class.java)
            .setAction(AlarmScheduler.ACTION_PRE_ALERT)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, parentId)
            .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, AlarmScheduler.KIND_CYCLE)
            .putExtra(AlarmScheduler.EXTRA_HOUR, 7)
            .putExtra(AlarmScheduler.EXTRA_MINUTE, 30)
            .putExtra(AlarmScheduler.EXTRA_LABEL, "早课")
            .putExtra(AlarmScheduler.EXTRA_PRE_ALERT_MINUTES, 10)

        receiver.onReceive(app, intent)

        // 父闹钟校验在 IO 线程里做：等通知真正出现（最多 3 秒），不要靠固定 sleep
        val manager = shadowOf(app.getSystemService(NotificationManager::class.java))
        val deadline = System.currentTimeMillis() + 3_000
        while (manager.allNotifications.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            Thread.sleep(25)
        }
        val notifications = manager.allNotifications
        assertEquals(1, notifications.size)
        val title = notifications.first().extras.getCharSequence("android.title")?.toString()
        // 断言与资源一致，而不是硬编码中文（Robolectric 默认 locale 是英文）
        assertEquals(app.getString(com.cyclealarm.app.R.string.pre_alert_title), title)
        runBlocking { AppDatabase.get(app).alarmDao().deleteById(parentId) }
    }
}
