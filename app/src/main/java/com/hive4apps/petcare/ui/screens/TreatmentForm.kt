package com.hive4apps.petcare.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.CareKind
import com.hive4apps.petcare.core.HH_MM
import com.hive4apps.petcare.core.defaultTimes
import com.hive4apps.petcare.core.parseTime
import com.hive4apps.petcare.ui.Draft
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreatmentForm(draft: Draft, onChange: (Draft) -> Unit, modifier: Modifier = Modifier) {
    val check = MaterialTheme.colorScheme.tertiary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CareKind.entries.forEach { kind ->
                FilterChip(
                    selected = draft.kind == kind,
                    onClick = { onChange(draft.copy(kind = kind)) },
                    label = { Text(stringResource(kindLabel(kind))) },
                    leadingIcon = { Icon(kindIcon(kind), null, Modifier.size(18.dp)) },
                )
            }
        }

        OutlinedTextField(
            value = draft.dose,
            onValueChange = { onChange(draft.copy(dose = it, guessed = draft.guessed - Draft.Field.DOSE)) },
            label = { Text(stringResource(R.string.field_dose)) },
            placeholder = { Text(stringResource(R.string.field_dose_hint)) },
            supportingText = if (Draft.Field.DOSE in draft.guessed) {
                { Text(stringResource(R.string.to_check_not_found), color = check) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- When ----
        Text(stringResource(R.string.field_when), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (Draft.Field.FREQUENCY in draft.guessed) {
            Text(stringResource(R.string.to_check_frequency), color = check, style = MaterialTheme.typography.bodySmall)
        }
        Stepper(
            label = pluralStringResource(R.plurals.times_per_day, draft.times.size, draft.times.size),
            onMinus = if (draft.times.size > 1) {
                { onChange(draft.copy(times = defaultTimes(draft.times.size - 1), guessed = draft.guessed - Draft.Field.FREQUENCY)) }
            } else {
                null
            },
            onPlus = if (draft.times.size < 6) {
                { onChange(draft.copy(times = defaultTimes(draft.times.size + 1), guessed = draft.guessed - Draft.Field.FREQUENCY)) }
            } else {
                null
            },
        )
        var editingTime by remember { mutableStateOf<Int?>(null) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.times.sorted().forEachIndexed { index, time ->
                AssistChip(
                    onClick = { editingTime = index },
                    label = { Text(time) },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                )
            }
        }
        editingTime?.let { index ->
            val sorted = draft.times.sorted()
            TimeDialog(
                initial = parseTime(sorted[index]) ?: LocalTime.of(8, 0),
                onDismiss = { editingTime = null },
                onPick = { picked ->
                    val newTimes = sorted.toMutableList().also { it[index] = picked.format(HH_MM) }.distinct().sorted()
                    onChange(draft.copy(times = newTimes, startTime = null).startingFrom(LocalDateTime.now()))
                    editingTime = null
                },
            )
        }
        Stepper(
            label = if (draft.everyDays <= 1) {
                stringResource(R.string.every_day)
            } else {
                pluralStringResource(R.plurals.every_n_days, draft.everyDays, draft.everyDays)
            },
            onMinus = if (draft.everyDays > 1) {
                { onChange(draft.copy(everyDays = draft.everyDays - 1, guessed = draft.guessed - Draft.Field.FREQUENCY)) }
            } else {
                null
            },
            onPlus = { onChange(draft.copy(everyDays = draft.everyDays + 1, guessed = draft.guessed - Draft.Field.FREQUENCY)) },
        )

        // ---- How long ----
        Text(stringResource(R.string.field_duration), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (Draft.Field.DURATION in draft.guessed) {
            Text(stringResource(R.string.to_check_duration), color = check, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.continuous_switch), Modifier.weight(1f))
            Switch(
                checked = draft.continuous,
                onCheckedChange = { onChange(draft.copy(continuous = it, guessed = draft.guessed - Draft.Field.DURATION)) },
            )
        }
        if (!draft.continuous) {
            Stepper(
                label = pluralStringResource(R.plurals.during_days, draft.durationDays, draft.durationDays),
                onMinus = if (draft.durationDays > 1) {
                    { onChange(draft.copy(durationDays = draft.durationDays - 1, guessed = draft.guessed - Draft.Field.DURATION)) }
                } else {
                    null
                },
                onPlus = { onChange(draft.copy(durationDays = draft.durationDays + 1, guessed = draft.guessed - Draft.Field.DURATION)) },
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 5, 7, 10, 14, 21, 28, 30, 60, 90).forEach { days ->
                    FilterChip(
                        selected = draft.durationDays == days,
                        onClick = { onChange(draft.copy(durationDays = days, guessed = draft.guessed - Draft.Field.DURATION)) },
                        label = { Text(pluralStringResource(R.plurals.days_short, days, days)) },
                    )
                }
            }
        }

        // ---- Start ----
        Text(stringResource(R.string.field_start), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        var pickingDate by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { pickingDate = true }) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(longDate(draft.startDate))
        }
        if (pickingDate) {
            DateDialog(
                initial = draft.startDate,
                onDismiss = { pickingDate = false },
                onPick = {
                    onChange(draft.copy(startDate = it, startTime = null).startingFrom(LocalDateTime.now()))
                    pickingDate = false
                },
            )
        }
        val sorted = draft.times.sorted()
        if (sorted.size > 1) {
            Text(stringResource(R.string.first_dose), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sorted.forEach { time ->
                    val selected = (draft.startTime ?: sorted.first()) == time
                    FilterChip(
                        selected = selected,
                        onClick = { onChange(draft.copy(startTime = time)) },
                        label = { Text(time) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.notes,
            onValueChange = { onChange(draft.copy(notes = it)) },
            label = { Text(stringResource(R.string.field_notes)) },
            placeholder = { Text(stringResource(R.string.field_notes_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun Stepper(label: String, onMinus: (() -> Unit)?, onPlus: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = { onMinus?.invoke() }, enabled = onMinus != null) {
            Icon(Icons.Filled.Remove, contentDescription = "-")
        }
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onPlus?.invoke() }, enabled = onPlus != null) {
            Icon(Icons.Filled.Add, contentDescription = "+")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: java.time.LocalDate, onDismiss: () -> Unit, onPick: (java.time.LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state)
    }
}

/** A removable chip, e.g. a page of a document. */
@Composable
fun RemovableChip(text: String, onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(text) },
        trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)) },
    )
}
