package app.nuddle.data.photo

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Longest side of a sent photo: enough to read a receipt, small enough for a slow connection. */
const val PHOTO_MAX_SIDE = 1600
private const val JPEG_QUALITY = 85

/** The bitmap scaled down to [maxSide] on its longest side, rotated by [rotationDegrees], as JPEG bytes. */
fun Bitmap.toJpeg(
    maxSide: Int = PHOTO_MAX_SIDE,
    rotationDegrees: Int = 0,
): ByteArray {
    val scale = minOf(1f, maxSide.toFloat() / max(width, height))
    val matrix =
        Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationDegrees.toFloat())
        }
    val upright = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    return ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}
