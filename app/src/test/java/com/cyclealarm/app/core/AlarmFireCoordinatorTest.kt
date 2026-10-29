package com.cyclealarm.app.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `reschedule timeout is reported and service still starts`() {
        val events = mutableListOf<String>()
        var failureStage: AlarmFireCoordinator.FailureStage? = null

        runBlocking {
            AlarmFireCoordinator.run(
                shouldReschedule = true,
                reschedule = {
                    events += "reschedule"
                    delay(300)
                },
                startService = { events += "service" },
                onFailure = { stage, _ -> failureStage = stage },
                timeoutMs = 50
            )
        }

        assertEquals(listOf("reschedule", "service"), events)
        assertEquals(AlarmFireCoordinator.FailureStage.RESCHEDULE, failureStage)
    }

    @Test
    fun `cancellation is not swallowed`() {
        var propagated = false
        try {
            runBlocking {
                AlarmFireCoordinator.run(
                    shouldReschedule = true,
                    reschedule = { throw CancellationException("cancel") },
                    startService = {},
                    onFailure = { _, _ -> }
                )
            }
        } catch (cancelled: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }
}
