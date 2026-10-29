package com.cyclealarm.app.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cyclealarm.app.MainActivity
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.AlarmFireCoordinator
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.NotificationChannels
import com.cyclealarm.app.core.NotificationPermissionCheck
import com.cyclealarm.app.data.AppDatabase
import com.cyclealarm.app.service.RingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟广播接收器。
 * - ACTION_FIRE：直接拉起 RingService 前台响铃（精确闹钟触发的前台服务有系统豁免），
 *   并在响铃后立即排入下一次循环。
 * - ACTION_STOP_RING：停止响铃服务（onDestroy 中释放铃声与振动）。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_STOP_RING -> {
                val kind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
                    ?: AlarmScheduler.KIND_CYCLE
                val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
                AlarmScheduler.clearSnoozeForAlarm(context, kind, alarmId)
                context.stopService(Intent(context, RingService::class.java))
            }

            AlarmScheduler.ACTION_RESYNC -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmResyncHelper.resyncAll(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "manual resync failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AlarmScheduler.ACTION_FIRE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
                        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
                            ?: AlarmScheduler.KIND_CYCLE
                        val dayIndex = intent.getIntExtra(AlarmScheduler.EXTRA_DAY_INDEX, 0)
                        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, 8)
                        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, 0)
                        val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL).orEmpty()
                        val snoozed = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZED, false)
                        val snoozeId = intent.getLongExtra(AlarmScheduler.EXTRA_SNOOZE_ID, 0L)
                        val originalHour = if (
                            intent.hasExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR)
                        ) {
                            intent.getIntExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR, hour)
                        } else {
                            null
                        }
                        val originalMinute = if (
                            intent.hasExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE)
                        ) {
                            intent.getIntExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE, minute)
                        } else {
                            null
                        }

                        if (snoozed) {
                            runCatching {
                                val db = AppDatabase.get(context)
                                if (snoozeId > 0) {
                                    db.snoozeDao().deleteById(snoozeId)
                                } else {
                                    db.snoozeDao().deleteByAlarm(kind, alarmId.toLong())
                                }
                            }.onFailure {
                                Log.e(TAG, "delete consumed snooze failed", it)
                            }
                        }

                        AlarmFireCoordinator.run(
                            shouldReschedule = !snoozed && kind == AlarmScheduler.KIND_CYCLE,
                            reschedule = {
                                // 周期闹钟响铃后立即计算下一次触发（假期自动跳过/恢复的逻辑在这里生效）；
                                // 按日期闹钟是一次性的，触发后其时间点已过，无需再排期。
                                val db = AppDatabase.get(context)
                                val alarm = db.alarmDao().getById(alarmId)
                                if (alarm != null && alarm.enabled) {
                                    AlarmScheduler.scheduleAlarm(
                                        context,
                                        alarm,
                                        db.settingsDao().get(),
                                        db.holidayDao().getAll()
                                    )
                                }
                            },
                            startService = {
                                ContextCompat.startForegroundService(
                                    context,
                                    RingService.ringIntent(
                                        context = context,
                                        alarmId = alarmId,
                                        dayIndex = dayIndex,
                                        hour = hour,
                                        minute = minute,
                                        label = label,
                                        snoozed = snoozed,
                                        kind = kind,
                                        originalHour = originalHour,
                                        originalMinute = originalMinute
                                    )
                                )
                            },
                            onFailure = { stage, throwable ->
                                when (stage) {
                                    AlarmFireCoordinator.FailureStage.RESCHEDULE -> {
                                        Log.e(TAG, "reschedule failed", throwable)
                                        // 排期失败比「这次没响」更难被发现，必须留可感知提示
                                        notifyRescheduleFailure(context, kind, hour, minute, label)
                                    }

                                    AlarmFireCoordinator.FailureStage.START_SERVICE -> {
                                        Log.e(TAG, "fgs failed", throwable)
                                        notifyRingServiceFailure(
                                            context, kind, alarmId, dayIndex, hour, minute, label
                                        )
                                    }
                                }
                            }
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun notifyRingServiceFailure(
        context: Context,
        kind: String,
        alarmId: Int,
        dayIndex: Int,
        hour: Int,
        minute: Int,
        label: String
    ) {
        val detail = label.ifBlank {
            if (kind == AlarmScheduler.KIND_DATE) "按日期闹钟" else "第 ${dayIndex + 1} 天"
        }
        postFallback(
            context = context,
            notificationId = FALLBACK_NOTIFICATION_BASE + AlarmScheduler.schedulerId(kind, alarmId),
            title = "闹钟响铃服务启动失败",
            text = "${DisplayPrefs.formatTime(context, hour, minute)} · $detail"
        )
    }

    private fun notifyRescheduleFailure(
        context: Context,
        kind: String,
        hour: Int,
        minute: Int,
        label: String
    ) {
        val detail = label.ifBlank {
            if (kind == AlarmScheduler.KIND_DATE) "按日期闹钟" else "闹钟"
        }
        postFallback(
            context = context,
            notificationId = RESCHEDULE_NOTIFICATION_ID,
            title = "下一次闹钟排期失败",
            text = "${DisplayPrefs.formatTime(context, hour, minute)} · $detail，请打开 App 检查"
        )
    }

    /**
     * 兜底通知：走独立渠道（不会被「响铃」渠道关闭连带影响）、发前检查权限、
     * 点击进主界面、并带一个「立即重排」action。
     */
    private fun postFallback(
        context: Context,
        notificationId: Int,
        title: String,
        text: String
    ) {
        if (!NotificationPermissionCheck.canSignal(context, NotificationChannels.RING_FALLBACK)) {
            Log.w(TAG, "fallback blocked: notification/channel unavailable ($title)")
            return
        }
        runCatching {
            NotificationChannels.createAll(context)
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val resyncIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, AlarmReceiver::class.java)
                    .setAction(AlarmScheduler.ACTION_RESYNC),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat
                .Builder(context, NotificationChannels.RING_FALLBACK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .addAction(0, "立即重排", resyncIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.onFailure {
            Log.e(TAG, "fallback notification failed", it)
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
        const val FALLBACK_NOTIFICATION_BASE = 2_000_000
        const val RESCHEDULE_NOTIFICATION_ID = 2_000_001
    }
}
