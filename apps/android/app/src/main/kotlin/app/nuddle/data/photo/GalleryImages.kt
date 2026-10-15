package app.nuddle.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** A picture picked from the gallery (4d, "Galeriden seç"), read as the same JPEG a shot would give. */
interface GalleryImages {
    /** Null when the picture cannot be read; the person can pick again. */
    suspend fun read(uri: Uri): ByteArray?
}

class ContentGalleryImages
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : GalleryImages {
        override suspend fun read(uri: Uri): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = decodeScaled(uri) ?: return@withContext null
                    bitmap.toJpeg(rotationDegrees = rotationOf(uri))
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }

        /** Decoded at most twice the sent size (BitmapFactory samples by powers of two); toJpeg does the rest. */
        private fun decodeScaled(uri: Uri): Bitmap? {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

        /** The EXIF orientation, so a picture taken sideways is sent upright. */
        private fun rotationOf(uri: Uri): Int =
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
                    ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
                    ExifInterface.ORIENTATION_ROTATE_270 -> THREE_QUARTER_TURN
                    else -> 0
                }
            } ?: 0

        private companion object {
            const val QUARTER_TURN = 90
            const val HALF_TURN = 180
            const val THREE_QUARTER_TURN = 270
        }
    }
