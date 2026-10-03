package com.hive4apps.petcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.Schedule
import com.hive4apps.petcare.ui.Draft
import com.hive4apps.petcare.ui.MainViewModel
import com.hive4apps.petcare.ui.Screen
import java.time.LocalDateTime

@Composable
fun EditTreatmentScreen(vm: MainViewModel, treatmentId: String?, petId: String) {
    val existing = treatmentId?.let { id -> vm.data.value.treatments.find { it.id == id } }
    var draft by remember(treatmentId) {
        mutableStateOf(existing?.let(Draft::fromTreatment) ?: Draft().startingFrom(LocalDateTime.now()))
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (existing == null) R.string.new_care else R.string.edit_care),
                style = MaterialTheme.typography.headlineSmall,
            )
            TreatmentForm(draft, onChange = { draft = it })
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                existing.documentId?.let { docId ->
                    OutlinedButton(onClick = { vm.open(Screen.Document(docId)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.see_document))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (existing.stoppedOn == null && !Schedule.isFinished(existing, vm.today)) {
                        OutlinedButton(onClick = { confirmStop = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.stop_treatment))
                        }
                    } else if (existing.stoppedOn != null) {
                        OutlinedButton(
                            onClick = {
                                vm.store.saveTreatments(listOf(existing.copy(stoppedOn = null)), null)
                                vm.back()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.resume_treatment)) }
                    }
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Button(
            onClick = {
                vm.store.saveTreatments(listOf(draft.toTreatment(petId, existing?.documentId).copy(stoppedOn = existing?.stoppedOn)), null)
                vm.back()
            },
            enabled = draft.name.isNotBlank() && draft.times.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) {
            Icon(Icons.Filled.Check, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save))
        }
    }

    if (confirmStop && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_treatment)) },
            text = { Text(stringResource(R.string.stop_treatment_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.store.saveTreatments(listOf(existing.copy(stoppedOn = vm.today.plusDays(1).toString())), null)
                    confirmStop = false
                    vm.back()
                }) { Text(stringResource(R.string.stop_treatment)) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_care_title, existing.name)) },
            text = { Text(stringResource(R.string.delete_care_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.back()
                    vm.store.deleteTreatment(existing.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
