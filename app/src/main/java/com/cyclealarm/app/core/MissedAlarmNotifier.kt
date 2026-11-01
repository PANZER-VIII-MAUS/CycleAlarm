package com.cyclealarm.app.core

import android.annotation.SuppressLint
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

    @SuppressLint("MissingPermission") // 上面已用 areNotificationsEnabled 显式判断
    fun notifyMissed(context: Context, missed: List<MissedAlarm>) {
        if (missed.isEmpty()) return
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) {
                Log.w(TAG, "notifications disabled, skip missed reminder")
                return
            }
            NotificationChannels.createAll(context)
            val separator = context.getString(R.string.list_separator)
            val fallback = context.getString(R.string.missed_label_fallback)
            val names = missed.take(3).joinToString(separator) { it.label.ifBlank { fallback } }
            val text = if (missed.size == 1) {
                context.getString(R.string.missed_single, names)
            } else {
                context.getString(R.string.missed_multi, missed.size, names)
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
                .setContentTitle(context.getString(R.string.missed_title))
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
