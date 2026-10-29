package com.cyclealarm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import com.cyclealarm.app.data.AppDatabase
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.data.SnoozeEntity
import com.cyclealarm.app.MainActivity
import com.cyclealarm.app.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 闹钟排期核心：同时管理「周期闹钟」与「按日期的一次性闹钟」。
 *
 * 关键设计：
 * 1. 每条闹钟用自己的数据库行 id 作为 PendingIntent requestCode，保证唯一、互不覆盖；
 *    按日期闹钟使用 [DATE_REQUEST_BASE] + id 的独立命名空间，避免与周期闹钟 id 撞车。
 * 2. 使用 setAlarmClock()，系统按最高优先级处理，并在状态栏显示闹钟图标。
 * 3. 假期模式下，被标记“假期静音”的闹钟会被跳过一次；周期闹钟会顺延到假期后的下一个匹配日，
 *    按日期闹钟则直接跳过当天（一次性语义），因此假期结束会自动恢复，无需额外定时任务。
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    const val ACTION_FIRE = "com.cyclealarm.app.action.FIRE"
    const val ACTION_STOP_RING = "com.cyclealarm.app.action.STOP_RING"

    /** 兜底通知里的「立即重排」action。 */
    const val ACTION_RESYNC = "com.cyclealarm.app.action.RESYNC"

    const val EXTRA_ALARM_ID = "alarmId"
    const val EXTRA_ALARM_KIND = "alarmKind"
    const val EXTRA_DAY_INDEX = "dayIndex"
    const val EXTRA_HOUR = "hour"
    const val EXTRA_MINUTE = "minute"
    const val EXTRA_LABEL = "label"
    const val EXTRA_SNOOZED = "snoozed"
    const val EXTRA_SNOOZE_ID = "snoozeId"
    const val EXTRA_ORIGINAL_HOUR = "originalHour"
    const val EXTRA_ORIGINAL_MINUTE = "originalMinute"

    /** 试听（预览）标记：禁止 snooze 与任何数据库写入。 */
    const val EXTRA_PREVIEW = "preview"

    /** 周期闹钟（第 1..N 天 / 周一..周日）。 */
    const val KIND_CYCLE = "cycle"

    /** 按具体日期的一次性闹钟。 */
    const val KIND_DATE = "date"

    /** 按日期闹钟的 requestCode 偏移，避免与周期闹钟 id 冲突。 */
    const val DATE_REQUEST_BASE = 1_000_000

    private const val SNOOZE_REQUEST_OFFSET = 100_000

    /** 全屏响铃 Activity 的独立 requestCode 偏移，避免与 showIntent / snooze 槽位混用。 */
    const val FULLSCREEN_REQUEST_OFFSET = 200_000

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 把 (类型, 数据库 id) 换算成全局唯一的调度 id。 */
    fun schedulerId(kind: String, alarmId: Int): Int =
        if (kind == KIND_DATE) DATE_REQUEST_BASE + alarmId else alarmId

    // ---------------------------------------------------------------------
    // 周期闹钟（原有逻辑保持不变）
    // ---------------------------------------------------------------------

    /** 计算某条周期闹钟的下一次触发时间；disabled 或无法找到返回 null。 */
    fun nextTriggerFor(
        alarm: AlarmEntity,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (!alarm.enabled) return null
        val cycleLen = (settings?.cycleLength ?: 6).coerceAtLeast(1)
        val anchor = settings?.cycleStartEpochDay ?: LocalDate.now().toEpochDay()
        val holidayMode = settings?.holidayModeEnabled ?: true
        val zone = ZoneId.systemDefault()
        // 注意：LocalDate.ofInstant 是 Java 9 新增，Android 直到 API 36 才有，
        // 用 atZone(...).toLocalDate() 保证 API 26+ 可用
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

        for (offset in 0L until 4000L) {
            val candidateEpochDay = today + offset
            val dayIndex = Math.floorMod(candidateEpochDay - anchor, cycleLen.toLong())
            if (dayIndex != alarm.dayIndex.toLong()) continue
            if (isMutedOn(alarm, holidays, candidateEpochDay, holidayMode)) continue
            val millis = LocalDate.ofEpochDay(candidateEpochDay)
                .atTime(alarm.hour, alarm.minute)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            if (millis > now) return millis
        }
        return null
    }

    /**
     * 判断某天该周期闹钟是否被静音：
     * 需要假期模式总开关打开、这条闹钟勾选了“假期静音”、
     * 且当天落在任意一段已启用假期（含调休连休）内。
     */
    fun isMutedOn(
        alarm: AlarmEntity,
        holidays: List<HolidayEntity>,
        epochDay: Long,
        holidayModeEnabled: Boolean
    ): Boolean {
        val inHolidayRange = holidays.any {
            it.enabled && epochDay in it.startEpochDay..it.endEpochDay
        }
        return HolidayMutePolicy.shouldMute(
            holidayModeEnabled,
            alarm.mutedDuringHoliday,
            inHolidayRange
        )
    }

    /** 用 setAlarmClock() 设置周期闹钟；无下一次触发则取消。 */
    fun scheduleAlarm(
        context: Context,
        alarm: AlarmEntity,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerFor(alarm, settings, holidays)
        if (triggerAt == null) {
            cancelAlarm(context, alarm)
            return
        }
        val requestCode = alarm.id
        val operation = firePendingIntent(
            context = context,
            requestCode = requestCode,
            kind = KIND_CYCLE,
            alarmId = alarm.id,
            dayIndex = alarm.dayIndex,
            hour = alarm.hour,
            minute = alarm.minute,
            label = alarm.label,
            snoozed = false
        )
        setAlarmClock(am, triggerAt, operation, showPendingIntent(context, requestCode))
    }

    /** 取消某条周期闹钟（含未触发的“稍后提醒”）。 */
    fun cancelAlarm(context: Context, alarm: AlarmEntity) {
        cancelBySchedulerId(context, alarm.id)
        clearSnoozeForAlarm(context, KIND_CYCLE, alarm.id)
    }

    // ---------------------------------------------------------------------
    // 按日期的一次性闹钟
    // ---------------------------------------------------------------------

    /** 计算某条按日期闹钟的下一次（也是唯一一次）触发时间；已过或静音返回 null。 */
    fun nextTriggerForDateAlarm(
        alarm: DateAlarmEntity,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (!alarm.enabled) return null
        val holidayMode = settings?.holidayModeEnabled ?: true
        if (isMutedOnDate(alarm, holidays, alarm.epochDay, holidayMode)) return null
        val millis = LocalDate.ofEpochDay(alarm.epochDay)
            .atTime(alarm.hour, alarm.minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return if (millis > now) millis else null
    }

    /** 某条按日期闹钟是否因假期被静音（一次性语义：静音即跳过当天）。 */
    fun isMutedOnDate(
        alarm: DateAlarmEntity,
        holidays: List<HolidayEntity>,
        epochDay: Long,
        holidayModeEnabled: Boolean
    ): Boolean {
        val inHolidayRange = holidays.any {
            it.enabled && epochDay in it.startEpochDay..it.endEpochDay
        }
        return HolidayMutePolicy.shouldMute(
            holidayModeEnabled,
            alarm.mutedDuringHoliday,
            inHolidayRange
        )
    }

    /** 排一条按日期的一次性闹钟；已过/静音则取消。 */
    fun scheduleDateAlarm(
        context: Context,
        alarm: DateAlarmEntity,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerForDateAlarm(alarm, settings, holidays)
        if (triggerAt == null) {
            cancelDateAlarm(context, alarm)
            return
        }
        val requestCode = schedulerId(KIND_DATE, alarm.id)
        val operation = firePendingIntent(
            context = context,
            requestCode = requestCode,
            kind = KIND_DATE,
            alarmId = alarm.id,
            dayIndex = 0,
            hour = alarm.hour,
            minute = alarm.minute,
            label = alarm.label,
            snoozed = false
        )
        setAlarmClock(am, triggerAt, operation, showPendingIntent(context, requestCode))
    }

    /** 取消某条按日期闹钟（含未触发的“稍后提醒”）。 */
    fun cancelDateAlarm(context: Context, alarm: DateAlarmEntity) {
        cancelBySchedulerId(context, schedulerId(KIND_DATE, alarm.id))
        clearSnoozeForAlarm(context, KIND_DATE, alarm.id)
    }

    // ---------------------------------------------------------------------
    // 全量重建
    // ---------------------------------------------------------------------

    /** 全量重建排期：周期闹钟 + 按日期闹钟。 */
    fun rescheduleAll(
        context: Context,
        alarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ) {
        alarms.forEach { alarm ->
            runCatching { scheduleAlarm(context, alarm, settings, holidays) }
                .onFailure { Log.e(TAG, "resync alarm failed id=${alarm.id}", it) }
        }
        dateAlarms.forEach { alarm ->
            runCatching { scheduleDateAlarm(context, alarm, settings, holidays) }
                .onFailure { Log.e(TAG, "resync date alarm failed id=${alarm.id}", it) }
        }
    }

    /** 兼容旧调用：只重建周期闹钟。 */
    fun rescheduleAll(
        context: Context,
        alarms: List<AlarmEntity>,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ) = rescheduleAll(context, alarms, emptyList(), settings, holidays)

    // ---------------------------------------------------------------------
    // 稍后提醒
    // ---------------------------------------------------------------------

    /** 稍后提醒（周期闹钟）。 */
    suspend fun snooze(context: Context, alarm: AlarmEntity, minutes: Int = 10): Boolean =
        snoozeAt(
            context = context,
            kind = KIND_CYCLE,
            alarmId = alarm.id,
            dayIndex = alarm.dayIndex,
            hour = alarm.hour,
            minute = alarm.minute,
            label = alarm.label,
            minutes = minutes
        )

    /**
     * 稍后提醒（通用）：响铃页对周期/按日期闹钟都走这里。
     * kind 决定 requestCode 命名空间，避免两类闹钟互相覆盖。
     */
    suspend fun snoozeAt(
        context: Context,
        kind: String,
        alarmId: Int,
        dayIndex: Int,
        hour: Int,
        minute: Int,
        label: String,
        minutes: Int = 10,
        originalHour: Int = hour,
        originalMinute: Int = minute
    ): Boolean {
        if (!RingLaunchValidator.hasValidAlarmId(alarmId) || minutes <= 0) return false
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val target = SnoozeTimePolicy.target(now, minutes)
        val requestCode = schedulerId(kind, alarmId) + SNOOZE_REQUEST_OFFSET
        val snooze = runCatching {
            db.withTransaction {
                db.snoozeDao().deleteByAlarm(kind, alarmId.toLong())
                val entity = SnoozeEntity(
                    kind = kind,
                    alarmId = alarmId.toLong(),
                    dayIndex = dayIndex,
                    hour = originalHour,
                    minute = originalMinute,
                    label = label,
                    triggerAt = target.triggerAt,
                    requestCode = requestCode
                )
                entity.copy(id = db.snoozeDao().insert(entity))
            }
        }.onFailure {
            Log.e(TAG, "persist snooze failed kind=$kind alarmId=$alarmId", it)
        }.getOrNull() ?: return false
        if (!rescheduleStoredSnooze(context, snooze)) {
            runCatching { db.snoozeDao().deleteById(snooze.id) }
                .onFailure { Log.e(TAG, "delete failed snooze row id=${snooze.id}", it) }
            return false
        }
        return true
    }

    /** 从持久化记录重建一条未到期的 snooze。 */
    fun rescheduleStoredSnooze(context: Context, snooze: SnoozeEntity): Boolean {
        if (!SnoozeTimePolicy.shouldRestore(snooze.triggerAt, System.currentTimeMillis())) {
            return false
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        val target = Instant.ofEpochMilli(snooze.triggerAt)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val operation = firePendingIntent(
            context = context,
            requestCode = snooze.requestCode,
            kind = snooze.kind,
            alarmId = snooze.alarmId.toInt(),
            dayIndex = snooze.dayIndex,
            hour = target.hour,
            minute = target.minute,
            label = snooze.label,
            snoozed = true,
            snoozeId = snooze.id,
            originalHour = snooze.hour,
            originalMinute = snooze.minute
        )
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, snooze.triggerAt, operation)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snooze.triggerAt, operation)
            }
            true
        }.onFailure {
            Log.e(TAG, "snooze failed kind=${snooze.kind} alarmId=${snooze.alarmId}", it)
        }.getOrDefault(false)
    }

    /** 清掉某条原闹钟残留的 snooze 记录；不阻塞调用线程。 */
    fun clearSnoozeForAlarm(context: Context, kind: String, alarmId: Int) {
        if (!RingLaunchValidator.hasValidAlarmId(alarmId)) return
        val appContext = context.applicationContext
        cleanupScope.launch {
            runCatching {
                AppDatabase.get(appContext)
                    .snoozeDao()
                    .deleteByAlarm(kind, alarmId.toLong())
            }.onFailure {
                Log.e(TAG, "clear snooze failed kind=$kind alarmId=$alarmId", it)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------

    private fun setAlarmClock(
        am: AlarmManager,
        triggerAt: Long,
        operation: PendingIntent,
        showIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // Android 12+ 未授予精确闹钟权限：先降级为不精确闹钟，避免启动崩溃；
                // 用户在权限引导中授权后，回到前台会自动切换回精确排期。
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
            }
        } catch (e: SecurityException) {
            // 兜底：仍失败时用不精确闹钟，绝不让排期异常导致应用崩溃
            runCatching { am.set(AlarmManager.RTC_WAKEUP, triggerAt, operation) }
        }
    }

    private fun showPendingIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 按调度 id 取消主闹钟与稍后提醒两条 PendingIntent。 */
    private fun cancelBySchedulerId(context: Context, schedulerId: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        cancelByRequestCode(context, am, schedulerId)
        cancelByRequestCode(context, am, schedulerId + SNOOZE_REQUEST_OFFSET)
    }

    private fun cancelByRequestCode(context: Context, am: AlarmManager, requestCode: Int) {
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (operation != null) {
            am.cancel(operation)
            operation.cancel()
        }
    }

    private fun firePendingIntent(
        context: Context,
        requestCode: Int,
        kind: String,
        alarmId: Int,
        dayIndex: Int,
        hour: Int,
        minute: Int,
        label: String,
        snoozed: Boolean,
        snoozeId: Long? = null,
        originalHour: Int? = null,
        originalMinute: Int? = null
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_KIND, kind)
            .putExtra(EXTRA_DAY_INDEX, dayIndex)
            .putExtra(EXTRA_HOUR, hour)
            .putExtra(EXTRA_MINUTE, minute)
            .putExtra(EXTRA_LABEL, label)
        if (snoozed) intent.putExtra(EXTRA_SNOOZED, true)
        if (snoozeId != null) intent.putExtra(EXTRA_SNOOZE_ID, snoozeId)
        if (originalHour != null && originalMinute != null) {
            intent.putExtra(EXTRA_ORIGINAL_HOUR, originalHour)
            intent.putExtra(EXTRA_ORIGINAL_MINUTE, originalMinute)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
