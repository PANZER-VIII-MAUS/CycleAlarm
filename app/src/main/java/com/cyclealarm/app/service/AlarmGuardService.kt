package com.cyclealarm.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cyclealarm.app.MainActivity
import com.cyclealarm.app.R
import com.cyclealarm.app.core.NotificationChannels

/**
 * 常驻前台服务：持续显示“闹钟守护”通知，
 * 降低 OPPO/ColorOS 在后台清理应用的几率。
 */
class AlarmGuardService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startAsForeground()
        } catch (e: Exception) {
            // 部分 ROM 会拒绝 specialUse 前台服务：不能崩，直接结束本服务
            stopSelf()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, NotificationChannels.GUARD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("循环闹钟正在运行")
            .setContentText("闹钟守护服务运行中，请在系统设置中允许后台运行")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, notification)
            }
        } catch (e: SecurityException) {
            // Android 14 以下允许无类型降级；14+ 失败就交给上层 stopSelf
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification)
            } else {
                throw e
            }
        }
    }
}
