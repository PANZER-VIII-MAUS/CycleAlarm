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
import com.cyclealarm.app.MainActivity
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.NotificationChannels
import com.cyclealarm.app.core.NotificationPermissionCheck
import com.cyclealarm.app.core.RingForegroundGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private var lastStartId: Int = 0
    private var previewMode: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // 进程被杀后系统重启服务但没有数据：直接结束，避免空响
            stopSelf()
            return START_NOT_STICKY
        }
        lastStartId = startId
        previewMode = intent.getBooleanExtra(AlarmScheduler.EXTRA_PREVIEW, false)
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
            ?: AlarmScheduler.KIND_CYCLE
        val dayIndex = intent.getIntExtra(AlarmScheduler.EXTRA_DAY_INDEX, 0)
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, 8)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, 0)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL).orEmpty()
        val originalHour = if (intent.hasExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR)) {
            intent.getIntExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR, hour)
        } else {
            null
        }
        val originalMinute = if (intent.hasExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE)) {
            intent.getIntExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE, minute)
        } else {
            null
        }

        val preview = intent.getBooleanExtra(AlarmScheduler.EXTRA_PREVIEW, false)
        _isRinging.value = true
        startAsForeground(kind, alarmId, dayIndex, hour, minute, label, originalHour, originalMinute)
        launchRingActivity(
            kind, alarmId, dayIndex, hour, minute, label, originalHour, originalMinute, preview
        )
        acquireWakeLock()
        runCatching { RingtonePlayer.play(this) }
            .onFailure { Log.e(TAG, "ringtone init failed", it) }
        runCatching { VibratorUtil.start(this) }
            .onFailure { Log.e(TAG, "vibration init failed", it) }

        val durationSec = RingPrefs.durationSeconds(this)
        timeout?.let { handler.removeCallbacks(it) }
        // 「一直响」也必须有硬上限：通知被拒时用户没有任何停止入口，否则只能强杀进程。
        val effectiveSec = if (durationSec > 0) durationSec else RingPrefs.ALWAYS_RING_CAP_SECONDS
        val task = Runnable {
            // 响满上限仍没人处理：留一条可点通知，而不是静默停止
            if (!previewMode) {
                postUnhandledNotification()
            }
            stopRing()
        }
        timeout = task
        handler.postDelayed(task, effectiveSec * 1000L)
        return START_STICKY
    }

    private fun stopRing() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        releaseWakeLock()
        _isRinging.value = false
        // 带 startId 停止：只结束本次启动，避免把新一次响铃也一并结束
        stopSelf(lastStartId)
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
        label: String,
        originalHour: Int?,
        originalMinute: Int?,
        preview: Boolean
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
                    .putExtra(AlarmScheduler.EXTRA_PREVIEW, preview)
                    .apply {
                        if (originalHour != null && originalMinute != null) {
                            putExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR, originalHour)
                            putExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE, originalMinute)
                        }
                    }
            )
        }
    }

    override fun onDestroy() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        _isRinging.value = false
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
        label: String,
        originalHour: Int?,
        originalMinute: Int?
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
                .putExtra(AlarmScheduler.EXTRA_LABEL, label)
                .apply {
                    if (originalHour != null && originalMinute != null) {
                        putExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR, originalHour)
                        putExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE, originalMinute)
                    }
                },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            this,
            schedulerId,
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_STOP_RING)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, kind),
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

        val outcome = RingForegroundGuard.attempt {
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
        if (!outcome.foregrounded) {
            // 前台化失败必须降级而不是崩溃：记录失败供下次打开 App 提示，改发普通通知，
            // 铃声与振动继续（同函数后续逻辑不受影响）。
            Log.e(TAG, "startForeground failed", outcome.error)
            outcome.error?.let { RingForegroundGuard.recordFailure(this, it) }
            postFallbackNotification(fullScreenIntent)
        }
    }

    /**
     * 前台化失败时的降级入口。
     *
     * 注意：catch 住异常不等于服务能活——系统可能随后以 RemoteServiceException 结束进程。
     * 降级目标只是「尽快给用户一条可点的通知」，所以这里用独立渠道并先查权限，
     * 否则通知会被用户关掉的响铃渠道连带挡掉（三轮复审 §4.3）。
     */
    private fun postFallbackNotification(contentIntent: PendingIntent) {
        NotificationChannels.createAll(this)
        if (!NotificationPermissionCheck.canSignal(this, NotificationChannels.RING_FALLBACK)) {
            Log.w(TAG, "fallback notification blocked: permission or channel unavailable")
            return
        }
        runCatching {
            val fallback = NotificationCompat.Builder(this, NotificationChannels.RING_FALLBACK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("闹钟正在响")
                .setContentText("前台服务不可用，点开可停止")
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(FALLBACK_NOTIFICATION_ID, fallback)
        }.onFailure { Log.e(TAG, "fallback notification failed", it) }
    }

    /** 响满上限仍未被处理时的提醒（独立渠道 + 权限检查）。 */
    private fun postUnhandledNotification() {
        NotificationChannels.createAll(this)
        if (!NotificationPermissionCheck.canSignal(this, NotificationChannels.RING_FALLBACK)) {
            Log.w(TAG, "unhandled notification blocked: permission or channel unavailable")
            return
        }
        runCatching {
            val contentIntent = PendingIntent.getActivity(
                this,
                UNHANDLED_NOTIFICATION_ID,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat
                .Builder(this, NotificationChannels.RING_FALLBACK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("闹钟未被处理")
                .setContentText("刚才的闹钟已响到上限自动停止，点开可确认状态")
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(UNHANDLED_NOTIFICATION_ID, notification)
        }.onFailure { Log.e(TAG, "unhandled notification failed", it) }
    }

    companion object {
        private const val TAG = "RingService"

        /** 前台化失败时降级通知的 id（与响铃前台通知 2 区分）。 */
        private const val FALLBACK_NOTIFICATION_ID = 3

        /** 响满上限未被处理的提醒 id。 */
        private const val UNHANDLED_NOTIFICATION_ID = 3_000_002

        private val _isRinging = MutableStateFlow(false)

        /** 响铃进行中状态：RingActivity 用它在服务停止后跟随退出（同一进程）。 */
        val isRinging: StateFlow<Boolean> = _isRinging

        /** 构建拉起响铃服务的 Intent（供 AlarmReceiver / 试听按钮使用）。 */
        fun ringIntent(
            context: Context,
            alarmId: Int,
            dayIndex: Int,
            hour: Int,
            minute: Int,
            label: String,
            snoozed: Boolean,
            kind: String = AlarmScheduler.KIND_CYCLE,
            originalHour: Int? = null,
            originalMinute: Int? = null,
            preview: Boolean = false
        ): Intent = Intent(context, RingService::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, kind)
            .putExtra(AlarmScheduler.EXTRA_DAY_INDEX, dayIndex)
            .putExtra(AlarmScheduler.EXTRA_HOUR, hour)
            .putExtra(AlarmScheduler.EXTRA_MINUTE, minute)
            .putExtra(AlarmScheduler.EXTRA_LABEL, label)
            .putExtra(AlarmScheduler.EXTRA_SNOOZED, snoozed)
            .putExtra(AlarmScheduler.EXTRA_PREVIEW, preview)
            .apply {
                if (originalHour != null && originalMinute != null) {
                    putExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR, originalHour)
                    putExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE, originalMinute)
                }
            }
    }
}
