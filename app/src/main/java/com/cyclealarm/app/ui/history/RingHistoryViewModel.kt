package com.cyclealarm.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyclealarm.app.core.RingHistoryFormatter
import com.cyclealarm.app.data.AppDatabase
import com.cyclealarm.app.data.RingEventEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 响铃历史：只读取最近 7 天的事件（数据库侧已按 7 天清理）。 */
class RingHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _events = MutableStateFlow<List<RingEventEntity>>(emptyList())
    val events: StateFlow<List<RingEventEntity>> = _events

    private val _summary = MutableStateFlow(RingHistoryFormatter.Summary(0, 0, 0))
    val summary: StateFlow<RingHistoryFormatter.Summary> = _summary

    init {
        viewModelScope.launch {
            val loaded = runCatching {
                AppDatabase.get(getApplication()).ringEventDao().recent(200)
            }.getOrDefault(emptyList())
            _events.value = loaded
            _summary.value = RingHistoryFormatter.summary(loaded)
        }
    }
}
