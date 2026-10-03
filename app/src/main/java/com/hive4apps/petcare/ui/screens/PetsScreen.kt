package com.hive4apps.petcare.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.Schedule
import com.hive4apps.petcare.ui.MainViewModel
import com.hive4apps.petcare.ui.Screen

@Composable
fun PetsScreen(vm: MainViewModel) {
    val data by vm.data.collectAsState()
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.tab_pets), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (data.pets.isEmpty()) {
            item {
                EmptyState("🐶🐱", stringResource(R.string.no_pets), stringResource(R.string.no_pets_text)) {
                    Button(onClick = { vm.startNewCare() }) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.new_follow_up))
                    }
                }
            }
        }
        items(data.pets, key = { it.id }) { pet ->
            val treatments = data.treatments.filter { it.petId == pet.id }
            val active = treatments.count { !Schedule.isFinished(it, vm.today) }
            val todayDoses = Schedule.dayPlan(treatments, vm.today)
            val doneToday = todayDoses.count { it.key in data.done }
            Card(
                onClick = { vm.open(Screen.PetDetail(pet.id)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PetAvatar(pet, size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pet.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            pluralStringResource(R.plurals.active_treatments, active, active),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (todayDoses.isNotEmpty()) {
                            Text(
                                stringResource(R.string.today_progress, doneToday, todayDoses.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
}
