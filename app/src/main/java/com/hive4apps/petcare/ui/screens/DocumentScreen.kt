package com.hive4apps.petcare.ui.screens

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hive4apps.petcare.R
import com.hive4apps.petcare.ui.MainViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The photos of a vet document (pinch to zoom) and the text read from them. */
@Composable
fun DocumentScreen(vm: MainViewModel, documentId: String) {
    val data by vm.data.collectAsState()
    val doc = data.documents.find { it.id == documentId } ?: return
    var showText by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                longDate(Instant.ofEpochMilli(doc.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        items(doc.imagePaths) { path ->
            ZoomableImage(File(path))
        }
        if (doc.text.isNotBlank()) {
            item {
                TextButton(onClick = { showText = !showText }) {
                    Text(stringResource(if (showText) R.string.hide_text else R.string.show_text))
                }
                if (showText) Text(doc.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        item {
            TextButton(onClick = { deleting = true }) {
                Text(stringResource(R.string.delete_document), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_document)) },
            text = { Text(stringResource(R.string.delete_document_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.back()
                    vm.store.deleteDocument(doc.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
