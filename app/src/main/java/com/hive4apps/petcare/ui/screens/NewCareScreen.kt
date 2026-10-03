package com.hive4apps.petcare.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.ui.Draft
import com.hive4apps.petcare.ui.MainViewModel
import com.hive4apps.petcare.ui.NewCareFlow
import java.io.File
import java.time.LocalDateTime

@Composable
fun NewCareScreen(vm: MainViewModel, flow: NewCareFlow) {
    when (flow.step) {
        NewCareFlow.Step.PET -> ChoosePetStep(vm, flow)
        NewCareFlow.Step.DOCUMENT -> DocumentStep(vm, flow)
        NewCareFlow.Step.REVIEW -> ReviewStep(vm, flow)
    }
}

@Composable
private fun StepHeader(step: Int, title: String, text: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        LinearProgressIndicator(progress = { step / 3f }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
        Text(stringResource(R.string.step_n, step, 3), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- 1. Which pet ----

@Composable
private fun ChoosePetStep(vm: MainViewModel, flow: NewCareFlow) {
    val data by vm.data.collectAsState()
    var creating by rememberSaveable { mutableStateOf(data.pets.isEmpty()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StepHeader(1, stringResource(R.string.which_pet), stringResource(R.string.which_pet_text)) }
        items(data.pets, key = { it.id }) { pet ->
            OutlinedCard(
                onClick = {
                    flow.petId = pet.id
                    flow.step = NewCareFlow.Step.DOCUMENT
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PetAvatar(pet)
                    Spacer(Modifier.width(14.dp))
                    Text(pet.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            if (!creating) {
                FilledTonalButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_pet))
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.new_pet), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Species.entries.forEach { species ->
                                FilterChip(
                                    selected = flow.newPetSpecies == species,
                                    onClick = { flow.newPetSpecies = species },
                                    label = {
                                        Text(
                                            speciesEmoji(species) + "  " +
                                                stringResource(if (species == Species.DOG) R.string.dog else R.string.cat),
                                        )
                                    },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = flow.newPetName,
                            onValueChange = { flow.newPetName = it },
                            label = { Text(stringResource(R.string.pet_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                flow.petId = null
                                flow.step = NewCareFlow.Step.DOCUMENT
                            },
                            enabled = flow.newPetName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.next)) }
                    }
                }
            }
        }
    }
}

// ---- 2. Photos of the vet's documents ----

@Composable
private fun DocumentStep(vm: MainViewModel, flow: NewCareFlow) {
    val context = LocalContext.current
    // Kept as a path: the camera app may push this screen out of memory.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPhoto?.let(::File)
        if (ok && file != null && file.length() > 0) flow.pages += file else file?.delete()
        pendingPhoto = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris: List<Uri> ->
        uris.forEach { uri ->
            val file = flow.newPhotoFile()
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            if (file.length() > 0) flow.pages += file else file.delete()
        }
    }
    val petName = vm.data.collectAsState().value.pets.find { it.id == flow.petId }?.name ?: flow.newPetName

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        StepHeader(2, stringResource(R.string.scan_title), stringResource(R.string.scan_text, petName))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val file = flow.newPhotoFile()
                    pendingPhoto = file.path
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (flow.pages.isEmpty()) R.string.take_photo else R.string.add_page))
            }
            OutlinedButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.from_gallery))
            }
        }
        Spacer(Modifier.height(16.dp))
        if (flow.pages.isEmpty()) {
            Text(
                stringResource(R.string.scan_tips),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(flow.pages.toList(), key = { _, f -> f.path }) { index, file ->
                    Box {
                        AsyncImage(
                            model = file,
                            contentDescription = stringResource(R.string.page_n, index + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 150.dp, height = 210.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        IconButton(onClick = { flow.pages.remove(file); file.delete() }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
        flow.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.read_error, it), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.weight(1f))
        if (flow.reading) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reading))
            }
        } else {
            Button(
                onClick = { vm.readDocument() },
                enabled = flow.pages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.DocumentScanner, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.read_document))
            }
            TextButton(
                onClick = {
                    flow.drafts.clear()
                    flow.drafts += Draft().startingFrom(LocalDateTime.now())
                    flow.step = NewCareFlow.Step.REVIEW
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.EditNote, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.enter_manually))
            }
        }
    }
}

// ---- 3. Check what was read ----

@Composable
private fun ReviewStep(vm: MainViewModel, flow: NewCareFlow) {
    var showText by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(flow.drafts.firstOrNull()?.id) }
    val count = flow.drafts.count { it.include && it.name.isNotBlank() }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                StepHeader(
                    3,
                    stringResource(R.string.review_title),
                    if (flow.pages.isEmpty()) {
                        stringResource(R.string.review_text_manual)
                    } else if (flow.drafts.isEmpty()) {
                        stringResource(R.string.review_nothing_found)
                    } else {
                        stringResource(R.string.review_text, flow.drafts.size)
                    },
                )
            }
            itemsIndexed(flow.drafts.toList(), key = { _, d -> d.id }) { index, draft ->
                val open = expanded == draft.id
                OutlinedCard(
                    border = BorderStroke(
                        1.dp,
                        if (draft.guessed.isNotEmpty() && draft.include) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { expanded = if (open) null else draft.id },
                        ) {
                            Checkbox(checked = draft.include, onCheckedChange = { flow.drafts[index] = draft.copy(include = it) })
                            KindBadge(draft.kind, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    draft.name.ifBlank { stringResource(R.string.unnamed) },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    listOfNotNull(
                                        draft.dose.takeIf { it.isNotBlank() },
                                        frequencyText(draft.times, draft.everyDays, if (draft.continuous) null else draft.durationDays),
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (draft.guessed.isNotEmpty() && draft.include) {
                                    Text(
                                        stringResource(R.string.to_check),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        }
                        if (open) {
                            if (draft.source.isNotBlank()) {
                                Text(
                                    draft.source,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            TreatmentForm(draft, onChange = { flow.drafts[index] = it.copy(include = true) }, modifier = Modifier.padding(top = 8.dp))
                            TextButton(onClick = { flow.drafts.removeAt(index) }) { Text(stringResource(R.string.remove_item)) }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val d = Draft().startingFrom(LocalDateTime.now())
                        flow.drafts += d
                        expanded = d.id
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_care))
                }
            }
            if (flow.text.isNotBlank()) {
                item {
                    TextButton(onClick = { showText = !showText }) {
                        Text(stringResource(if (showText) R.string.hide_text else R.string.show_text))
                    }
                    if (showText) {
                        Text(flow.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Button(
            onClick = { vm.finishNewCare() },
            enabled = count > 0 || flow.pages.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) {
            Icon(Icons.Filled.Check, null)
            Spacer(Modifier.width(8.dp))
            Text(pluralStringResource(R.plurals.create_plan, count, count))
        }
    }
}
