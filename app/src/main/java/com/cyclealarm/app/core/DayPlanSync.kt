package com.cyclealarm.app.core

import com.cyclealarm.app.data.ActivityTypeEntity
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.DayPlanEntity

/** 安排类型的纯内存表示（不碰数据库）。 */
data class PlanType(
    val id: Int,
    val name: String,
    val hour: Int,
    val minute: Int,
    val rings: Boolean
)

/** 某天安排的纯内存表示。 */
data class PlanRow(
    val dayIndex: Int,
    val typeId: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
)

/** 由安排推导出的闹钟操作。 */
sealed interface AlarmOp {
    data class Insert(
        val dayIndex: Int,
        val hour: Int,
        val minute: Int,
        val label: String
    ) : AlarmOp

    data class Update(
        val alarmId: Int,
        val dayIndex: Int,
        val hour: Int,
        val minute: Int,
        val label: String
    ) : AlarmOp

    data class Delete(val alarmId: Int) : AlarmOp
}

/**
 * 「循环安排 → 闹钟」的纯函数差异计算：只吃内存数据、吐操作列表，不碰 Context/数据库。
 *
 * 规则：
 * 1. 期望闹钟 = plan.enabled && type.rings；时间用 plan.hour/minute（不是类型默认值），label 用类型名；
 * 2. 现有安排闹钟 = alarms.filter { planGenerated }，按 dayIndex 匹配；
 * 3. planGenerated=true 但无 plan / plan 变休息 / plan 停用 / typeId 不存在 → 删除；
 * 4. planGenerated=false 的手动/导入闹钟一律不碰，但它们会参与"同分钟冲突"判断：
 *    当天已有同 (hour, minute) 的手动闹钟时，安排闹钟不生成；已存在则删除；
 * 5. 输出按 dayIndex 升序、同天按 id 升序，保证同样输入得到同样输出。
 */
object DayPlanSync {

    fun diff(
        plans: List<PlanRow>,
        types: List<PlanType>,
        alarms: List<AlarmEntity>
    ): List<AlarmOp> {
        val typeById = types.associateBy { it.id }
        val planByDay = plans.associateBy { it.dayIndex }
        val manualByDay = alarms.filter { !it.planGenerated }.groupBy { it.dayIndex }
        val planAlarmsByDay = alarms.filter { it.planGenerated }.groupBy { it.dayIndex }
        val ops = mutableListOf<AlarmOp>()

        // 1) 先处理已存在的安排闹钟（含清理同一天多余记录）
        planAlarmsByDay.keys.sorted().forEach { dayIndex ->
            val dayAlarms = planAlarmsByDay.getValue(dayIndex).sortedBy { it.id }
            val survivor = dayAlarms.first()
            dayAlarms.drop(1).forEach { ops += AlarmOp.Delete(it.id) }

            val plan = planByDay[dayIndex]
            val type = plan?.let { typeById[it.typeId] }
            val need = plan != null && type != null && plan.enabled && type.rings
            val conflict = plan != null && hasManualAt(manualByDay[dayIndex], plan.hour, plan.minute)

            when {
                plan == null || type == null || !need || conflict ->
                    ops += AlarmOp.Delete(survivor.id)

                survivor.hour != plan.hour ||
                    survivor.minute != plan.minute ||
                    survivor.label != type.name ->
                    ops += AlarmOp.Update(
                        alarmId = survivor.id,
                        dayIndex = dayIndex,
                        hour = plan.hour,
                        minute = plan.minute,
                        label = type.name
                    )

                else -> Unit // 一致，不操作
            }
        }

        // 2) 需要但还没有安排闹钟的天 → Insert（与手动闹钟同分钟则跳过）
        plans.sortedBy { it.dayIndex }.forEach { plan ->
            val type = typeById[plan.typeId] ?: return@forEach
            val need = plan.enabled && type.rings
            if (!need) return@forEach
            if (hasManualAt(manualByDay[plan.dayIndex], plan.hour, plan.minute)) return@forEach
            if (planAlarmsByDay.containsKey(plan.dayIndex)) return@forEach
            ops += AlarmOp.Insert(
                dayIndex = plan.dayIndex,
                hour = plan.hour,
                minute = plan.minute,
                label = type.name
            )
        }

        return ops
    }

    fun toPlanRow(entity: DayPlanEntity): PlanRow = PlanRow(
        dayIndex = entity.dayIndex,
        typeId = entity.typeId,
        hour = entity.hour,
        minute = entity.minute,
        enabled = entity.enabled
    )

    fun toPlanType(entity: ActivityTypeEntity): PlanType = PlanType(
        id = entity.id,
        name = entity.name,
        hour = entity.hour,
        minute = entity.minute,
        rings = entity.rings
    )

    private fun hasManualAt(manualAlarms: List<AlarmEntity>?, hour: Int, minute: Int): Boolean =
        manualAlarms.orEmpty().any { it.hour == hour && it.minute == minute }
}
