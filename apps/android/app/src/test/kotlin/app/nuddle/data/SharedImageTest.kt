package app.nuddle.data

import android.content.Intent
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** An image shared to Nuddle (NUD-138, docs/ux/platform.md, Other). Robolectric for the Intent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedImageTest {
    private val picture = Uri.parse("content://com.whatsapp.provider/media/1.jpg")

    private fun share(
        uri: Uri?,
        text: String? = null,
        type: String = "image/jpeg",
        action: String = Intent.ACTION_SEND,
    ): Intent =
        Intent(action).setType(type).apply {
            uri?.let { putExtra(Intent.EXTRA_STREAM, it) }
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }

    @Test
    fun `a shared image becomes the attached picture, its text the box text`() {
        assertEquals(SharedImage(picture, null), SharedImages.of(share(picture)))
        assertEquals(
            SharedImage(picture, "markete git, fotodakileri al"),
            SharedImages.of(share(picture, "  markete git, fotodakileri al ")),
        )
        assertEquals(SharedImage(picture, null), SharedImages.of(share(picture, type = "image/png")))
    }

    @Test
    fun `nothing for a text share, an image without its stream, or another action`() {
        assertNull(SharedImages.of(share(null)))
        assertNull(SharedImages.of(share(picture, type = "text/plain")))
        assertNull(SharedImages.of(share(picture, action = Intent.ACTION_SEND_MULTIPLE)))
        assertNull(SharedImages.of(null))
    }
}
