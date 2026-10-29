package com.cyclealarm.app.core

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * 通知可用性检查。
 *
 * 兜底通知最常见的无效原因是「通知权限被拒」或「渠道被用户关掉」——
 * 这两种情况下 notify() 是静默失败的，所以发之前必须先判断。
 */
object NotificationPermissionCheck {

    fun canPost(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(false)

    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val importance = manager.getNotificationChannel(channelId)?.importance
                ?: NotificationManager.IMPORTANCE_NONE
            importance != NotificationManager.IMPORTANCE_NONE
        }.getOrDefault(false)
    }

    fun canSignal(context: Context, channelId: String): Boolean =
        canPost(context) && isChannelEnabled(context, channelId)
}
