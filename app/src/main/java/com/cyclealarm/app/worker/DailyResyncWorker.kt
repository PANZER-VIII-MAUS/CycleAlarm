package com.cyclealarm.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cyclealarm.app.core.AlarmResyncHelper
import java.util.concurrent.TimeUnit

/**
 * 每日兜底任务：即使假期配置或日期跨天出现问题，
 * 每天也会从数据库重建一次全部闹钟排期。
 */
class DailyResyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            AlarmResyncHelper.resyncAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyResyncWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("daily_resync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
