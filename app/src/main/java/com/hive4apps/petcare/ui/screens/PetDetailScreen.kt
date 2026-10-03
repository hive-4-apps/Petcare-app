package com.hive4apps.petcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.Pet
import com.hive4apps.petcare.core.Schedule
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.core.Treatment
import com.hive4apps.petcare.ui.MainViewModel
import com.hive4apps.petcare.ui.Screen
import com.hive4apps.petcare.ui.theme.DoneGreen
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PetDetailScreen(vm: MainViewModel, petId: String) {
    val data by vm.data.collectAsState()
    val pet = data.pets.find { it.id == petId } ?: return
    val treatments = data.treatments.filter { it.petId == petId }
    var tab by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PetAvatar(pet, size = 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pet.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(if (pet.species == Species.CAT) R.string.cat else R.string.dog),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_pet)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menu = false; renaming = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_pet)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; deleting = true },
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.startNewCare(petId) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.DocumentScanner, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.scan_prescription), maxLines = 1)
            }
            OutlinedButton(onClick = { vm.open(Screen.EditTreatment(null, petId)) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_care), maxLines = 1)
            }
        }
        TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 12.dp)) {
            listOf(R.string.tab_cares, R.string.tab_plan, R.string.tab_documents).forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
            }
        }
        when (tab) {
            0 -> CaresTab(vm, treatments, data.done.keys)
            1 -> PlanTab(vm, pet, treatments, data.done)
            else -> DocumentsTab(vm, petId)
        }
    }

    if (renaming) PetDialog(pet, onDismiss = { renaming = false }) { vm.store.updatePet(it); renaming = false }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_pet_title, pet.name)) },
            text = { Text(stringResource(R.string.delete_pet_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.back()
                    vm.store.deletePet(pet.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CaresTab(vm: MainViewModel, treatments: List<Treatment>, done: Set<String>) {
    val (finished, active) = treatments.partition { Schedule.isFinished(it, vm.today) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (treatments.isEmpty()) {
            item { EmptyState("💊", stringResource(R.string.no_cares), stringResource(R.string.no_cares_text)) }
        }
        if (active.isNotEmpty()) item { SectionTitle(stringResource(R.string.ongoing)) }
        items(active.sortedBy { it.startDate }, key = { it.id }) { TreatmentCard(vm, it, done) }
        if (finished.isNotEmpty()) item { SectionTitle(stringResource(R.string.finished)) }
        items(finished.sortedByDescending { it.startDate }, key = { it.id }) { TreatmentCard(vm, it, done) }
    }
}

@Composable
private fun TreatmentCard(vm: MainViewModel, t: Treatment, done: Set<String>) {
    val progress = Schedule.progress(t, done, vm.today)
    val last = Schedule.lastDay(t)
    Card(
        onClick = { vm.open(Screen.EditTreatment(t.id, t.petId)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindBadge(t.kind)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                    Text(treatmentSummary(t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            val total = progress.total
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { progress.given.toFloat() / total },
                    color = DoneGreen,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.progress_given, progress.given, total),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val missed = progress.dueSoFar - progress.given
            Text(
                listOfNotNull(
                    stringResource(R.string.from_date, shortDate(LocalDate.parse(t.startDate))),
                    last?.let { stringResource(R.string.until_date, shortDate(it)) },
                    t.stoppedOn?.let { stringResource(R.string.stopped_on, shortDate(LocalDate.parse(it))) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (missed > 0 && !Schedule.isFinished(t, vm.today)) {
                Text(
                    stringResource(R.string.not_validated, missed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PlanTab(vm: MainViewModel, pet: Pet, treatments: List<Treatment>, done: Map<String, Long>) {
    var onlyRemaining by rememberSaveable { mutableStateOf(false) }
    val starts = treatments.mapNotNull { runCatching { LocalDate.parse(it.startDate) }.getOrNull() }
    if (starts.isEmpty()) {
        EmptyState("📅", stringResource(R.string.no_cares), stringResource(R.string.no_cares_text))
        return
    }
    // Continuous treatments: the plan shows the next 30 days.
    val end = treatments.maxOf { Schedule.lastDay(it) ?: vm.today.plusDays(30) }
    val from = if (onlyRemaining) vm.today else starts.min()
    val doses = treatments.flatMap { Schedule.doses(it, from, end) }.sortedBy { it.at }
    val byDay = doses.groupBy { it.at.toLocalDate() }
    val total = doses.size
    val given = doses.count { it.key in done }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        stringResource(R.string.plan_summary, byDay.size, total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.plan_until, longDate(end)), style = MaterialTheme.typography.bodyMedium)
                    if (!onlyRemaining) {
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else given.toFloat() / total },
                            color = DoneGreen,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        Text(stringResource(R.string.progress_given, given, total), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyRemaining, onClick = { onlyRemaining = false }, label = { Text(stringResource(R.string.whole_plan)) })
                FilterChip(selected = onlyRemaining, onClick = { onlyRemaining = true }, label = { Text(stringResource(R.string.from_today)) })
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "d$day") {
                val dayDone = list.count { it.key in done }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        (if (day == vm.today) stringResource(R.string.today) + " · " else "") + dayMonth(day),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (day == vm.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$dayDone/${list.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dayDone == list.size) DoneGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.key }) { dose ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dose.at.toLocalTime().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        DoseRow(
                            dose = dose,
                            pet = pet,
                            doneAt = done[dose.key],
                            now = vm.now,
                            onToggle = { vm.store.setDone(dose.key, dose.key !in done) },
                            onOpen = { vm.open(Screen.EditTreatment(dose.treatment.id, pet.id)) },
                            showPet = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentsTab(vm: MainViewModel, petId: String) {
    val data by vm.data.collectAsState()
    val docs = data.documents.filter { it.petId == petId }.sortedByDescending { it.createdAt }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (docs.isEmpty()) {
            item { EmptyState("📄", stringResource(R.string.no_documents), stringResource(R.string.no_documents_text)) }
        }
        items(docs, key = { it.id }) { doc ->
            Card(onClick = { vm.open(Screen.Document(doc.id)) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    doc.imagePaths.firstOrNull()?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(72.dp).height(96.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            longDate(Instant.ofEpochMilli(doc.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val linked = data.treatments.filter { it.documentId == doc.id }.joinToString(", ") { it.name }
                        if (linked.isNotBlank()) Text(linked, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Text(
                            stringResource(R.string.pages_count, doc.imagePaths.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PetDialog(pet: Pet, onDismiss: () -> Unit, onSave: (Pet) -> Unit) {
    var name by remember { mutableStateOf(pet.name) }
    var species by remember { mutableStateOf(pet.species) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_pet)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Species.entries.forEach { s ->
                        FilterChip(
                            selected = species == s,
                            onClick = { species = s },
                            label = { Text(speciesEmoji(s) + "  " + stringResource(if (s == Species.DOG) R.string.dog else R.string.cat)) },
                        )
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.pet_name)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pet.copy(name = name.trim(), species = species)) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
