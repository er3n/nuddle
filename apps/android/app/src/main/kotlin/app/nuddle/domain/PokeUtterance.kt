package app.nuddle.domain

/**
 * What a ringing poke says aloud (NUD-124, docs/glossary.md, Spoken poke). The Android side turns it into text with the
 * `poke_speech_from` resource, so the names and the title stay as written (no suffix, open-questions #21).
 */
sealed interface PokeUtterance {
    /** "Sibel diyor ki: ilacını iç". */
    data class FromSender(
        val sender: String,
        val task: String,
    ) : PokeUtterance

    /** A task to self, or a sender whose name is not on the phone: the title alone. */
    data class TaskOnly(
        val task: String,
    ) : PokeUtterance
}

object PokeUtterances {
    /**
     * The utterance for a poke at [level], or null when nothing is said: a `SILENT` step, or a title with nothing to
     * read. Only the title is read, never the detail (docs/domain.md, Poke levels).
     */
    fun of(
        level: PokeLevel,
        title: String,
        toSelf: Boolean,
        senderName: String?,
    ): PokeUtterance? {
        if (level == PokeLevel.SILENT) return null
        val task = title.trim().takeIf { it.isNotEmpty() } ?: return null
        val sender = senderName?.trim()?.takeIf { it.isNotEmpty() }
        return if (toSelf || sender == null) PokeUtterance.TaskOnly(task) else PokeUtterance.FromSender(sender, task)
    }
}
