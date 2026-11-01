package com.cyclealarm.app.ui

import android.content.Context
import com.cyclealarm.app.R
import com.cyclealarm.app.core.DisplayPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 周几对应的文案资源（ISO：1=周一）。 */
fun weekdayRes(isoDay: Int): Int = when (isoDay) {
    1 -> R.string.week_mon
    2 -> R.string.week_tue
    3 -> R.string.week_wed
    4 -> R.string.week_thu
    5 -> R.string.week_fri
    6 -> R.string.week_sat
    else -> R.string.week_sun
}

/** Material3 DatePicker 返回 UTC 零点的毫秒，需按 UTC 还原为日期。 */
fun datePickerMillisToEpochDay(millis: Long?): Long? =
    millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }

fun epochDayToDatePickerMillis(epochDay: Long?): Long? =
    epochDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

fun formatEpochDay(context: Context, epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val date = context.getString(R.string.date_month_day, d.monthValue, d.dayOfMonth)
    return date + " " + context.getString(weekdayRes(d.dayOfWeek.value))
}

fun formatTimeMillis(context: Context, millis: Long): String {
    val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val datePart = when (zdt.toLocalDate()) {
        today -> context.getString(R.string.countdown_today)
        today.plusDays(1) -> context.getString(R.string.countdown_tomorrow)
        else -> context.getString(R.string.date_month_day, zdt.monthValue, zdt.dayOfMonth)
    }
    val timePart = DisplayPrefs.formatTime(context, zdt.hour, zdt.minute)
    return "$datePart $timePart " + context.getString(weekdayRes(zdt.dayOfWeek.value))
}
