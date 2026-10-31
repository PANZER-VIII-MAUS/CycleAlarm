package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedAlarmMergePolicyTest {

    private fun missed(at: Long, label: String = "闹钟", kind: String = "CYCLE") =
        MissedAlarm(label = label, expectedAt = at, kind = kind)

    @Test
    fun `duplicates from both sources are merged`() {
        val computed = listOf(missed(1_000))
        val events = listOf(missed(1_000))
        val merged = MissedAlarmMergePolicy.merge(computed, events, lastCheckAt = 0)
        assertEquals(1, merged.size)
    }

    @Test
    fun `both sources contribute distinct entries`() {
        val computed = listOf(missed(1_000))
        val events = listOf(missed(2_000))
        val merged = MissedAlarmMergePolicy.merge(computed, events, lastCheckAt = 0)
        assertEquals(listOf(1_000L, 2_000L), merged.map { it.expectedAt })
    }

    @Test
    fun `same timestamp but different kind is not merged`() {
        val computed = listOf(missed(1_000, kind = "CYCLE"))
        val events = listOf(missed(1_000, kind = "DATE"))
        assertEquals(2, MissedAlarmMergePolicy.merge(computed, events, 0).size)
    }

    @Test
    fun `entries at or before the checkpoint are dropped`() {
        val computed = listOf(missed(1_000), missed(2_000))
        val merged = MissedAlarmMergePolicy.merge(computed, emptyList(), lastCheckAt = 1_000)
        assertEquals(listOf(2_000L), merged.map { it.expectedAt })
    }

    @Test
    fun `result is sorted by expected time`() {
        val events = listOf(missed(3_000), missed(1_000))
        val merged = MissedAlarmMergePolicy.merge(emptyList(), events, 0)
        assertTrue(merged[0].expectedAt < merged[1].expectedAt)
    }
}
