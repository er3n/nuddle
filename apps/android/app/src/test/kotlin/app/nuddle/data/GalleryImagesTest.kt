package app.nuddle.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.photo.ContentGalleryImages
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** "Galeriden seç" (4d, 5a): a picked picture read through its URI comes back as the JPEG a shot would give. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GalleryImagesTest {
    @get:Rule val temp = TemporaryFolder()

    private val gallery = ContentGalleryImages(ApplicationProvider.getApplicationContext())

    private fun picture(
        width: Int,
        height: Int,
    ): File =
        temp.newFile("picked.jpg").also { file ->
            file.outputStream().use {
                Bitmap
                    .createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888,
                    ).compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
        }

    @Test
    fun `a picked picture is read as a JPEG of its own size`() =
        runTest {
            val bytes = gallery.read(Uri.fromFile(picture(20, 10)))
            assertNotNull(bytes)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(20 to 10, decoded.width to decoded.height)
        }

    // Robolectric's legacy graphics decode any bytes into a bitmap, so an unreadable picture cannot be tried here.
    @Test
    fun `a picture that is gone gives nothing`() =
        runTest {
            assertNull(gallery.read(Uri.fromFile(File(temp.root, "missing.jpg"))))
        }
}
