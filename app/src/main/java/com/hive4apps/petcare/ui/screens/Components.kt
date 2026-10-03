package com.hive4apps.petcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.CareKind
import com.hive4apps.petcare.core.Pet
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.core.Treatment
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

fun speciesEmoji(species: Species?): String = if (species == Species.CAT) "🐱" else "🐶"

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
