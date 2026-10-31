package com.cyclealarm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.cyclealarm.app.MainActivity
import com.cyclealarm.app.R
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.NextAlarmFinder
import com.cyclealarm.app.core.WidgetTextPolicy
import com.cyclealarm.app.data.AppDatabase

/** 桌面小组件的渲染与刷新（读取数据库 → NextAlarmFinder → RemoteViews）。 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, NextAlarmWidgetProvider::class.java))
        if (ids.isEmpty()) return
        update(context, ids)
    }

    suspend fun update(context: Context, appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val payload = runCatching { loadPayload(context) }
            .onFailure { Log.e(TAG, "load widget payload failed", it) }
            .getOrNull() ?: return

        appWidgetIds.forEach { widgetId ->
            runCatching {
                manager.updateAppWidget(widgetId, buildViews(context, payload))
            }.onFailure { Log.e(TAG, "update widget $widgetId failed", it) }
        }
    }

    private data class Payload(val text: WidgetTextPolicy.WidgetText, val is24Hour: Boolean)

    private suspend fun loadPayload(context: Context): Payload {
        val db = AppDatabase.get(context)
        val settings = db.settingsDao().get()
        val now = System.currentTimeMillis()
        val next = if (settings == null) {
            null
        } else {
            NextAlarmFinder.find(
                now = now,
                cycleAlarms = db.alarmDao().getAll(),
                dateAlarms = db.dateAlarmDao().getAll(),
                snoozes = db.snoozeDao().getAll(),
                settings = settings,
                holidays = db.holidayDao().getAll()
            )
        }
        val is24Hour = DisplayPrefs.is24Hour(context)
        return Payload(WidgetTextPolicy.render(next, now, is24Hour), is24Hour)
    }

    private fun buildViews(context: Context, payload: Payload): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_next_alarm).apply {
            setTextViewText(R.id.widget_time, payload.text.time)
            setTextViewText(R.id.widget_subtitle, payload.text.subtitle)
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
