package com.hive4apps.petcare.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.Dose
import com.hive4apps.petcare.core.HH_MM
import com.hive4apps.petcare.core.Pet
import com.hive4apps.petcare.core.Schedule
import com.hive4apps.petcare.ui.MainViewModel
import com.hive4apps.petcare.ui.theme.DoneGreen
import com.hive4apps.petcare.ui.theme.Late
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun TodayScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val data by vm.data.collectAsState()
    var dayOffset by rememberSaveable { mutableStateOf(0L) }
    val day = vm.today.plusDays(dayOffset)
    val doses = Schedule.dayPlan(data.treatments, day)
    val done = doses.count { it.key in data.done }
    val pets = data.pets.associateBy { it.id }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dayOffset-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (dayOffset) {
                            0L -> stringResource(R.string.today)
                            1L -> stringResource(R.string.tomorrow)
                            -1L -> stringResource(R.string.yesterday)
                            else -> longDate(day)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (dayOffset in -1L..1L) {
                        Text(longDate(day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { dayOffset++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
                }
            }
            if (dayOffset != 0L) {
                TextButton(onClick = { dayOffset = 0 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back_to_today)) }
            }
        }
        if (!vm.canNotify && data.treatments.isNotEmpty()) {
            item {
                Card(
                    onClick = onOpenSettings,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.notifications_off_banner), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        if (doses.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(
                            if (done == doses.size) stringResource(R.string.all_done) else stringResource(R.string.done_count, done, doses.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        LinearProgressIndicator(
                            progress = { done.toFloat() / doses.size },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }
            }
        }
        if (doses.isEmpty()) {
            item {
                if (data.treatments.isEmpty()) {
                    EmptyState("🐾", stringResource(R.string.empty_title), stringResource(R.string.empty_text)) {
                        Button(onClick = { vm.startNewCare() }) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.new_follow_up))
                        }
                    }
                } else {
                    EmptyState("✨", stringResource(R.string.nothing_today), stringResource(R.string.nothing_today_text))
                }
            }
        }
        val byTime = doses.groupBy { it.at.toLocalTime() }
        byTime.forEach { (time, list) ->
            item(key = "t$time") { SectionTitle(time.format(HH_MM), Modifier.padding(start = 4.dp)) }
            items(list, key = { it.key }) { dose ->
                DoseRow(
                    dose = dose,
                    pet = pets[dose.treatment.petId],
                    doneAt = data.done[dose.key],
                    now = vm.now,
                    onToggle = { vm.store.setDone(dose.key, dose.key !in data.done) },
                    onOpen = { vm.open(com.hive4apps.petcare.ui.Screen.PetDetail(dose.treatment.petId)) },
                )
            }
        }
    }
}

@Composable
fun DoseRow(dose: Dose, pet: Pet?, doneAt: Long?, now: LocalDateTime, onToggle: () -> Unit, onOpen: () -> Unit, showPet: Boolean = true) {
    val isDone = doneAt != null
    val late = !isDone && dose.at.isBefore(now)
    val container by animateColorAsState(
        when {
            isDone -> DoneGreen.copy(alpha = 0.12f)
            late -> Late.copy(alpha = 0.10f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "dose",
    )
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showPet) PetAvatar(pet, size = 40.dp) else KindBadge(dose.treatment.kind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (showPet) "${pet?.name.orEmpty()} · ${dose.treatment.name}" else dose.treatment.name,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                )
                val details = listOfNotNull(
                    stringResource(kindLabel(dose.treatment.kind)),
                    dose.treatment.dose.takeIf { it.isNotBlank() },
                    dose.total?.let { stringResource(R.string.dose_number_of, dose.number, it) },
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dose.treatment.notes.isNotBlank()) {
                    Text(dose.treatment.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    doneAt != null -> Text(
                        stringResource(R.string.done_at, Instant.ofEpochMilli(doneAt).atZone(ZoneId.systemDefault()).toLocalTime().format(HH_MM)),
                        style = MaterialTheme.typography.labelMedium,
                        color = DoneGreen,
                    )
                    late -> Text(stringResource(R.string.late), style = MaterialTheme.typography.labelMedium, color = Late)
                }
            }
            IconButton(onClick = onToggle) {
                if (isDone) {
                    Icon(Icons.Filled.CheckCircle, stringResource(R.string.mark_not_done), tint = DoneGreen)
                } else {
                    Icon(Icons.Outlined.RadioButtonUnchecked, stringResource(R.string.mark_done), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
