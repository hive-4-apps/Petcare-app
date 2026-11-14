package com.hive4apps.noamipets.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hive4apps.noamipets.R
import com.hive4apps.noamipets.core.CareKind
import com.hive4apps.noamipets.core.Pet
import com.hive4apps.noamipets.core.Species
import com.hive4apps.noamipets.core.Treatment
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun kindLabel(kind: CareKind): Int = when (kind) {
    CareKind.MEDICATION -> R.string.kind_medication
    CareKind.SHAMPOO -> R.string.kind_shampoo
    CareKind.EYE_DROPS -> R.string.kind_eye_drops
    CareKind.EAR_DROPS -> R.string.kind_ear_drops
    CareKind.TOPICAL -> R.string.kind_topical
    CareKind.INJECTION -> R.string.kind_injection
    CareKind.ANTIPARASITIC -> R.string.kind_antiparasitic
    CareKind.OTHER -> R.string.kind_other
}

fun kindIcon(kind: CareKind): ImageVector = when (kind) {
    CareKind.MEDICATION -> Icons.Outlined.Medication
    CareKind.SHAMPOO -> Icons.Outlined.Shower
    CareKind.EYE_DROPS -> Icons.Outlined.Visibility
    CareKind.EAR_DROPS -> Icons.Outlined.Hearing
    CareKind.TOPICAL -> Icons.Outlined.Healing
    CareKind.INJECTION -> Icons.Outlined.Vaccines
    CareKind.ANTIPARASITIC -> Icons.Outlined.BugReport
    CareKind.OTHER -> Icons.Outlined.Pets
}

fun speciesEmoji(species: Species?): String = (species ?: Species.OTHER).emoji

fun speciesLabel(species: Species): Int = when (species) {
    Species.DOG -> R.string.dog
    Species.CAT -> R.string.cat
    Species.BIRD -> R.string.species_bird
    Species.RABBIT -> R.string.species_rabbit
    Species.HAMSTER -> R.string.species_hamster
    Species.GUINEA_PIG -> R.string.species_guinea_pig
    Species.CHINCHILLA -> R.string.species_chinchilla
    Species.TURTLE -> R.string.species_turtle
    Species.LIZARD -> R.string.species_lizard
    Species.SNAKE -> R.string.species_snake
    Species.FISH -> R.string.species_fish
    Species.OTHER -> R.string.species_other
}

/** Every kind of pet as chips, wrapping on several lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeciesPicker(selected: Species, onSelect: (Species) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Species.entries.forEach { s ->
            FilterChip(
                selected = selected == s,
                onClick = { onSelect(s) },
                label = { Text(s.emoji + "  " + stringResource(speciesLabel(s))) },
            )
        }
    }
}

@Composable
fun longDate(date: LocalDate): String {
    val locale = currentLocale()
    return date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
}

@Composable
fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()))

@Composable
fun dayMonth(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", currentLocale()))

@Composable
fun currentLocale(): Locale = LocalContext.current.resources.configuration.locales[0]

/** "2× par jour (08:00, 20:00) · 14 jours" */
@Composable
fun frequencyText(times: List<String>, everyDays: Int, durationDays: Int?): String {
    val perDay = times.size
    val when_ = if (everyDays <= 1) {
        pluralStringResource(R.plurals.times_per_day, perDay, perDay)
    } else {
        pluralStringResource(R.plurals.every_n_days, everyDays, everyDays)
    }
    val duration = if (durationDays == null) {
        stringResource(R.string.continuous)
    } else {
        pluralStringResource(R.plurals.during_days, durationDays, durationDays)
    }
    return "$when_ (${times.sorted().joinToString(", ")}) · $duration"
}

@Composable
fun treatmentSummary(t: Treatment): String = listOfNotNull(
    t.dose.takeIf { it.isNotBlank() },
    frequencyText(t.times, t.everyDays, t.durationDays),
).joinToString(" · ")

@Composable
fun PetAvatar(pet: Pet?, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(speciesEmoji(pet?.species), fontSize = (size.value * 0.5f).sp)
        // The pet's photo over its emoji (the emoji stays if the file is gone).
        pet?.photoPath?.let { File(it) }?.takeIf { it.exists() }?.let { photo ->
            AsyncImage(
                model = photo,
                contentDescription = pet?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

@Composable
fun KindBadge(kind: CareKind, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kindIcon(kind), contentDescription = stringResource(kindLabel(kind)), tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 56.sp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        action()
    }
}
