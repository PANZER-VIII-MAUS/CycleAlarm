package com.cyclealarm.app.service

import android.content.Context

/**
 * 常驻守护服务的启动入口（play flavor：Manifest 已删除该服务，这里全部 no-op）。
 */
object GuardServiceStarter {

    fun start(context: Context): Boolean = false

    fun stop(context: Context) = Unit
}
