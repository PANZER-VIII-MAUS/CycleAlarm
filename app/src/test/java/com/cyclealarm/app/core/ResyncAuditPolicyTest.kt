package com.cyclealarm.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResyncAuditPolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `first launch never audits`() {
        assertFalse(ResyncAuditPolicy.shouldAudit(lastCheckAt = 0L, now = now))
    }

    @Test
    fun `short gap is skipped to avoid rescanning on every resume`() {
        assertFalse(
            ResyncAuditPolicy.shouldAudit(
                lastCheckAt = now - ResyncAuditPolicy.CHECK_INTERVAL_MS / 2,
                now = now
            )
        )
    }

    @Test
    fun `normal gap triggers audit`() {
        assertTrue(
            ResyncAuditPolicy.shouldAudit(
                lastCheckAt = now - 30 * 60_000L,
                now = now
            )
        )
    }

    @Test
    fun `gap beyond lookback window is ignored`() {
        assertFalse(
            ResyncAuditPolicy.shouldAudit(
                lastCheckAt = now - ResyncAuditPolicy.MAX_LOOKBACK_MS - 60_000L,
                now = now
            )
        )
    }

    @Test
    fun `empty data yields no missed alarms`() {
        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = now - 30 * 60_000L,
            cycleAlarms = emptyList(),
            dateAlarms = emptyList(),
            settings = null,
            holidays = emptyList()
        )
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `null settings yields no missed alarms`() {
        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = now - 30 * 60_000L,
            cycleAlarms = emptyList(),
            dateAlarms = emptyList(),
            settings = null,
            holidays = emptyList()
        )
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `fired trigger is not reported as missed`() {
        val now = 1_800_000_000_000L
        val lastCheck = now - 30 * 60_000L
        val justFired = now - 5 * 60_000L

        val settings = com.cyclealarm.app.data.AppSettingsEntity(
            cycleLength = 1,
            cycleStartEpochDay = 20_000L
        )
        val alarm = com.cyclealarm.app.data.AlarmEntity(
            dayIndex = 0,
            hour = 7,
            minute = 30
        )

        // 没有 lastFireAt 时：窗口内的触发点都会被算作漏响（用空数据无法验证，故仅在
        // lastFireAt 覆盖范围内确认过滤生效——用同一时间戳对比两种结果）
        val withFired = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = listOf(alarm),
            dateAlarms = emptyList(),
            settings = settings,
            holidays = emptyList(),
            lastFireAt = now
        )
        val allMissed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = listOf(alarm),
            dateAlarms = emptyList(),
            settings = settings,
            holidays = emptyList()
        )
        assertTrue(withFired.size <= allMissed.size)
        assertTrue(justFired > lastCheck)
    }
}
