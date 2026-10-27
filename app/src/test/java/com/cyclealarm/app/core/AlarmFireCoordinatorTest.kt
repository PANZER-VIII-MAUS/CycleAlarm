package com.cyclealarm.app.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmFireCoordinatorTest {

    @Test
    fun `reschedules before starting service when reschedule fails`() {
        val events = mutableListOf<String>()
        var failureStage: AlarmFireCoordinator.FailureStage? = null

        runBlocking {
            AlarmFireCoordinator.run(
                shouldReschedule = true,
                reschedule = {
                    events += "reschedule"
                    error("reschedule failed")
                },
                startService = { events += "service" },
                onFailure = { stage, _ -> failureStage = stage }
            )
        }

        assertEquals(listOf("reschedule", "service"), events)
        assertEquals(AlarmFireCoordinator.FailureStage.RESCHEDULE, failureStage)
    }

    @Test
    fun `service failure is contained after reschedule`() {
        val events = mutableListOf<String>()
        var failureStage: AlarmFireCoordinator.FailureStage? = null

        runBlocking {
            AlarmFireCoordinator.run(
                shouldReschedule = true,
                reschedule = { events += "reschedule" },
                startService = {
                    events += "service"
                    error("service failed")
                },
                onFailure = { stage, _ -> failureStage = stage }
            )
        }

        assertEquals(listOf("reschedule", "service"), events)
        assertEquals(AlarmFireCoordinator.FailureStage.START_SERVICE, failureStage)
    }

    @Test
    fun `snooze skips reschedule but still starts service`() {
        val events = mutableListOf<String>()
        var failureStage: AlarmFireCoordinator.FailureStage? = null

        runBlocking {
            AlarmFireCoordinator.run(
                shouldReschedule = false,
                reschedule = { events += "reschedule" },
                startService = { events += "service" },
                onFailure = { stage, _ -> failureStage = stage }
            )
        }

        assertEquals(listOf("service"), events)
        assertNull(failureStage)
    }
}
