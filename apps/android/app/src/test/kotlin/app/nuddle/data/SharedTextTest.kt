package app.nuddle.data

import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Text shared to Nuddle from another app (NUD-127, docs/ux/platform.md, Other). Robolectric for the Intent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedTextTest {
    private fun share(
        text: String?,
        subject: String? = null,
        type: String = "text/plain",
        action: String = Intent.ACTION_SEND,
    ): Intent =
        Intent(action).setType(type).apply {
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    @Test
    fun `a shared message becomes the box text, trimmed`() {
        assertEquals("Yarın okula 20 TL getir", SharedText.of(share("  Yarın okula 20 TL getir\n")))
    }

    @Test
    fun `a subject goes on its own line above the text unless the text starts with it`() {
        assertEquals("Veli toplantısı\nCuma 18:00, 3B sınıfı", SharedText.of(share("Cuma 18:00, 3B sınıfı", "Veli toplantısı")))
        assertEquals("Veli toplantısı cuma 18:00", SharedText.of(share("Veli toplantısı cuma 18:00", "Veli toplantısı")))
        assertEquals("Veli toplantısı", SharedText.of(share(null, "Veli toplantısı")))
    }

    @Test
    fun `a long text is cut to what a write takes, without splitting a character`() {
        assertEquals(SharedText.MAX_LENGTH, SharedText.of(share("a".repeat(800)))?.length)
        val emojiAtTheEdge = "a".repeat(SharedText.MAX_LENGTH - 1) + "😀" + "b"
        assertEquals("a".repeat(SharedText.MAX_LENGTH - 1), SharedText.of(share(emojiAtTheEdge)))
    }

    @Test
    fun `anything else is not a text share`() {
        assertNull(SharedText.of(null))
        assertNull(SharedText.of(share("   ")))
        assertNull(SharedText.of(share(null)))
        assertNull(SharedText.of(share("bir resim", type = "image/jpeg")))
        assertNull(SharedText.of(share("merhaba", action = Intent.ACTION_VIEW)))
        assertNull(SharedText.of(Intent(Intent.ACTION_MAIN)))
    }
}
