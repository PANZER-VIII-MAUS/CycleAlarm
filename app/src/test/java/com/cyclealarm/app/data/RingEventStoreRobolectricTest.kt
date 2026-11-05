package com.cyclealarm.app.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 重复触发的幂等登记（五轮复审 B#2）：
 * 唯一索引 + IGNORE 时代，重复登记返回 -1，调用方拿不到行 id，
 * `markRung` 无处可写 → 明明响过的闹钟会被判成漏响。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RingEventStoreRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao get() = AppDatabase.get(context).ringEventDao()

    @Before
    fun clean() {
        // 其它测试类会 reset 单例并 close 库：先重建实例，避免拿到已关闭的连接
        AppDatabase.resetInstanceForTest()
        runBlocking { dao.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { dao.deleteAll() }
        AppDatabase.resetInstanceForTest()
    }

    @Test
    fun `registering the same trigger twice returns the same row`() = runBlocking {
        val expectedAt = 1_800_000_000_000L
        val first = RingEventStore.register(dao, "CYCLE", 5, "第一次", expectedAt)
        val second = RingEventStore.register(dao, "CYCLE", 5, "第二次", expectedAt)

        assertTrue("首次登记应拿到有效 id", first > 0L)
        assertEquals("重复登记必须返回同一行", first, second)
        dao.recent(10).let { assertEquals(1, it.size) }
    }

    @Test
    fun `mark rung works on a re-registered event`() = runBlocking {
        val expectedAt = 1_800_000_100_000L
        RingEventStore.register(dao, "DATE", 9, "日期", expectedAt)
        val id = RingEventStore.register(dao, "DATE", 9, "日期", expectedAt)

        dao.markRung(id, expectedAt + 500L)

        val row = dao.find("DATE", 9, expectedAt)
        assertTrue("重复登记后 rungAt 必须能写入", (row?.rungAt ?: 0L) > 0L)
    }

    @Test
    fun `blank label is backfilled on re-register`() = runBlocking {
        val expectedAt = 1_800_000_200_000L
        RingEventStore.register(dao, "CYCLE", 11, "", expectedAt)
        RingEventStore.register(dao, "CYCLE", 11, "白班", expectedAt)

        assertEquals("白班", dao.find("CYCLE", 11, expectedAt)?.label)
    }
}
