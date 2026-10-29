package com.cyclealarm.app.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cyclealarm.app.MainActivity
import com.cyclealarm.app.R

/** 漏响提醒：走上报渠道 RING_FALLBACK，点开进主界面。 */
object MissedAlarmNotifier {
    private const val TAG = "MissedAlarmNotifier"
    private const val NOTIFICATION_ID = 4

    fun notifyMissed(context: Context, missed: List<MissedAlarm>) {
        if (missed.isEmpty()) return
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) {
                Log.w(TAG, "notifications disabled, skip missed reminder")
                return
            }
            NotificationChannels.createAll(context)
            val names = missed.take(3).joinToString("、") { it.label.ifBlank { "闹钟" } }
            val text = if (missed.size == 1) {
                "错过一次闹钟：$names"
            } else {
                "错过 ${missed.size} 次闹钟：$names"
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.RING_FALLBACK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("错过闹钟")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.e(TAG, "post missed reminder failed", it) }
    }
}
