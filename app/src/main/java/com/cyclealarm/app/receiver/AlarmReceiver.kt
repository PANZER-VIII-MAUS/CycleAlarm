package com.cyclealarm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cyclealarm.app.core.AlarmScheduler
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

                        ContextCompat.startForegroundService(
                            context,
                            RingService.ringIntent(
                                context, alarmId, dayIndex, hour, minute, label, snoozed, kind
                            )
                        )

                        // 周期闹钟响铃后立即计算下一次触发（假期自动跳过/恢复的逻辑在这里生效）；
                        // 按日期闹钟是一次性的，触发后其时间点已过，无需再排期。
                        if (!snoozed && kind == AlarmScheduler.KIND_CYCLE) {
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
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
