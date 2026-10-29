package com.cyclealarm.app.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 执行「先重排、后起响铃服务」的顺序与异常隔离。
 *
 * 0.2.3 收敛点：
 * - 重排加超时（数据库长事务时不至于把响铃无限期推迟，响铃优先）；
 * - `CancellationException` 不吞（协程取消语义保留）；
 * - `TimeoutCancellationException` 归为「重排失败」而不是取消，后续仍要起服务。
 */
object AlarmFireCoordinator {

    enum class FailureStage {
        RESCHEDULE,
        START_SERVICE
    }

    /** 重排允许占用的最长时间。 */
    const val RESCHEDULE_TIMEOUT_MS = 8_000L

    suspend fun run(
        shouldReschedule: Boolean,
        reschedule: suspend () -> Unit,
        startService: suspend () -> Unit,
        onFailure: (FailureStage, Throwable) -> Unit,
        timeoutMs: Long = RESCHEDULE_TIMEOUT_MS
    ) {
        fun reportFailure(stage: FailureStage, throwable: Throwable) {
            runCatching { onFailure(stage, throwable) }
        }

        if (shouldReschedule) {
            try {
                withTimeout(timeoutMs) { reschedule() }
            } catch (timeout: TimeoutCancellationException) {
                // 超时属于「重排失败」，不能当成整体取消
                reportFailure(FailureStage.RESCHEDULE, timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Exception) {
                reportFailure(FailureStage.RESCHEDULE, throwable)
            }
        }

        try {
            startService()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Exception) {
            reportFailure(FailureStage.START_SERVICE, throwable)
        }
    }
}
