package com.cyclealarm.app.core

import android.content.Context
import com.cyclealarm.app.R
import com.cyclealarm.app.data.ActivityTypeEntity

/**
 * 把「旧版本迁移写死的中文默认类型名」本地化。
 *
 * 历史原因：v5→v6 迁移用 SQL 直接插入 `('上课'),('自习'),('休息')`，这两行数据不随界面语言变化，
 * 英文界面里会一直显示中文（五轮复审 §4.8）。
 *
 * 处理原则：
 * - 只改**恰好等于旧默认名**的类型，用户自己改过的名字一律不动；
 * - 目标名取当前应用语言下的默认文案，若与现名相同则跳过（幂等）。
 */
object DefaultTypeLocalizer {

    /** 旧迁移写死的中文默认名 → 资源键。 */
    private val LEGACY_NAMES = mapOf(
        "上课" to R.string.vm_default_class,
        "自习" to R.string.vm_default_study,
        "休息" to R.string.vm_default_break
    )

    data class Rename(val typeId: Int, val from: String, val to: String)

    /** 纯函数：算出需要改名的类型（便于单测）。传入目标语言的默认名。 */
    fun plan(types: List<ActivityTypeEntity>, localized: Map<Int, String>): List<Rename> =
        types.mapNotNull { type ->
            val resId = LEGACY_NAMES[type.name] ?: return@mapNotNull null
            val target = localized[resId] ?: return@mapNotNull null
            if (target == type.name) null else Rename(type.id, type.name, target)
        }

    /** 当前语言下的默认类型名。 */
    fun localizedDefaults(context: Context): Map<Int, String> =
        LEGACY_NAMES.values.associateWith { context.getString(it) }

    /** 应用改名（在 IO 线程调用）。 */
    suspend fun applyIfNeeded(
        context: Context,
        loadTypes: suspend () -> List<ActivityTypeEntity>,
        rename: suspend (Int, String) -> Unit
    ): Int {
        val types = runCatching { loadTypes() }.getOrNull().orEmpty()
        val renames = plan(types, localizedDefaults(context))
        renames.forEach { runCatching { rename(it.typeId, it.to) } }
        return renames.size
    }
}
