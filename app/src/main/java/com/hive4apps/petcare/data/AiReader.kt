package com.hive4apps.petcare.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.hive4apps.petcare.core.AiPrescriptionReader
import com.hive4apps.petcare.core.AiProvider
import com.hive4apps.petcare.core.AiReading
import com.hive4apps.petcare.core.ExistingCare
import com.hive4apps.petcare.core.PageImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.math.max

/**
 * The optional AI reading (Claude): reads handwriting, which the phone's text recognition cannot. Only used when
 * the user entered their own Anthropic API key in Settings; the photos are then sent to Anthropic.
 */
object AiReader {
    private const val PREFS = "ai"
    private const val KEY = "anthropic_api_key"

    /** Long side of the photos sent: sharp enough for handwriting, small enough to send quickly. */
    private const val MAX_SIDE = 2400

    // Shared preferences are not in the backup rules (files only): the key stays on this phone.
    fun apiKey(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(context: Context, key: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (key.isNullOrBlank()) remove(KEY) else putString(KEY, AiProvider.cleanKey(key))
        }.apply()
    }

    suspend fun read(apiKey: String, images: List<File>, existing: List<ExistingCare>): AiReading = withContext(Dispatchers.IO) {
        AiPrescriptionReader.read(apiKey, images.map { PageImage(encode(it)) }, existing)
    }

    /** JPEG, upright (camera EXIF rotation applied), at most [MAX_SIDE] pixels, in base64. */
    private fun encode(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("photo illisible")
        val matrix = Matrix()
        val scale = MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        val rotation = when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation != 0f) matrix.postRotate(rotation)
        if (!matrix.isIdentity) {
            val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (turned !== bitmap) bitmap.recycle()
            bitmap = turned
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }
}
