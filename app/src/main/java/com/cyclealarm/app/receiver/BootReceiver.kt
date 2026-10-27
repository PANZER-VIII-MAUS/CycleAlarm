package com.cyclealarm.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.service.AlarmGuardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机 / 改时间 / 时区变化 / 应用升级后重建闹钟排期并拉起守护服务。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        runCatching { AlarmResyncHelper.resyncAll(context) }
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, AlarmGuardService::class.java)
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
