package com.cyclealarm.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 0.6.11 复查 §3.4 的回归：前台化失败必须能被持久化感知，
 * 设置页据此显示「已开启（但服务启动失败）」，而不是只显示用户意图。
 *
 * 真机上 ColorOS 不拦这个服务，无法触发真实失败；这里锁住状态机本身
 * （服务失败 → recordFailure；成功 → clearFailure；设置页读 lastFailureAt）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuardServiceHealthRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `failure is recorded and cleared`() {
        GuardServicePolicy.clearFailure(context)
        assertNull("初始状态应无失败记录", GuardServicePolicy.lastFailureAt(context))

        GuardServicePolicy.recordFailure(context)
        val at = GuardServicePolicy.lastFailureAt(context)
        assertNotNull("失败后应能读到时间戳", at)
        assertTrue((at ?: 0L) > 0L)

        GuardServicePolicy.clearFailure(context)
        assertNull("成功启动后失败标记应被清掉", GuardServicePolicy.lastFailureAt(context))
    }

    @Test
    fun `enabled state is independent from failure state`() {
        GuardServicePolicy.clearFailure(context)
        GuardServicePolicy.setEnabled(context, true)
        GuardServicePolicy.recordFailure(context)
        // 失败不应该把用户意图改成关闭——UI 靠「已开启 + 有失败记录」显示异常状态
        assertTrue(GuardServicePolicy.isEnabled(context))
        assertNotNull(GuardServicePolicy.lastFailureAt(context))
        GuardServicePolicy.setEnabled(context, false)
        GuardServicePolicy.clearFailure(context)
    }
}
