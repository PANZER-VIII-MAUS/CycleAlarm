package com.cyclealarm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.R
import com.cyclealarm.app.data.HolidayEntity

@Composable
internal fun HolidayEditDialog(
    holiday: HolidayEntity,
    onDismiss: () -> Unit,
    onSave: (label: String, startEpochDay: Long, endEpochDay: Long) -> Unit
) {
    val context = LocalContext.current
    var label by rememberSaveable(holiday.id) { mutableStateOf(holiday.label) }
    var start by rememberSaveable(holiday.id) { mutableStateOf(holiday.startEpochDay) }
    var end by rememberSaveable(holiday.id) { mutableStateOf(holiday.endEpochDay) }
    var pickStart by rememberSaveable(holiday.id) { mutableStateOf(false) }
    var pickEnd by rememberSaveable(holiday.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
                    Text(if (holiday.id == 0) stringResource(R.string.holiday_add) else stringResource(R.string.holiday_edit))
                },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.holiday_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { pickStart = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.holiday_start, formatEpochDay(context, start)))
                }
                OutlinedButton(
                    onClick = { pickEnd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.holiday_end, formatEpochDay(context, end)))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(label.trim().ifBlank { context.getString(R.string.holiday_custom_label) }, start, end)
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (pickStart) {
        HolidayDateDialog(
            title = stringResource(R.string.pick_start_date),
            initialEpochDay = start,
            onConfirm = { start = it },
            onDismiss = { pickStart = false }
        )
    }
    if (pickEnd) {
        HolidayDateDialog(
            title = stringResource(R.string.pick_end_date),
            initialEpochDay = end,
            onConfirm = { end = it },
            onDismiss = { pickEnd = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HolidayDateDialog(
    title: String,
    initialEpochDay: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = epochDayToDatePickerMillis(initialEpochDay)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val day = datePickerMillisToEpochDay(state.selectedDateMillis)
                    if (day != null) onConfirm(day)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    ) {
        DatePicker(state = state, title = { Text(title) })
    }
}
