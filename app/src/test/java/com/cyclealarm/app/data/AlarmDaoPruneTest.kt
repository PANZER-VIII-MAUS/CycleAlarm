package com.cyclealarm.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
 * 切到「每周」时只能回收「安排生成」的越界闹钟，手动闹钟必须保留（四轮复审 H2）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmDaoPruneTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db: AppDatabase get() = AppDatabase.get(context)

    @Before
    fun clean() = runBlocking {
        db.alarmDao().deleteAll()
    }

    @After
    fun tearDown() = runBlocking {
        db.alarmDao().deleteAll()
    }

    @Test
    fun `only plan generated alarms beyond the weekly range are removed`() = runBlocking {
        db.alarmDao().upsert(
            AlarmEntity(dayIndex = 3, hour = 7, minute = 0, label = "周内", planGenerated = true)
        )
        db.alarmDao().upsert(
            AlarmEntity(dayIndex = 8, hour = 8, minute = 0, label = "安排越界", planGenerated = true)
        )
        db.alarmDao().upsert(
            AlarmEntity(dayIndex = 9, hour = 9, minute = 0, label = "手动越界", planGenerated = false)
        )

        db.alarmDao().deletePlanGeneratedFromIndex(7)

        val remaining = db.alarmDao().getAll().map { it.label }.toSet()
        assertTrue("周内安排应保留", "周内" in remaining)
        assertTrue("手动闹钟必须保留", "手动越界" in remaining)
        assertEquals(false, "安排越界" in remaining)
    }
}
