package com.cyclealarm.app.core

import com.cyclealarm.app.R
import com.cyclealarm.app.data.ActivityTypeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧迁移写死的中文默认类型名（上课/自习/休息）要能按语言本地化，
 * 且不能碰用户自己改过的名字。
 */
class DefaultTypeLocalizerTest {

    private fun type(id: Int, name: String) =
        ActivityTypeEntity(id = id, name = name, hour = 8, minute = 0, rings = true, sortOrder = id)

    private val english = mapOf(
        R.string.vm_default_class to "Class",
        R.string.vm_default_study to "Study",
        R.string.vm_default_break to "Break"
    )

    @Test
    fun `legacy chinese defaults are renamed to the current language`() {
        val renames = DefaultTypeLocalizer.plan(
            listOf(type(1, "上课"), type(2, "自习"), type(3, "休息")),
            english
        )
        assertEquals(3, renames.size)
        assertEquals(listOf("Class", "Study", "Break"), renames.map { it.to })
    }

    @Test
    fun `custom names are left untouched`() {
        val renames = DefaultTypeLocalizer.plan(
            listOf(type(1, "早读"), type(2, "夜班"), type(3, "休息")),
            english
        )
        assertEquals(1, renames.size)
        assertEquals(3, renames.single().typeId)
    }

    @Test
    fun `already localized names produce no work`() {
        val renames = DefaultTypeLocalizer.plan(
            listOf(type(1, "上课")),
            mapOf(R.string.vm_default_class to "上课")
        )
        assertTrue("目标名与现名相同不应产生改名", renames.isEmpty())
    }
}
