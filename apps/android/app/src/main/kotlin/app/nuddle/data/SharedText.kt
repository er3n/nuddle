package app.nuddle.data

import android.content.Intent

/**
 * Text shared to Nuddle from another app (NUD-127, docs/ux/platform.md, Other): a WhatsApp message, a school e-mail.
 * It goes into the compose box, never straight out; the person edits and sends it.
 */
object SharedText {
    /** The longest text a write takes (`WriteTaskCommand.text` in packages/contract/openapi.yaml). */
    const val MAX_LENGTH = 500

    private const val TEXT_PLAIN = "text/plain"

    /** The box text of a share intent, or null when [intent] is not a text share or carries no text. */
    fun of(intent: Intent?): String? =
        intent?.let {
            of(
                action = it.action,
                type = it.type,
                text = it.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                subject = it.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            )
        }

    /**
     * The subject (an e-mail's, when the app sends one) on its own line above the text, unless the text already
     * starts with it; trimmed, and cut to [MAX_LENGTH] without splitting a character.
     */
    fun of(
        action: String?,
        type: String?,
        text: String?,
        subject: String?,
    ): String? {
        if (action != Intent.ACTION_SEND || type?.startsWith(TEXT_PLAIN) != true) return null
        val body = text?.trim().orEmpty()
        val title = subject?.trim().orEmpty().takeUnless { it.isEmpty() || body.startsWith(it) }
        val joined = listOfNotNull(title, body.takeIf { it.isNotEmpty() }).joinToString("\n")
        return joined.takeIf { it.isNotEmpty() }?.let(::cut)
    }

    private fun cut(text: String): String {
        if (text.length <= MAX_LENGTH) return text
        val end = if (text[MAX_LENGTH - 1].isHighSurrogate()) MAX_LENGTH - 1 else MAX_LENGTH
        return text.substring(0, end).trimEnd()
    }
}
