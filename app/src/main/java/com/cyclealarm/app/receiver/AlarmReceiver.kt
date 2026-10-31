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
import com.cyclealarm.app.core.ResyncCheckpoint
import com.cyclealarm.app.data.AppDatabase
import com.cyclealarm.app.data.RingEventEntity
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
                        AlarmResyncHelper.resyncAll(context, auditMissed = false)
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
                        // v10：每闹钟响铃设置随 FIRE 广播传来，转交响铃服务
                        val ringtoneUri = intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI)
                        val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true)
                        val rampVolume = intent.getBooleanExtra(AlarmScheduler.EXTRA_RAMP_VOLUME, false)

                        // 幽灵守护：系统里可能残留「已被删除/已停用」的闹钟 PendingIntent。
                        // 这种情况下不能响铃，否则用户会听到一条已不存在的闹钟。
                        if (!snoozed) {
                            val stillActive = runCatching {
                                val db = AppDatabase.get(context)
                                if (kind == AlarmScheduler.KIND_DATE) {
                                    db.dateAlarmDao().getAll().any { it.id == alarmId && it.enabled }
                                } else {
                                    db.alarmDao().getById(alarmId)?.enabled == true
                                }
                            }.getOrDefault(true)
                            if (!stillActive) {
                                Log.w(TAG, "stale fire for missing/disabled alarm id=$alarmId, ignored")
                                return@launch
                            }
                        }

                        // v9：先登记一条「应有事件」，再靠 rungAt 区分「真的响了」与「响铃失败」。
                        // snooze 触发时登记原定时间，这样漏响判定对的是原闹钟而不是 snooze 时间。
                        val firedNow = System.currentTimeMillis()
                        val expectedAt = if (snoozed && originalHour != null && originalMinute != null) {
                            AlarmScheduler.expectedMillisOfToday(
                                originalHour!!, originalMinute!!, firedNow
                            )
                        } else {
                            AlarmScheduler.expectedMillisOfToday(hour, minute, firedNow)
                        }
                        val ringEventId = runCatching {
                            AppDatabase.get(context).ringEventDao().insert(
                                RingEventEntity(
                                    alarmKind = kind,
                                    alarmId = alarmId,
                                    label = label,
                                    expectedAt = expectedAt
                                )
                            )
                        }.getOrDefault(-1L)

                        // 兼容旧检查点：旧版本升级上来的第一轮仍按时间戳豁免，避免误报
                        ResyncCheckpoint.markFired(context, expectedAt)

                        // snooze 行在「响铃服务成功拉起」之后才消费：
                        // 先删行再起服务的话，一旦起服务失败，这次稍后提醒就永久消失。
                        var serviceStarted = false

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
                                        originalMinute = originalMinute,
                                        ringtoneUri = ringtoneUri,
                                        vibrate = vibrate,
                                        rampVolume = rampVolume
                                    )
                                )
                                serviceStarted = true
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

                        // 服务真的起来了才算「响过」；起不来时保持 rungAt = null，
                        // 漏响审计就能精确捕获「响铃服务启动失败」这种以前检测不到的情况
                        if (serviceStarted && ringEventId > 0L) {
                            runCatching {
                                AppDatabase.get(context).ringEventDao()
                                    .markRung(ringEventId, System.currentTimeMillis())
                            }
                        }

                        if (snoozed && serviceStarted) {
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
        // 先建渠道再判断：冷启动时渠道可能还不存在，否则会被自己的检查挡掉
        NotificationChannels.createAll(context)
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
        // 通知 id 分区：1 = 前台服务；2 = 响铃；2_000_000 + schedulerId = 兜底；
        // 3_000_001 = 排期失败（避开兜底区间，三轮复审 §4.4-1）
        const val RESCHEDULE_NOTIFICATION_ID = 3_000_001
    }
}
