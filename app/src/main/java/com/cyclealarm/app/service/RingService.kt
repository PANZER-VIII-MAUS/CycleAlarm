package com.cyclealarm.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.NotificationChannels
import com.cyclealarm.app.core.RingtonePlayer
import com.cyclealarm.app.core.RingPrefs
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.VibratorUtil
import com.cyclealarm.app.receiver.AlarmReceiver
import com.cyclealarm.app.ui.ring.RingActivity

/**
 * 响铃前台服务：由 AlarmManager 精确闹钟广播直接拉起
 * （Android 12+ 对精确闹钟触发的前台服务有豁免，不依赖 WorkManager 后台限制）。
 * 前台运行并循环播放铃声 + 振动；2 分钟无操作自动停止，
 * 通知栏“停止”或响铃页按钮可提前停止。
 */
class RingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // 进程被杀后系统重启服务但没有数据：直接结束，避免空响
            stopSelf()
            return START_NOT_STICKY
        }
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
            ?: AlarmScheduler.KIND_CYCLE
        val dayIndex = intent.getIntExtra(AlarmScheduler.EXTRA_DAY_INDEX, 0)
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, 8)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, 0)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL).orEmpty()

        startAsForeground(kind, alarmId, dayIndex, hour, minute, label)
        launchRingActivity(kind, alarmId, dayIndex, hour, minute, label)
        acquireWakeLock()
        runCatching { RingtonePlayer.play(this) }
            .onFailure { Log.e(TAG, "ringtone init failed", it) }
        runCatching { VibratorUtil.start(this) }
            .onFailure { Log.e(TAG, "vibration init failed", it) }

        val durationSec = RingPrefs.durationSeconds(this)
        timeout?.let { handler.removeCallbacks(it) }
        if (durationSec > 0) {
            val task = Runnable { stopRing() }
            timeout = task
            handler.postDelayed(task, durationSec * 1000L)
        } else {
            // 一直响：不自动停止，等用户点「停止」
            timeout = null
        }
        return START_STICKY
    }

    private fun stopRing() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        releaseWakeLock()
        stopSelf()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        releaseWakeLock()
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CycleAlarm:Ring"
        ).apply {
            setReferenceCounted(false)
        }
        runCatching { lock.acquire() }
            .onSuccess { wakeLock = lock }
            .onFailure { Log.e(TAG, "wake lock acquire failed", it) }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        runCatching { if (lock.isHeld) lock.release() }
            .onFailure { Log.e(TAG, "wake lock release failed", it) }
    }

    /** 直接拉起响铃界面，保证任何时候都有“停止/稍后”按钮可操作。 */
    private fun launchRingActivity(
        kind: String,
        alarmId: Int,
        dayIndex: Int,
        hour: Int,
        minute: Int,
        label: String
    ) {
        runCatching {
            startActivity(
                Intent(this, RingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, kind)
                    .putExtra(AlarmScheduler.EXTRA_DAY_INDEX, dayIndex)
                    .putExtra(AlarmScheduler.EXTRA_HOUR, hour)
                    .putExtra(AlarmScheduler.EXTRA_MINUTE, minute)
                    .putExtra(AlarmScheduler.EXTRA_LABEL, label)
            )
        }
    }

    override fun onDestroy() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        releaseWakeLock()
        RingtonePlayer.stop()
        VibratorUtil.stop(this)
        super.onDestroy()
    }

    private fun startAsForeground(
        kind: String,
        alarmId: Int,
        dayIndex: Int,
        hour: Int,
        minute: Int,
        label: String
    ) {
        val timeText = DisplayPrefs.formatTime(this, hour, minute)
        // 周期与按日期闹钟的数据库 id 可能相同，PendingIntent requestCode 必须用调度 id 区分
        val schedulerId = AlarmScheduler.schedulerId(kind, alarmId)
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            schedulerId + AlarmScheduler.FULLSCREEN_REQUEST_OFFSET,
            Intent(this, RingActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, kind)
                .putExtra(AlarmScheduler.EXTRA_DAY_INDEX, dayIndex)
                .putExtra(AlarmScheduler.EXTRA_HOUR, hour)
                .putExtra(AlarmScheduler.EXTRA_MINUTE, minute)
                .putExtra(AlarmScheduler.EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            this,
            schedulerId,
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_STOP_RING)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, NotificationChannels.RING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("闹钟 $timeText")
            .setContentText(
                label.ifBlank {
                    if (kind == AlarmScheduler.KIND_DATE) "按日期闹钟" else "第 ${dayIndex + 1} 天"
                }
            )
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, "停止", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                2,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(2, notification)
        }
    }

    companion object {
        private const val TAG = "RingService"

        /** 构建拉起响铃服务的 Intent（供 AlarmReceiver / 试听按钮使用）。 */
        fun ringIntent(
            context: Context,
            alarmId: Int,
            dayIndex: Int,
            hour: Int,
            minute: Int,
            label: String,
            snoozed: Boolean,
            kind: String = AlarmScheduler.KIND_CYCLE
        ): Intent = Intent(context, RingService::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, kind)
            .putExtra(AlarmScheduler.EXTRA_DAY_INDEX, dayIndex)
            .putExtra(AlarmScheduler.EXTRA_HOUR, hour)
            .putExtra(AlarmScheduler.EXTRA_MINUTE, minute)
            .putExtra(AlarmScheduler.EXTRA_LABEL, label)
            .putExtra(AlarmScheduler.EXTRA_SNOOZED, snoozed)
    }
}
