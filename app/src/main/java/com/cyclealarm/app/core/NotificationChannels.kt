package com.cyclealarm.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.cyclealarm.app.R

object NotificationChannels {
    const val GUARD = "alarm_guard"
    const val RING = "alarm_ring"

    /** 响铃异常 / 漏响提醒：独立渠道，避免和响铃渠道一起被关闭。 */
    const val RING_FALLBACK = "alarm_fallback"

    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val guard = NotificationChannel(
            GUARD,
            AppLanguage.appString(context, R.string.channel_guard_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = AppLanguage.appString(context, R.string.channel_guard_desc)
            setShowBadge(false)
        }
        val ring = NotificationChannel(
            RING,
            AppLanguage.appString(context, R.string.channel_ring_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = AppLanguage.appString(context, R.string.channel_ring_desc)
            setShowBadge(true)
            enableVibration(true)
            // 铃声由播放器自行处理，避免双重声音
            setSound(null, null)
        }
        val fallback = NotificationChannel(
            RING_FALLBACK,
            AppLanguage.appString(context, R.string.channel_fallback_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = AppLanguage.appString(context, R.string.channel_fallback_desc)
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannels(listOf(guard, ring, fallback))
    }
}
