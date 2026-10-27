package com.cyclealarm.app.ui

import android.content.Context
import com.cyclealarm.app.core.DisplayPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val weekCn = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** Material3 DatePicker 返回 UTC 零点的毫秒，需按 UTC 还原为日期。 */
fun datePickerMillisToEpochDay(millis: Long?): Long? =
    millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }

fun epochDayToDatePickerMillis(epochDay: Long?): Long? =
    epochDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

fun formatEpochDay(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return "${d.monthValue}月${d.dayOfMonth}日 ${weekCn[d.dayOfWeek.value - 1]}"
}

fun formatTimeMillis(context: Context, millis: Long): String {
    val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val datePart = when (zdt.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${zdt.monthValue}月${zdt.dayOfMonth}日"
    }
    val timePart = DisplayPrefs.formatTime(context, zdt.hour, zdt.minute)
    return "$datePart $timePart ${weekCn[zdt.dayOfWeek.value - 1]}"
}
