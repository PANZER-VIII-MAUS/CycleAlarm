package com.cyclealarm.app.core

/**
 * 响铃界面的显示策略（纯函数，便于单测）。
 *
 * 背景（真机定位）：在 ColorOS 上 alarm 触发后
 * - `startActivity(RingActivity)` 被后台启动限制（BAL_BLOCK）拦掉，且不抛异常；
 * - 通知里的 fullScreenIntent 也可能被系统拒绝（USE_FULL_SCREEN_INTENT 被 reject）。
 * 结果就是「响铃但没有任何界面」。
 *
 * 因此服务侧要能判断：Activity 到底显示出来了没有；没显示就退到悬浮窗兜底界面
 * （TYPE_APPLICATION_OVERLAY，需要用户授予「悬浮窗/后台弹出界面」权限）。
 */
object RingUiFallbackPolicy {

    /** 检查窗口显示的延迟：给 Activity 启动留出时间。 */
    const val OVERLAY_DELAY_MS = 1_200L

    /**
     * 是否需要弹出悬浮窗兜底界面。
     *
     * @param activityVisible 延迟检查时 [RingUiPresence.isVisible] 的结果
     * @param overlayPermitted 是否已授予悬浮窗权限
     * @param preview 是否只是试听（试听不弹兜底界面，避免打扰）
     */
    fun shouldShowOverlay(
        activityVisible: Boolean,
        overlayPermitted: Boolean,
        preview: Boolean
    ): Boolean = !preview && !activityVisible && overlayPermitted
}
