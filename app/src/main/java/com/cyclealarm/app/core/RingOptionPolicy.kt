package com.cyclealarm.app.core

/**
 * 每闹钟响铃选项的纯逻辑（铃声解析 + 渐强音量曲线）。
 * 与 Android API 无关，便于单测。
 */
object RingOptionPolicy {

    /** 渐强起点音量。 */
    const val RAMP_START = 0.3f

    /** 每次提升的步长。 */
    const val RAMP_STEP = 0.1f

    /** 提升间隔（毫秒）。 */
    const val RAMP_INTERVAL_MS = 3_000L

    /** 自定义铃声为空时回退到系统默认（返回 null 表示用全局默认）。 */
    fun resolveUri(custom: String?): String? = custom?.takeIf { it.isNotBlank() }

    /**
     * 第 [step] 步的目标音量（0.3 → 1.0，超出后保持 1.0）。
     */
    fun rampVolume(step: Int): Float {
        val safeStep = step.coerceAtLeast(0)
        return (RAMP_START + RAMP_STEP * safeStep).coerceAtMost(1f)
    }

    /** 从起始音量升到满音量需要多少步。 */
    fun rampSteps(): Int {
        var step = 0
        while (rampVolume(step) < 1f) {
            step++
            if (step > 100) return step
        }
        return step
    }
}
