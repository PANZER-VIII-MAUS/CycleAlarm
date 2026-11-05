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
import com.cyclealarm.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.cyclealarm.app.core.NotificationPermissionCheck
import com.cyclealarm.app.core.RingForegroundGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.cyclealarm.app.core.RingtonePlayer
import com.cyclealarm.app.core.RingUiFallbackPolicy
import com.cyclealarm.app.core.RingUiPresence
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
    private var timedOut: Boolean = false
    private var currentAlarmId: Int = 0
    private var currentKind: String = AlarmScheduler.KIND_CYCLE
    // v10：每闹钟响铃设置
    private var customRingtoneUri: String? = null
    private var vibrateEnabled: Boolean = true
    private var rampEnabled: Boolean = false
    // 本次响铃对应的 ring_events 行与 snooze 行（前台化成功后处理）
    private var ringEventId: Long = 0L
    private var snoozeId: Long = 0L
    private var snoozedStart: Boolean = false
    // 浮窗兜底：系统拦掉 Activity 时保证用户仍有「停止/稍后」可点
    private var overlay: RingOverlayController? = null
    private var overlayCheck: Runnable? = null
    private var overlayWatch: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // 进程被杀后系统重启服务但没有数据：直接结束，避免空响
            stopSelf()
            return START_NOT_STICKY
        }
        lastStartId = startId
        previewMode = intent.getBooleanExtra(AlarmScheduler.EXTRA_PREVIEW, false)
        customRingtoneUri = intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI)
        vibrateEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true)
        rampEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_RAMP_VOLUME, false)
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
            ?: AlarmScheduler.KIND_CYCLE
        currentAlarmId = alarmId
        currentKind = kind
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
        ringEventId = intent.getLongExtra(AlarmScheduler.EXTRA_RING_EVENT_ID, 0L)
        snoozeId = intent.getLongExtra(AlarmScheduler.EXTRA_SNOOZE_ID, 0L)
        snoozedStart = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZED, false)
        _isRinging.value = true
        val foregrounded = startAsForeground(
            kind, alarmId, dayIndex, hour, minute, label, originalHour, originalMinute
        )
        if (foregrounded && !preview) {
            // 真正前台化成功才算「响过」（四轮复审 N3）：起不来时 rungAt 保持 null，
            // 漏响审计与「未被处理」提醒才能覆盖这种情况。
            persistRingOutcome(kind, alarmId)
        }
        launchRingActivity(
            kind, alarmId, dayIndex, hour, minute, label, originalHour, originalMinute, preview
        )
        scheduleOverlayFallback(
            kind, alarmId, dayIndex, hour, minute, label, originalHour, originalMinute, preview
        )
        acquireWakeLock()
        // v10：用闹钟自己的铃声与渐强设置（自定义为空则回退全局默认）
        runCatching { RingtonePlayer.play(this, customRingtoneUri, rampEnabled) }
            .onFailure { Log.e(TAG, "ringtone init failed", it) }
        // 先无条件停掉上一条：否则「上一条振动开着、这一条关了」时旧振动会一直转（四轮复审 N5）
        runCatching { VibratorUtil.stop(this) }
        if (vibrateEnabled) {
            runCatching { VibratorUtil.start(this) }
                .onFailure { Log.e(TAG, "vibration init failed", it) }
        }

        val durationSec = RingPrefs.durationSeconds(this)
        timeout?.let { handler.removeCallbacks(it) }
        // 「一直响」也必须有硬上限：通知被拒时用户没有任何停止入口，否则只能强杀进程。
        val effectiveSec = if (durationSec > 0) durationSec else RingPrefs.ALWAYS_RING_CAP_SECONDS
        val task = Runnable {
            // 响满上限仍没人处理：留一条可点通知，而不是静默停止
            timedOut = true
            if (!previewMode) {
                postUnhandledNotification()
            }
            stopRing()
        }
        timeout = task
        handler.postDelayed(task, effectiveSec * 1000L)
        return START_STICKY
    }

    /**
     * 延迟检查响铃界面是否真的显示出来；没显示且用户授予了悬浮窗权限，就画一层兜底界面。
     *
     * 背景（ColorOS 真机日志）：alarm 触发后 startActivity(RingActivity) 会返回 BAL_BLOCK，
     * 且不抛异常；FSI 也可能被系统拒绝。此时用户听到铃声却没有任何可操作界面。
     */
    private fun scheduleOverlayFallback(
        kind: String,
        alarmId: Int,
        dayIndex: Int,
        hour: Int,
        minute: Int,
        label: String,
        originalHour: Int?,
        originalMinute: Int?
    , preview: Boolean) {
        overlayCheck?.let { handler.removeCallbacks(it) }
        val check = Runnable {
            val permitted = RingOverlayController.canDrawOverlays(this)
            val visible = RingUiPresence.isVisible()
            if (RingUiFallbackPolicy.shouldShowOverlay(visible, permitted, preview)) {
                Log.w(
                    TAG,
                    "ring UI not visible (activityBalBlocked=$visible, overlayPermitted=$permitted) → show overlay fallback"
                )
                val controller = overlay ?: RingOverlayController(this).also { overlay = it }
                val snoozeMinutes = RingPrefs.snoozeMinutes(this)
                controller.show(
                    timeText = RingOverlayController.formatTime(this, hour, minute),
                    detail = label.ifBlank {
                        if (kind == AlarmScheduler.KIND_DATE) {
                            getString(R.string.ring_kind_date)
                        } else {
                            getString(R.string.ring_day_format, dayIndex + 1)
                        }
                    },
                    showSnooze = !preview,
                    snoozeMinutes = snoozeMinutes,
                    onStop = {
                        releaseOverlay()
                        AlarmScheduler.clearSnoozeForAlarm(this, kind, alarmId)
                        stopRing()
                    },
                    onSnooze = {
                        releaseOverlay()
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                AlarmScheduler.snoozeAt(
                                    context = this@RingService,
                                    kind = kind,
                                    alarmId = alarmId,
                                    dayIndex = dayIndex,
                                    hour = hour,
                                    minute = minute,
                                    label = label,
                                    minutes = snoozeMinutes,
                                    originalHour = originalHour ?: hour,
                                    originalMinute = originalMinute ?: minute
                                )
                            }.onFailure { Log.e(TAG, "overlay snooze failed", it) }
                            handler.post { stopRing() }
                        }
                    }
                )
                // Activity 之后又被系统放行显示时，把浮窗撤掉，避免盖住真界面
                val watch = object : Runnable {
                    override fun run() {
                        if (RingUiPresence.isVisible()) {
                            releaseOverlay()
                        } else if (overlay?.isShowing() == true) {
                            handler.postDelayed(this, 1_000L)
                        }
                    }
                }
                overlayWatch = watch
                handler.postDelayed(watch, 1_000L)
            } else if (!permitted && !visible) {
                // 没有浮窗权限 → 至少留下可排查的日志（真机上这是「只有铃声没有界面」的根因）
                Log.w(
                    TAG,
                    "ring UI not visible and overlay permission missing; notification-only fallback (FSI rejected?)"
                )
            }
        }
        overlayCheck = check
        handler.postDelayed(check, RingUiFallbackPolicy.OVERLAY_DELAY_MS)
    }

    private fun releaseOverlay() {
        overlayWatch?.let { handler.removeCallbacks(it) }
        overlayWatch = null
        overlay?.hide()
    }

    private fun stopRing() {
        overlayCheck?.let { handler.removeCallbacks(it) }
        overlayCheck = null
        releaseOverlay()
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        releaseWakeLock()
        _isRinging.value = false
        // 「已处理」在 onDestroy 里写：真正的停止路径（响铃页按钮 / 通知栏停止）都会走到那里
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
        overlayCheck?.let { handler.removeCallbacks(it) }
        overlayCheck = null
        releaseOverlay()
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        _isRinging.value = false
        releaseWakeLock()
        RingtonePlayer.stop()
        VibratorUtil.stop(this)
        // 用户主动停止 = 「已处理」；超时自动停止不算（仍应计入漏响），预览试听也不算
        if (!previewMode && !timedOut && currentAlarmId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    AppDatabase.get(this@RingService).ringEventDao()
                        .markLatestAcked(currentKind, currentAlarmId, System.currentTimeMillis())
                }
            }
        }
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
    ): Boolean {
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
            .setContentTitle(getString(R.string.ring_notification_title, timeText))
            .setContentText(
                label.ifBlank {
                    if (kind == AlarmScheduler.KIND_DATE) {
                        getString(R.string.ring_kind_date)
                    } else {
                        getString(R.string.ring_day_format, dayIndex + 1)
                    }
                }
            )
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, getString(R.string.ring_stop), stopIntent)
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
        return outcome.foregrounded
    }

    /** 前台化成功之后：标记「真的响过」并消费这次 snooze 行。 */
    private fun persistRingOutcome(kind: String, alarmId: Int) {
        if (ringEventId <= 0L && !snoozedStart) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = AppDatabase.get(this@RingService)
                if (ringEventId > 0L) {
                    db.ringEventDao().markRung(ringEventId, System.currentTimeMillis())
                }
                if (snoozedStart) {
                    if (snoozeId > 0L) {
                        db.snoozeDao().deleteById(snoozeId)
                    } else {
                        db.snoozeDao().deleteByAlarm(kind, alarmId.toLong())
                    }
                }
            }.onFailure { Log.e(TAG, "persist ring outcome failed", it) }
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
                .setContentTitle(getString(R.string.svc_fallback_title))
                .setContentText(getString(R.string.svc_fallback_text))
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
                .setContentTitle(getString(R.string.svc_unhandled_title))
                .setContentText(getString(R.string.svc_unhandled_text))
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
            preview: Boolean = false,
            ringtoneUri: String? = null,
            vibrate: Boolean = true,
            rampVolume: Boolean = false,
            ringEventId: Long = 0L,
            snoozeId: Long = 0L
        ): Intent = Intent(context, RingService::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmScheduler.EXTRA_ALARM_KIND, kind)
            .putExtra(AlarmScheduler.EXTRA_DAY_INDEX, dayIndex)
            .putExtra(AlarmScheduler.EXTRA_HOUR, hour)
            .putExtra(AlarmScheduler.EXTRA_MINUTE, minute)
            .putExtra(AlarmScheduler.EXTRA_LABEL, label)
            .putExtra(AlarmScheduler.EXTRA_SNOOZED, snoozed)
            .putExtra(AlarmScheduler.EXTRA_PREVIEW, preview)
            .putExtra(AlarmScheduler.EXTRA_VIBRATE, vibrate)
            .putExtra(AlarmScheduler.EXTRA_RAMP_VOLUME, rampVolume)
            .putExtra(AlarmScheduler.EXTRA_RING_EVENT_ID, ringEventId)
            .putExtra(AlarmScheduler.EXTRA_SNOOZE_ID, snoozeId)
            .apply {
                ringtoneUri?.let { putExtra(AlarmScheduler.EXTRA_RINGTONE_URI, it) }
            }
            .apply {
                if (originalHour != null && originalMinute != null) {
                    putExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR, originalHour)
                    putExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE, originalMinute)
                }
            }
    }
}
