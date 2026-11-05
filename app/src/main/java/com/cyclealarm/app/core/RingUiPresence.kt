package com.cyclealarm.app.core

/**
 * 「响铃界面是否真的显示出来了」的运行时标记。
 *
 * RingService 拉起 [com.cyclealarm.app.ui.ring.RingActivity] 后，系统可能因为
 * 后台启动限制（BAL）或 ROM 定制策略直接拦掉——`startActivity` 不抛异常，只是被拒。
 * 所以在 Activity 的 onResume/onPause 里打标记，服务侧延迟检查：
 * 没显示出来就退到「悬浮窗兜底界面」。
 */
object RingUiPresence {

    @Volatile
    private var visible: Boolean = false

    /** 最近一次「响铃界面可见」的时间戳。 */
    @Volatile
    var lastVisibleAt: Long = 0L
        private set

    fun onActivityResumed() {
        visible = true
        lastVisibleAt = System.currentTimeMillis()
    }

    fun onActivityPaused() {
        visible = false
    }

    fun isVisible(): Boolean = visible
}
