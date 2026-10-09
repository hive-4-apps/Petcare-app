package com.hive4apps.petcare.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.Dose
import com.hive4apps.petcare.core.HH_MM
import com.hive4apps.petcare.core.Pet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The plan of one day as plain text, readable in WhatsApp or anywhere it is pasted:
 *
 * 🐾 Soins du jeudi 9 octobre
 *
 * ⏰ 08:00
 * • 🐶 Thor : Apoquel 16 mg — 1/2 comprimé (prise 3/14) ✓ fait à 08:05
 */
fun dayPlanText(context: Context, day: LocalDate, doses: List<Dose>, pets: Map<String, Pet>, done: Map<String, Long>): String {
    val locale = context.resources.configuration.locales[0]
    val date = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
    val lines = mutableListOf("🐾 " + context.getString(R.string.share_day_title, date))
    if (doses.isEmpty()) {
        lines += ""
        lines += context.getString(R.string.nothing_today_text)
    }
    doses.groupBy { it.at.toLocalTime() }.forEach { (time, list) ->
        lines += ""
        lines += "⏰ ${time.format(HH_MM)}"
        list.forEach { dose ->
            val pet = pets[dose.treatment.petId]
            val t = dose.treatment
            val text = StringBuilder("• ${speciesEmoji(pet?.species)} ${pet?.name.orEmpty()} : ${t.name}")
            if (t.dose.isNotBlank()) text.append(" — ").append(t.dose)
            dose.total?.let { text.append(" (").append(context.getString(R.string.dose_number_of, dose.number, it)).append(")") }
            done[dose.key]?.let {
                val at = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(HH_MM)
                text.append(" ✓ ").append(context.getString(R.string.share_done_at, at))
            }
            lines += text.toString()
            if (t.notes.isNotBlank()) lines += "   ${t.notes}"
        }
    }
    return lines.joinToString("\n")
}

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

/** WhatsApp directly when installed, else the usual "share with" list. */
fun shareText(context: Context, text: String, whatsApp: Boolean) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (whatsApp) {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(Intent(send).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(context, R.string.whatsapp_missing, Toast.LENGTH_SHORT).show()
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_day)))
}

/** Preview of the day's text, with WhatsApp / other apps / copy. */
@Composable
fun ShareDayDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_day)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
                Button(onClick = { shareText(context, text, whatsApp = true); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_whatsapp))
                }
                OutlinedButton(onClick = { shareText(context, text, whatsApp = false); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_other))
                }
                OutlinedButton(onClick = { copyText(context, text); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_text))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
