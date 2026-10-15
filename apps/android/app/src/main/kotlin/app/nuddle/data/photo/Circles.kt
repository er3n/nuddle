package app.nuddle.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap

/** The bitmap's centre square as a circle on a transparent ground, for a notification's large icon (NUD-80). */
fun Bitmap.circled(): Bitmap {
    val side = minOf(width, height)
    val square = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    val out = createBitmap(side, side)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
    val radius = side / 2f
    Canvas(out).drawCircle(radius, radius, radius, paint)
    return out
}
