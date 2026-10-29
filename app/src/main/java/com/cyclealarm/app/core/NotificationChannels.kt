package com.cyclealarm.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val GUARD = "alarm_guard"
    const val RING = "alarm_ring"

    /** 响铃异常 / 漏响提醒：独立渠道，避免和响铃渠道一起被关闭。 */
    const val RING_FALLBACK = "alarm_fallback"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        val guard = NotificationChannel(GUARD, "闹钟守护", NotificationManager.IMPORTANCE_LOW).apply {
            description = "保持闹钟服务后台运行，请不要关闭此通知"
            setShowBadge(false)
        }
        val ring = NotificationChannel(RING, "闹钟响铃", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "闹钟响铃时的提醒"
            setShowBadge(true)
            enableVibration(true)
            // 铃声由播放器自行处理，避免双重声音
            setSound(null, null)
        }
        val fallback = NotificationChannel(
            RING_FALLBACK,
            "响铃异常与漏响提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "响铃服务启动失败或错过闹钟时的提醒，建议保持开启"
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannels(listOf(guard, ring, fallback))
    }
}
