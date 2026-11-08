package com.cyclealarm.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 常驻守护服务的启动入口（direct flavor 真实实现）。
 *
 * 主代码不直接引用 [AlarmGuardService]：play flavor 里该类根本不存在，
 * 统一通过本对象调用（play 侧是同名 no-op 实现）。
 */
object GuardServiceStarter {

    /** @return 是否成功发出启动请求（同步异常视为失败）。 */
    fun start(context: Context): Boolean = runCatching {
        ContextCompat.startForegroundService(context, Intent(context, AlarmGuardService::class.java))
        true
    }.getOrDefault(false)

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, AlarmGuardService::class.java)) }
    }
}
