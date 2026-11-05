package com.cyclealarm.app.core

/**
 * 锁屏闹钟能不能正常弹出「停止/稍后」界面的健康检查（纯函数）。
 *
 * 真机定位（ColorOS / Android 14）：响铃界面能否出现在锁屏上，取决于四个系统设置：
 * 通知权限、闹钟响铃渠道、全屏通知（FSI）、悬浮窗（兜底界面）+ 电池白名单。
 * 任一缺失都会「响铃但没有可操作界面」，而应用此前完全不给提示。
 */
data class PermissionHealth(
    val notifications: Boolean,
    val ringChannel: Boolean,
    /** null = 系统没有这个概念（Android 14 以下） */
    val fullScreenIntent: Boolean?,
    val overlay: Boolean,
    /** null = 查询不到 */
    val batteryExempt: Boolean?
) {
    enum class Issue { NOTIFICATIONS, RING_CHANNEL, FULL_SCREEN_INTENT, OVERLAY, BATTERY }
}

object PermissionHealthPolicy {

    /** 按「对锁屏响铃界面影响从大到小」的顺序返回缺失项。 */
    fun issues(health: PermissionHealth): List<PermissionHealth.Issue> = buildList {
        if (!health.notifications) add(PermissionHealth.Issue.NOTIFICATIONS)
        if (!health.ringChannel) add(PermissionHealth.Issue.RING_CHANNEL)
        if (health.fullScreenIntent == false) add(PermissionHealth.Issue.FULL_SCREEN_INTENT)
        if (!health.overlay) add(PermissionHealth.Issue.OVERLAY)
        if (health.batteryExempt == false) add(PermissionHealth.Issue.BATTERY)
    }

    fun isHealthy(health: PermissionHealth): Boolean = issues(health).isEmpty()
}
