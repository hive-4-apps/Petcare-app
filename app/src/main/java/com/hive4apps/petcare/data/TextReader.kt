package com.hive4apps.petcare.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

/** Reads the text of the vet's documents on the phone (ML Kit, offline: the photos never leave the phone). */
object TextReader {
    suspend fun read(context: Context, images: List<File>): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val pages = mutableListOf<String>()
            for (file in images) {
                // The camera's rotation (EXIF) is applied by fromFilePath.
                val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                pages += inReadingOrder(recognizer.process(image).await())
            }
            return pages.joinToString("\n")
        } finally {
            recognizer.close()
        }
    }

    /**
     * Lines top to bottom, and left to right when two lines share the same height (a name on the left, a
     * quantity on the right): closer to how a prescription is read than the block order of the recogniser.
     */
    private fun inReadingOrder(text: Text): String {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        if (lines.isEmpty()) return text.text
        val height = lines.map { it.boundingBox!!.height() }.sorted()[lines.size / 2].coerceAtLeast(1)
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (line in lines.sortedBy { it.boundingBox!!.centerY() }) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.first().boundingBox!!.centerY() - line.boundingBox!!.centerY()) < height / 2) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } }
    }
}
