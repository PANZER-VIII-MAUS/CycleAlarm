package com.cyclealarm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.core.BootResyncPolicy
import com.cyclealarm.app.core.ResyncAuditPolicy
import com.cyclealarm.app.core.ResyncCheckpoint
import com.cyclealarm.app.service.AlarmGuardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机 / 改时间 / 时区变化 / 应用升级后重建闹钟排期并拉起守护服务。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // NEXT_ALARM_CLOCK_CHANGED 只做对账：整轮重排会再次改变系统闹钟并再次触发该广播，
        // 形成死循环（0.2.3 实测：同一秒内触发数十次）。
        if (BootResyncPolicy.shouldAuditOnly(action)) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (ResyncAuditPolicy.shouldSkipFullResync(
                            ResyncCheckpoint.lastCheckAt(context),
                            System.currentTimeMillis()
                        )
                    ) {
                        Log.d(TAG, "skip audit: resynced too recently")
                    } else {
                        AlarmResyncHelper.auditNextAlarm(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "audit next alarm failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (!BootResyncPolicy.shouldResync(action)) return

        // 任何来源的整轮重排都做防抖，避免广播回环
        if (ResyncAuditPolicy.shouldSkipFullResync(
                ResyncCheckpoint.lastCheckAt(context),
                System.currentTimeMillis()
            )
        ) {
            Log.d(TAG, "skip full resync: too soon after last one ($action)")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { AlarmResyncHelper.resyncAll(context) }
                    .onFailure {
                        Log.e(TAG, "scheduled resync failed for $action", it)
                    }
                runCatching {
                    ContextCompat.startForegroundService(
                            context,
                            Intent(context, AlarmGuardService::class.java)
                        )
                }.onFailure {
                    Log.e(TAG, "guard service start failed for ${intent.action}", it)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
