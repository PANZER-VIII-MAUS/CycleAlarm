package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁屏响铃健康检查（真机教训：ColorOS 上缺任一设置就「只有铃声没界面」）。
 */
class PermissionHealthPolicyTest {

    private fun health(
        notifications: Boolean = true,
        ringChannel: Boolean = true,
        fullScreenIntent: Boolean? = true,
        overlay: Boolean = true,
        batteryExempt: Boolean? = true
    ) = PermissionHealth(notifications, ringChannel, fullScreenIntent, overlay, batteryExempt)

    @Test
    fun `healthy setup has no issues`() {
        assertTrue(PermissionHealthPolicy.isHealthy(health()))
        assertEquals(emptyList<PermissionHealth.Issue>(), PermissionHealthPolicy.issues(health()))
    }

    @Test
    fun `missing notifications is reported first`() {
        val issues = PermissionHealthPolicy.issues(health(notifications = false, overlay = false))
        assertEquals(PermissionHealth.Issue.NOTIFICATIONS, issues.first())
        assertTrue(PermissionHealth.Issue.OVERLAY in issues)
    }

    @Test
    fun `overlay and battery missing are both reported`() {
        val issues = PermissionHealthPolicy.issues(health(overlay = false, batteryExempt = false))
        assertTrue(PermissionHealth.Issue.OVERLAY in issues)
        assertTrue(PermissionHealth.Issue.BATTERY in issues)
        assertFalse(PermissionHealthPolicy.isHealthy(health(overlay = false)))
    }

    @Test
    fun `full screen intent only counts when the system reports false`() {
        // null = 系统没有这个概念（Android 14 以下），不应报缺失
        assertEquals(emptyList<PermissionHealth.Issue>(), PermissionHealthPolicy.issues(health(fullScreenIntent = null)))
        assertTrue(
            PermissionHealth.Issue.FULL_SCREEN_INTENT in
                PermissionHealthPolicy.issues(health(fullScreenIntent = false))
        )
    }

    @Test
    fun `unknown battery state does not raise an issue`() {
        assertTrue(PermissionHealthPolicy.issues(health(batteryExempt = null)).isEmpty())
    }
    @Test
    fun `battery missing is informational, not blocking`() {
        val health = health(batteryExempt = false)
        assertTrue(PermissionHealthPolicy.issues(health).contains(PermissionHealth.Issue.BATTERY))
        assertTrue("电池项不应出现在阻断列表", PermissionHealthPolicy.blockingIssues(health).isEmpty())
        assertEquals(
            listOf(PermissionHealth.Issue.BATTERY),
            PermissionHealthPolicy.informationalIssues(health)
        )
    }

    @Test
    fun `notification missing is still blocking`() {
        val health = health(notifications = false)
        assertTrue(PermissionHealth.Issue.NOTIFICATIONS in PermissionHealthPolicy.blockingIssues(health))
    }
}
