package com.hive4apps.noamipets.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hive4apps.noamipets.R
import com.hive4apps.noamipets.core.AppData
import com.hive4apps.noamipets.core.CareHistory
import com.hive4apps.noamipets.core.CareRecord
import com.hive4apps.noamipets.core.HH_MM
import com.hive4apps.noamipets.core.HistoryEntry
import com.hive4apps.noamipets.core.Pet
import com.hive4apps.noamipets.ui.MainViewModel
import com.hive4apps.noamipets.ui.Screen
import com.hive4apps.noamipets.ui.theme.DoneGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The pet's care history: every dose due so far, given (and when) or not, day by day; and each care with
 * its period and how many doses were given. Cares removed from the follow-up stay here.
 */
@Composable
fun HistoryTab(vm: MainViewModel, pet: Pet, data: AppData) {
    val current = data.treatments.filter { it.petId == pet.id }
    val archived = data.history.filter { it.petId == pet.id }
    val entries = remember(current, archived, data.done, vm.now) { CareHistory.entries(current + archived, data.done, vm.now) }
    val records = remember(current, archived, data.done, vm.now, pet.closedOn) {
        CareHistory.records(current, archived, data.done, vm.now, followUpClosed = pet.closedOn != null)
    }
    var byCare by rememberSaveable { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf<CareRecord?>(null) }

    if (entries.isEmpty() && records.isEmpty()) {
        EmptyState("🗂️", stringResource(R.string.history_empty), stringResource(R.string.history_empty_text))
        return
    }
    if (sharing) {
        ShareDayDialog(
            historyText(LocalContext.current, pet, entries),
            onDismiss = { sharing = false },
            title = stringResource(R.string.share_history),
        )
    }
    erasing?.let { record ->
        AlertDialog(
            onDismissRequest = { erasing = null },
            title = { Text(stringResource(R.string.delete_from_history) + " ?") },
            text = { Text(record.treatment.name + "\n\n" + stringResource(R.string.delete_from_history_text)) },
            confirmButton = {
                TextButton(onClick = {
                    erasing = null
                    vm.store.deleteFromHistory(record.treatment.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { erasing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    val given = entries.count { it.given }
    val since = entries.minOfOrNull { it.at.toLocalDate() }
    val byDay = entries.groupBy { it.at.toLocalDate() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.progress_given, given, entries.size), style = MaterialTheme.typography.titleMedium)
                        since?.let { Text(stringResource(R.string.history_since, shortDate(it)), style = MaterialTheme.typography.bodyMedium) }
                        if (entries.isNotEmpty()) {
                            LinearProgressIndicator(
                                progress = { given.toFloat() / entries.size },
                                color = DoneGreen,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            )
                        }
                    }
                    IconButton(onClick = { sharing = true }, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share_history))
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !byCare, onClick = { byCare = false }, label = { Text(stringResource(R.string.history_journal)) })
                FilterChip(selected = byCare, onClick = { byCare = true }, label = { Text(stringResource(R.string.history_by_care)) })
            }
        }
        if (byCare) {
            val (ongoing, past) = records.partition { it.ongoing }
            if (ongoing.isNotEmpty()) item { SectionTitle(stringResource(R.string.ongoing)) }
            items(ongoing, key = { "c" + it.treatment.id }) { CareRecordCard(vm, it, onErase = null) }
            if (past.isNotEmpty()) item { SectionTitle(stringResource(R.string.history_past)) }
            items(past, key = { "c" + it.treatment.id }) { CareRecordCard(vm, it, onErase = { erasing = it }) }
        } else {
            if (entries.isEmpty()) {
                item { EmptyState("🗂️", stringResource(R.string.history_empty), stringResource(R.string.history_empty_text)) }
            }
            byDay.forEach { (day, list) ->
                item(key = "d$day") {
                    val dayGiven = list.count { it.given }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            (if (day == vm.today) stringResource(R.string.today) + " · " else "") + dayMonth(day) +
                                if (day.year != vm.today.year) " ${day.year}" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$dayGiven/${list.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (dayGiven == list.size) DoneGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(list, key = { "e" + it.treatment.id + it.at }) { HistoryRow(it) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val t = entry.treatment
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(entry.at.toLocalTime().format(HH_MM), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(52.dp))
        KindBadge(t.kind, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(t.name, t.dose.takeIf { it.isNotBlank() }).joinToString(" — "),
                style = MaterialTheme.typography.bodyMedium,
            )
            val status = entry.givenAt?.let { millis ->
                val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                if (at.toLocalDate() == entry.at.toLocalDate()) {
                    stringResource(R.string.history_given_at, at.toLocalTime().format(HH_MM))
                } else {
                    stringResource(R.string.history_given_on, shortDate(at.toLocalDate()), at.toLocalTime().format(HH_MM))
                }
            } ?: stringResource(R.string.history_not_given)
            val n = entry.number
            val total = entry.total
            val number = if (n != null && total != null) stringResource(R.string.dose_number_of, n, total) + " · " else ""
            Text(
                number + status,
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.given) DoneGreen else MaterialTheme.colorScheme.tertiary,
            )
        }
        Icon(
            if (entry.given) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = null,
            tint = if (entry.given) DoneGreen else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CareRecordCard(vm: MainViewModel, record: CareRecord, onErase: (() -> Unit)?) {
    val t = record.treatment
    Card(
        onClick = { if (!record.archived) vm.open(Screen.EditTreatment(t.id, t.petId)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindBadge(t.kind)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                    Text(treatmentSummary(t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (record.archived && onErase != null) {
                    IconButton(onClick = onErase) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete_from_history), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(end = 10.dp)) {
                if (record.due > 0) {
                    LinearProgressIndicator(
                        progress = { record.given.toFloat() / record.due },
                        color = DoneGreen,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(stringResource(R.string.progress_given, record.given, record.due), style = MaterialTheme.typography.bodySmall)
                Text(
                    listOfNotNull(
                        record.from?.let { stringResource(R.string.from_date, shortDate(it)) },
                        record.to?.takeIf { !record.ongoing || t.durationDays != null }?.let { stringResource(R.string.until_date, shortDate(it)) },
                        t.archivedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            ?.let { stringResource(R.string.history_removed_on, shortDate(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The history as plain text, for the vet or a pet sitter:
 *
 * 🐾 Historique des soins de Thor
 *
 * 📅 jeudi 9 octobre 2026
 * • 08:00 Apoquel 16 mg — 1/2 comprimé ✓ donné à 08:05
 * • 20:00 Apoquel 16 mg — 1/2 comprimé ✗ non coché
 */
fun historyText(context: Context, pet: Pet, entries: List<HistoryEntry>): String {
    val locale = context.resources.configuration.locales[0]
    val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
    val lines = mutableListOf("🐾 " + context.getString(R.string.share_history_title, pet.name))
    entries.groupBy { it.at.toLocalDate() }.forEach { (day, list) ->
        lines += ""
        lines += "📅 " + day.format(dayFormat)
        list.sortedBy { it.at }.forEach { e ->
            val t = e.treatment
            val text = StringBuilder("• ${e.at.toLocalTime().format(HH_MM)} ${t.name}")
            if (t.dose.isNotBlank()) text.append(" — ").append(t.dose)
            val givenAt = e.givenAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
            when {
                givenAt == null -> text.append(" ✗ ").append(context.getString(R.string.history_not_given))
                givenAt.toLocalDate() == day -> text.append(" ✓ ").append(context.getString(R.string.history_given_at, givenAt.toLocalTime().format(HH_MM)))
                else -> text.append(" ✓ ").append(
                    context.getString(
                        R.string.history_given_on,
                        givenAt.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                        givenAt.toLocalTime().format(HH_MM),
                    ),
                )
            }
            lines += text.toString()
        }
    }
    return lines.joinToString("\n")
}
