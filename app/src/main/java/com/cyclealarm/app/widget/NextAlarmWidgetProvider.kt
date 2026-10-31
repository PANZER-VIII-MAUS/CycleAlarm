package com.cyclealarm.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 「下次响铃」桌面小组件。
 *
 * 刷新时机：
 * 1) 系统按 updatePeriodMillis（30 分钟）回调 onUpdate；
 * 2) App 内每次重排排期后主动调用 [WidgetUpdater.updateAll]。
 */
class NextAlarmWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(context.applicationContext, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        // 首次添加：立即按当前数据渲染一次
        super.onEnabled(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
