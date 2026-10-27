package com.cyclealarm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.core.BootResyncPolicy
import com.cyclealarm.app.service.AlarmGuardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机 / 改时间 / 时区变化 / 应用升级后重建闹钟排期并拉起守护服务。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BootResyncPolicy.shouldResync(intent.action)) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { AlarmResyncHelper.resyncAll(context) }
                    .onFailure {
                        Log.e(TAG, "scheduled resync failed for ${intent.action}", it)
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
