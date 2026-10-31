package com.cyclealarm.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DayPlanAlarmSync
import com.cyclealarm.app.core.DayPlanSync
import com.cyclealarm.app.core.DeleteUndoPolicy
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.core.InstallMarker
import com.cyclealarm.app.core.NextAlarm
import com.cyclealarm.app.core.NextAlarmKind
import com.cyclealarm.app.core.TemporaryMuteStore
import com.cyclealarm.app.core.PlanDeletePolicy
import com.cyclealarm.app.core.PlanSeedPolicy
import com.cyclealarm.app.data.ActivityTypeEntity
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppDatabase
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.ChinaHolidaysProvider
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.DayPlanEntity
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.data.SnoozeEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val _databaseError = MutableStateFlow<String?>(null)
    val databaseError: StateFlow<String?> = _databaseError

    val alarms: StateFlow<List<AlarmEntity>> = db.alarmDao().observeAll()
        .guardDatabase(emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 安排类型（上课 / 自习 / 休息 …）。 */
    val activityTypes: StateFlow<List<ActivityTypeEntity>> = db.activityTypeDao().observeAll()
        .guardDatabase(emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 每个周期日的安排。 */
    val dayPlans: StateFlow<List<DayPlanEntity>> = db.dayPlanDao().observeAll()
        .guardDatabase(emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按日期的一次性闹钟（课表/排班表按日期导入）。 */
    val dateAlarms: StateFlow<List<DateAlarmEntity>> = db.dateAlarmDao().observeAll()
        .guardDatabase(emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val snoozes: StateFlow<List<SnoozeEntity>> = db.snoozeDao().observeAll()
        .guardDatabase(emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val holidays: StateFlow<List<HolidayEntity>> = db.holidayDao().observeAll()
        .guardDatabase(emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettingsEntity?> = db.settingsDao().observe()
        .guardDatabase<AppSettingsEntity?>(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val cycleLength: StateFlow<Int> = settings.map { it?.cycleLength ?: 6 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 6)

    init {
        viewModelScope.launch {
            runCatching {
                ensureSeeded()
                ensurePlansSeeded()
            }.onFailure { throwable ->
                handleDatabaseFailure(throwable)
            }
        }
    }

    fun clearDatabaseError() {
        _databaseError.value = null
    }

    private fun <T> Flow<T>.guardDatabase(fallback: T): Flow<T> =
        catch { throwable ->
            handleDatabaseFailure(throwable)
            emit(fallback)
        }

    private fun handleDatabaseFailure(throwable: Throwable) {
        Log.e(TAG, "database access failed", throwable)
        _databaseError.value = if (
            throwable is IllegalStateException &&
            throwable.message?.contains("migration", ignoreCase = true) == true
        ) {
            "数据库版本过旧，请卸载重装"
        } else {
            "数据库初始化失败，请重启应用"
        }
    }

    /** 首次启动只建 settings（闹钟改由「循环安排」生成）。 */
    private suspend fun ensureSeeded() {
        if (db.settingsDao().get() != null) return
        val today = LocalDate.now().toEpochDay()
        db.settingsDao().upsert(
            AppSettingsEntity(id = 1, cycleLength = 6, cycleStartEpochDay = today)
        )
        // 首次启动自动导入还没过去的法定节假日（当前只有 2026 中秋/国庆等）
        importChinaHolidaysInternal()
        reschedule()
    }

    // ---------------------------------------------------------------------
    // 循环安排：一次性 seed
    // ---------------------------------------------------------------------

    /**
     * 安排功能的一次性 seed（幂等）：
     * 「清空旧闹钟再建默认计划」是被废弃的旧行为：v5→v6 迁移后 plansSeeded=false，
     * 旧做法会让每个升级用户首次启动时丢失已有周期闹钟。现在只在 alarms 与 day_plans
     * 同时为空时创建默认计划；任何既有闹钟都必须保留，由 [DayPlanSync] 决定后续接管。
     *
     * plansSeeded 在函数结尾统一置 true，重入不会再次触发默认计划。
     */
    private suspend fun ensurePlansSeeded() {
        val settings = db.settingsDao().get() ?: return

        // 类型表为空则补默认类型；这不表示要重建用户的闹钟或安排。
        if (db.activityTypeDao().count() == 0) insertDefaultTypes()

        val freshInstall = !InstallMarker.hasMarker(getApplication())

        if (!settings.plansSeeded) {
            val alarmCount = db.alarmDao().getAll().size
            val dayPlanCount = db.dayPlanDao().getAll().size
            // 只有「从未安装过（无标记）」且两张表都为空时才注入默认计划；
            // 老用户把闹钟/安排删光也不会被当成新装再注入一遍。
            if (freshInstall && PlanSeedPolicy.shouldCreateDefaultPlans(alarmCount, dayPlanCount)) {
                // 只有全新安装 / 两张表都为空时才建默认计划：第 1..N-1 天上课，第 N 天休息
                val types = db.activityTypeDao().getAll()
                val classType = types.firstOrNull { it.name == "上课" }
                    ?: types.firstOrNull { it.rings }
                val restType = types.firstOrNull { !it.rings }
                // 注入上限 6 天：30 天周期不该一次塞 29 条默认闹钟
                val length = settings.cycleLength.coerceAtLeast(1).coerceAtMost(MAX_SEED_DAYS)
                if (classType != null && length <= 1) {
                    db.dayPlanDao().upsert(
                        DayPlanEntity(
                            dayIndex = 0,
                            typeId = classType.id,
                            hour = classType.hour,
                            minute = classType.minute,
                            enabled = true
                        )
                    )
                } else if (classType != null) {
                    for (day in 0 until length - 1) {
                        db.dayPlanDao().upsert(
                            DayPlanEntity(
                                dayIndex = day,
                                typeId = classType.id,
                                hour = classType.hour,
                                minute = classType.minute,
                                enabled = true
                            )
                        )
                    }
                    if (restType != null) {
                        db.dayPlanDao().upsert(
                            DayPlanEntity(
                                dayIndex = length - 1,
                                typeId = restType.id,
                                hour = restType.hour,
                                minute = restType.minute,
                                enabled = true
                            )
                        )
                    }
                }
            }
        }

        db.settingsDao().upsert(settings.copy(plansSeeded = true))
        // 注入成功后再写入新装标记：先标记再注入的话，一旦注入过程中异常，
        // 这台设备会永远失去「全新安装」身份，默认计划再也补不回来。
        InstallMarker.markInstalled(getApplication())
        syncPlans()
    }

    private suspend fun insertDefaultTypes() {
        listOf(
            ActivityTypeEntity(name = "上课", hour = 7, minute = 30, rings = true, sortOrder = 0),
            ActivityTypeEntity(name = "自习", hour = 8, minute = 30, rings = true, sortOrder = 1),
            ActivityTypeEntity(name = "休息", hour = 0, minute = 0, rings = false, sortOrder = 2)
        ).forEach { db.activityTypeDao().upsert(it) }
    }

    /** 跳过下一次响铃：只跳过这一条具体触发点，之后的照常。 */
    fun skipNextAlarm(next: NextAlarm) = viewModelScope.launch {
        val kind = when (next.kind) {
            NextAlarmKind.CYCLE -> AlarmScheduler.KIND_CYCLE
            NextAlarmKind.DATE -> AlarmScheduler.KIND_DATE
            NextAlarmKind.SNOOZE -> return@launch // 稍后提醒不参与「跳过下一次」
        }
        TemporaryMuteStore.skipNext(getApplication(), kind, next.alarmId, next.triggerAt)
        AlarmResyncHelper.resyncAll(getApplication(), auditMissed = false)
    }

    /** 今天临时静音：今天的闹钟全部跳过一次，明天恢复。 */
    fun muteToday() = viewModelScope.launch {
        val today = java.time.LocalDate.now().toEpochDay()
        TemporaryMuteStore.muteDay(getApplication(), today)
        AlarmResyncHelper.resyncAll(getApplication(), auditMissed = false)
    }

    fun unmuteToday() = viewModelScope.launch {
        val today = java.time.LocalDate.now().toEpochDay()
        TemporaryMuteStore.clearMutedDay(getApplication(), today)
        AlarmResyncHelper.resyncAll(getApplication(), auditMissed = false)
    }

    /** 安排 ↔ 闹钟 一致性同步（幂等，可在任何触发点调用）。 */
    private suspend fun syncPlans() {
        DayPlanAlarmSync.syncFromDatabase(getApplication())
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        // 计划生成的闹钟不在 UI 可编辑列表里；这里只处理手动闹钟，不再做 detach
        db.alarmDao().upsert(alarm.copy(enabled = enabled))
        syncPlans()
    }

    fun saveAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        db.alarmDao().upsert(alarm)
        syncPlans()
    }

    /** 为某一天添加一条新的闹钟（默认 7:00）。 */
    fun addAlarmToDay(dayIndex: Int) = viewModelScope.launch {
        db.alarmDao().upsert(
            AlarmEntity(dayIndex = dayIndex, hour = 7, minute = 0, enabled = true)
        )
        syncPlans()
    }

    /** 删除单条闹钟：取消排期并删除，不影响周期天数。 */
    fun deleteAlarm(id: Int) = viewModelScope.launch {
        val alarm = db.alarmDao().getById(id) ?: return@launch
        AlarmScheduler.cancelAlarm(getApplication(), alarm)
        db.alarmDao().deleteById(id)
        syncPlans()
    }

    fun restoreAlarm(snapshot: AlarmEntity) = viewModelScope.launch {
        db.alarmDao().upsert(DeleteUndoPolicy.alarmForRestore(snapshot))
        syncPlans()
    }

    /**
     * 应用循环设置：
     * - DAILY：固定 1 天，每天重复；
     * - WEEKLY：固定 7 天，第 1 天 = 本周一；
     * - CUSTOM：N 天一轮（1..30），今天对齐到第 alignDay 天。
     * 缩短天数时会删除末尾天的全部闹钟；周期一旦确定，只能在第 1..N 天里添加闹钟。
     */
    fun applyCycleSettings(mode: String, length: Int, alignDay: Int) = viewModelScope.launch {
        val settings = db.settingsDao().get() ?: return@launch
        val target = when (mode) {
            CycleTypes.DAILY -> 1
            CycleTypes.WEEKLY -> 7
            else -> length.coerceIn(1, 30)
        }
        if (target < settings.cycleLength) {
            db.alarmDao().getAll()
                .filter { it.dayIndex >= target }
                .forEach { AlarmScheduler.cancelAlarm(getApplication(), it) }
            db.alarmDao().deleteFromIndex(target)
            // 周期变短：超出新长度的安排一并删除
            db.dayPlanDao().deleteFromIndex(target)
        }
        val today = LocalDate.now()
        val anchor = when (mode) {
            CycleTypes.WEEKLY -> today.toEpochDay() - (today.dayOfWeek.value - 1)
            CycleTypes.DAILY -> today.toEpochDay()
            else -> today.toEpochDay() - (alignDay - 1)
        }
        db.settingsDao().upsert(
            settings.copy(
                cycleType = mode,
                cycleLength = target,
                cycleStartEpochDay = anchor
            )
        )
        syncPlans()
    }

    // ---------------------------------------------------------------------
    // 循环安排：每日安排与安排类型
    // ---------------------------------------------------------------------

    /** 给某天指定安排（时间取该类型的默认值）。 */
    fun setDayPlan(dayIndex: Int, typeId: Int) = viewModelScope.launch {
        val type = db.activityTypeDao().getById(typeId) ?: return@launch
        db.dayPlanDao().upsert(
            DayPlanEntity(
                dayIndex = dayIndex,
                typeId = type.id,
                hour = type.hour,
                minute = type.minute,
                enabled = true
            )
        )
        syncPlans()
    }

    /** 该天改为「未安排」。 */
    /** 临时停用/启用某天的安排（保留安排记录，不生成闹钟）。 */
    fun setPlanEnabled(dayIndex: Int, enabled: Boolean) = viewModelScope.launch {
        val plan = db.dayPlanDao().getByDay(dayIndex) ?: return@launch
        db.dayPlanDao().upsert(plan.copy(enabled = enabled))
        syncPlans()
    }

    /** 单日调整安排时间（只影响这一天，其他同类型日不变）。 */
    fun updatePlanTime(dayIndex: Int, hour: Int, minute: Int) = viewModelScope.launch {
        val plan = db.dayPlanDao().getByDay(dayIndex) ?: return@launch
        db.dayPlanDao().upsert(plan.copy(hour = hour, minute = minute))
        syncPlans()
    }

    /** 新增安排类型。 */
    fun addActivityType(name: String, hour: Int, minute: Int, rings: Boolean) = viewModelScope.launch {
        val sortOrder = (db.activityTypeDao().getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        db.activityTypeDao().upsert(
            ActivityTypeEntity(
                name = name.trim(),
                hour = hour,
                minute = minute,
                rings = rings,
                sortOrder = sortOrder
            )
        )
        syncPlans()
    }

    /**
     * 更新安排类型。
     * [applyToExisting] = true 时，把这类型当前使用的所有安排的时间一起改成新默认值；
     * false 则只改类型默认值，已安排的日子保持原时间。
     */
    fun updateActivityType(type: ActivityTypeEntity, applyToExisting: Boolean) = viewModelScope.launch {
        db.activityTypeDao().upsert(type)
        if (applyToExisting) {
            db.dayPlanDao().getAll()
                .filter { it.typeId == type.id }
                .forEach { plan ->
                    db.dayPlanDao().upsert(plan.copy(hour = type.hour, minute = type.minute))
                }
        }
        syncPlans()
    }

    /** 待撤销的删除操作。放在 ViewModel 里，转屏/页面重建都不会丢。 */
    sealed interface PendingUndo {
        data class Plan(val plan: DayPlanEntity, val message: String) : PendingUndo
        data class Type(
            val snapshot: ActivityTypeDeleteSnapshot,
            val message: String
        ) : PendingUndo
    }

    private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)
    val pendingUndo: StateFlow<PendingUndo?> = _pendingUndo

    /** 删除某天的安排并登记待撤销项。 */
    suspend fun deleteDayPlanWithUndo(dayIndex: Int, label: String) {
        val removed = deleteDayPlan(dayIndex) ?: return
        _pendingUndo.value = PendingUndo.Plan(removed, "已删除${label}的安排")
    }

    /** 删除安排类型并登记待撤销项。 */
    suspend fun deleteActivityTypeWithUndo(id: Int) {
        val snapshot = deleteActivityTypeWithSnapshot(id) ?: return
        _pendingUndo.value = PendingUndo.Type(snapshot, "已删除安排类型「${snapshot.type.name}」")
    }

    fun undoPending() {
        when (val pending = _pendingUndo.value) {
            is PendingUndo.Plan -> restoreDayPlan(pending.plan)
            is PendingUndo.Type -> restoreActivityType(pending.snapshot)
            null -> return
        }
        _pendingUndo.value = null
    }

    fun clearPendingUndo() {
        _pendingUndo.value = null
    }

    /** 删除某天的安排，并返回被删除的行（供撤销恢复）。 */
    suspend fun deleteDayPlan(dayIndex: Int): DayPlanEntity? {
        val existing = db.dayPlanDao().getByDay(dayIndex) ?: return null
        db.dayPlanDao().deleteByDay(dayIndex)
        syncPlans()
        return existing
    }

    /** 撤销「删除安排」：把原行写回并重排。 */
    fun restoreDayPlan(plan: DayPlanEntity) = viewModelScope.launch {
        db.dayPlanDao().upsert(plan)
        syncPlans()
    }

    /** 删除安排类型前的快照：类型行 + 被连带删除的 day_plan 行。 */
    data class ActivityTypeDeleteSnapshot(
        val type: ActivityTypeEntity,
        val plans: List<DayPlanEntity>
    )

    /** 删除安排类型并返回快照（供撤销恢复）。 */
    suspend fun deleteActivityTypeWithSnapshot(id: Int): ActivityTypeDeleteSnapshot? {
        val type = db.activityTypeDao().getById(id) ?: return null
        val plans = PlanDeletePolicy.plansUsingType(db.dayPlanDao().getAll(), id)
        db.dayPlanDao().deleteByType(id)
        db.activityTypeDao().deleteById(id)
        syncPlans()
        return ActivityTypeDeleteSnapshot(type, plans)
    }

    /** 撤销「删除安排类型」：恢复类型与被连带删除的安排，并重排。 */
    fun restoreActivityType(snapshot: ActivityTypeDeleteSnapshot) = viewModelScope.launch {
        db.activityTypeDao().upsert(snapshot.type)
        snapshot.plans.forEach { db.dayPlanDao().upsert(it) }
        syncPlans()
    }

    /** 类型排序：上移 / 下移。 */
    fun moveActivityType(id: Int, up: Boolean) = viewModelScope.launch {
        val list = db.activityTypeDao().getAll()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@launch
        val target = if (up) index - 1 else index + 1
        if (target !in list.indices) return@launch
        val reordered = list.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { position, type ->
            db.activityTypeDao().upsert(type.copy(sortOrder = position))
        }
    }

    /**
     * 假期模式总开关：
     * - 开启：假期段内的“假期静音”闹钟自动静默；
     * - 关闭：所有假期段不生效，但保留每条闹钟自己的“假期静音”标记。
     */
    fun setHolidayMode(enabled: Boolean) = viewModelScope.launch {
        val settings = db.settingsDao().get() ?: return@launch
        db.settingsDao().upsert(settings.copy(holidayModeEnabled = enabled))
        reschedule()
    }

    /** 一键导入中国法定节假日：自动跳过已经过去的假期，避免历史年份污染列表。 */
    fun importChinaHolidays() = viewModelScope.launch {
        importChinaHolidaysInternal()
        reschedule()
    }

    private suspend fun importChinaHolidaysInternal() {
        val existing = db.holidayDao().getAll().associateBy { it.label }
        ChinaHolidaysProvider.upcomingOrActive().forEach { official ->
            val old = existing[official.label]
            db.holidayDao().upsert(
                if (old == null) {
                    official
                } else {
                    // 已存在同名假期：只更新日期，保留用户自己的启用状态
                    old.copy(
                        startEpochDay = official.startEpochDay,
                        endEpochDay = official.endEpochDay
                    )
                }
            )
        }
    }

    /** 新增（id=0）或编辑一段假期；开始晚于结束时自动交换。 */
    fun saveHoliday(holiday: HolidayEntity) = viewModelScope.launch {
        val s = holiday.startEpochDay
        val e = holiday.endEpochDay
        val (ss, ee) = if (s <= e) s to e else e to s
        db.holidayDao().upsert(holiday.copy(startEpochDay = ss, endEpochDay = ee))
        reschedule()
    }

    /** 启用/停用某一段假期。 */
    fun setHolidayEnabled(id: Int, enabled: Boolean) = viewModelScope.launch {
        val h = db.holidayDao().getById(id) ?: return@launch
        db.holidayDao().upsert(h.copy(enabled = enabled))
        reschedule()
    }

    /** 删除一段假期。 */
    fun deleteHoliday(id: Int) = viewModelScope.launch {
        db.holidayDao().deleteById(id)
        reschedule()
    }

    fun restoreHoliday(snapshot: HolidayEntity) = viewModelScope.launch {
        db.holidayDao().upsert(DeleteUndoPolicy.holidayForRestore(snapshot))
        reschedule()
    }

    /** 批量设置所有闹钟的“假期静音”开关。 */
    fun setAllAlarmsMutedDuringHoliday(muted: Boolean) = viewModelScope.launch {
        db.alarmDao().setAllMutedDuringHoliday(muted)
        reschedule()
    }

    // ---------------------------------------------------------------------
    // 按日期闹钟（一次性）
    // ---------------------------------------------------------------------

    fun setDateAlarmEnabled(alarm: DateAlarmEntity, enabled: Boolean) = viewModelScope.launch {
        db.dateAlarmDao().upsert(alarm.copy(enabled = enabled))
        reschedule()
    }

    fun saveDateAlarm(alarm: DateAlarmEntity) = viewModelScope.launch {
        db.dateAlarmDao().upsert(alarm)
        reschedule()
    }

    fun deleteDateAlarm(id: Int) = viewModelScope.launch {
        db.dateAlarmDao().getById(id)?.let { AlarmScheduler.cancelDateAlarm(getApplication(), it) }
        db.dateAlarmDao().deleteById(id)
        reschedule()
    }

    fun restoreDateAlarm(snapshot: DateAlarmEntity) = viewModelScope.launch {
        db.dateAlarmDao().upsert(DeleteUndoPolicy.dateAlarmForRestore(snapshot))
        reschedule()
    }

    private suspend fun reschedule() {
        AlarmScheduler.rescheduleAll(
            getApplication(),
            db.alarmDao().getAll(),
            db.dateAlarmDao().getAll(),
            db.settingsDao().get(),
            db.holidayDao().getAll()
        )
    }

    private companion object {
        const val TAG = "AlarmViewModel"

        /** 默认计划注入的最大天数（防止长周期一次注入过多闹钟）。 */
        const val MAX_SEED_DAYS = 6
    }
}
