package com.hive4apps.petcare.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hive4apps.petcare.BuildConfig
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.CareKind
import com.hive4apps.petcare.core.Dose
import com.hive4apps.petcare.core.Treatment
import com.hive4apps.petcare.data.CrashLog
import com.hive4apps.petcare.reminders.Reminders
import com.hive4apps.petcare.ui.MainViewModel
import com.hive4apps.petcare.ui.theme.DoneGreen
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val data by vm.data.collectAsState()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.store.exportJson().toByteArray()) } }
                .onSuccess { Toast.makeText(context, R.string.backup_saved, Toast.LENGTH_SHORT).show() }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty")
                vm.store.importJson(text)
            }.onSuccess { Toast.makeText(context, R.string.backup_restored, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, R.string.backup_invalid, Toast.LENGTH_LONG).show() }
        }
    }
    var confirmImport by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)

        SectionTitle(stringResource(R.string.reminders))
        StatusCard(
            icon = Icons.Outlined.Notifications,
            ok = vm.canNotify,
            title = stringResource(if (vm.canNotify) R.string.notifications_on else R.string.notifications_off),
            text = stringResource(R.string.notifications_text),
            action = if (vm.canNotify) null else stringResource(R.string.open_settings),
            onAction = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
        StatusCard(
            icon = Icons.Outlined.Alarm,
            ok = vm.canRingOnTime,
            title = stringResource(if (vm.canRingOnTime) R.string.exact_on else R.string.exact_off),
            text = stringResource(R.string.exact_text),
            action = if (vm.canRingOnTime || Build.VERSION.SDK_INT < 31) null else stringResource(R.string.open_settings),
            onAction = {
                if (Build.VERSION.SDK_INT >= 31) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
        )
        Text(stringResource(R.string.remind_before), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 5, 10, 15, 30).forEach { minutes ->
                FilterChip(
                    selected = data.reminderAdvanceMinutes == minutes,
                    onClick = { vm.store.setReminderAdvance(minutes) },
                    label = { Text(if (minutes == 0) stringResource(R.string.on_time) else "$minutes min") },
                )
            }
        }
        OutlinedButton(onClick = {
            val sample = Treatment("test", "", context.getString(R.string.test_care_name), CareKind.MEDICATION, startDate = LocalDate.now().toString())
            Reminders.notifyDose(context, Dose(sample, LocalDateTime.now(), 1, null))
            if (!Reminders.canNotify(context)) Toast.makeText(context, R.string.notifications_off, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.test_notification)) }

        SectionTitle(stringResource(R.string.backup))
        Text(stringResource(R.string.backup_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { exporter.launch("petcare-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_backup))
            }
            OutlinedButton(onClick = { confirmImport = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.import_backup))
            }
        }
        if (confirmImport) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmImport = false },
                title = { Text(stringResource(R.string.import_backup)) },
                text = { Text(stringResource(R.string.import_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmImport = false
                        importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }

        SectionTitle(stringResource(R.string.about))
        Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val crash = remember { CrashLog.last(context) }
        if (crash != null) {
            var show by remember { mutableStateOf(false) }
            TextButton(onClick = { show = !show }) { Text(stringResource(R.string.last_error)) }
            if (show) {
                Text(crash.take(3000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                TextButton(onClick = {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, crash), null),
                    )
                }) { Text(stringResource(R.string.share)) }
            }
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, ok: Boolean, title: String, text: String, action: String?, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        null,
                        tint = if (ok) DoneGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                Text(text, style = MaterialTheme.typography.bodySmall)
                if (action != null) TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}
