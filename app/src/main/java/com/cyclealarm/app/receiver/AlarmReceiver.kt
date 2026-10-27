package com.cyclealarm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.AlarmFireCoordinator
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.NotificationChannels
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
                context.stopService(Intent(context, RingService::class.java))
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
                                        context, alarmId, dayIndex, hour, minute, label, snoozed, kind
                                    )
                                )
                            },
                            onFailure = { stage, throwable ->
                                when (stage) {
                                    AlarmFireCoordinator.FailureStage.RESCHEDULE -> {
                                        Log.e(TAG, "reschedule failed", throwable)
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
        runCatching {
            NotificationChannels.createAll(context)
            val schedulerId = AlarmScheduler.schedulerId(kind, alarmId)
            val detail = label.ifBlank {
                if (kind == AlarmScheduler.KIND_DATE) "按日期闹钟" else "第 ${dayIndex + 1} 天"
            }
            val notification = NotificationCompat.Builder(context, NotificationChannels.RING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("闹钟响铃服务启动失败")
                .setContentText("${DisplayPrefs.formatTime(context, hour, minute)} · $detail")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context)
                .notify(FALLBACK_NOTIFICATION_BASE + schedulerId, notification)
        }.onFailure {
            Log.e(TAG, "fallback notification failed", it)
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
        const val FALLBACK_NOTIFICATION_BASE = 2_000_000
    }
}
