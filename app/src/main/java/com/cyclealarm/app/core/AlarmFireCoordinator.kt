package com.cyclealarm.app.core

/**
 * Executes the callback ordering and exception isolation around an alarm fire event.
 */
object AlarmFireCoordinator {
    enum class FailureStage {
        RESCHEDULE,
        START_SERVICE
    }

    suspend fun run(
        shouldReschedule: Boolean,
        reschedule: suspend () -> Unit,
        startService: suspend () -> Unit,
        onFailure: (FailureStage, Throwable) -> Unit
    ) {
        fun reportFailure(stage: FailureStage, throwable: Throwable) {
            runCatching { onFailure(stage, throwable) }
        }

        if (shouldReschedule) {
            try {
                reschedule()
            } catch (throwable: Throwable) {
                reportFailure(FailureStage.RESCHEDULE, throwable)
            }
        }

        try {
            startService()
        } catch (throwable: Throwable) {
            reportFailure(FailureStage.START_SERVICE, throwable)
        }
    }
}
