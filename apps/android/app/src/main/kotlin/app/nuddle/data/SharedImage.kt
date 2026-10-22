package app.nuddle.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** An image shared to dürt (NUD-138): the picture behind [uri], and the text that came with it for the box. */
data class SharedImage(
    val uri: Uri,
    val text: String?,
)

/**
 * An image shared to Nuddle from another app (NUD-138, docs/ux/platform.md, Other): a WhatsApp photo, a shopping list.
 * It becomes the attached example photo in the compose bar, its text goes in the box as for a text share; nothing goes
 * out by itself.
 */
object SharedImages {
    private const val IMAGE = "image/"
    private const val TEXT_PLAIN = "text/plain"

    /** The shared image of [intent], or null when it is not a single image share. */
    fun of(intent: Intent?): SharedImage? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith(IMAGE) != true) return null
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
        val text =
            SharedText.of(
                action = Intent.ACTION_SEND,
                type = TEXT_PLAIN,
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            )
        return SharedImage(uri, text)
    }
}
