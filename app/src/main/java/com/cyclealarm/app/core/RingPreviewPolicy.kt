package com.cyclealarm.app.core

/**
 * 试听（预览）与真实响铃的行为差异。
 *
 * 试听走的是生产链路（同一个 RingService），如果不加区分，
 * 试听时点「稍后」会给一条还没到点的闹钟落 snooze（之后真会响），
 * 点「停止」又会清掉该闹钟真实待触发的 snooze —— 都会污染真实状态。
 */
object RingPreviewPolicy {

    /** 是否允许「稍后提醒」：试听态禁止。 */
    fun allowSnooze(isPreview: Boolean): Boolean = !isPreview

    /** 是否允许写数据库（清 snooze / 落 snooze）：试听态禁止。 */
    fun allowDatabaseWrites(isPreview: Boolean): Boolean = !isPreview
}
