package com.cyclealarm.app.ui.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclealarm.app.core.RingHistoryFormatter
import com.cyclealarm.app.core.RingHistoryStatus
import com.cyclealarm.app.data.RingEventEntity
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.theme.CycleAlarmTheme
import com.cyclealarm.app.ui.theme.ThemeManager

/** 响铃历史：最近 7 天的响铃 / 漏响 / 未处理。 */
class RingHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyStartupTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CycleAlarmTheme {
                RingHistoryScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingHistoryScreen(
    onBack: () -> Unit,
    viewModel: RingHistoryViewModel = viewModel()
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("响铃历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "最近 7 天：已处理 ${summary.acked} · 未处理 ${summary.unhandled} · 漏响 ${summary.missed}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (events.isEmpty()) {
                Text(
                    "暂无记录（响铃、漏响会记录在这里）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events, key = { it.id }) { event ->
                        RingHistoryRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun RingHistoryRow(event: RingEventEntity) {
    val status = RingHistoryFormatter.statusOf(event)
    CycleCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Text(
            text = RingHistoryFormatter.timeText(event.expectedAt) +
                " · " + event.label.ifBlank { "闹钟" },
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = RingHistoryFormatter.label(status),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
