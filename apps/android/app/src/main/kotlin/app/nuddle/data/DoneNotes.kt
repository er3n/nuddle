package app.nuddle.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A finished task to tell the person about (DoneNote, 5c). [caughtAt] is the poke that caught them, if one rang. */
data class DoneNote(
    val taskId: String,
    val title: String,
    val caughtAt: Int?,
)

/**
 * The done note waiting for home, whichever way the task was finished: "Şimdiden bitti" on home, or Bitti on the poke
 * (full screen or notification), which closes while home is not on screen. Home shows it for 4 s once it is visible
 * (TODO(open-questions#31)). Kept in memory only: a note does not outlive the process.
 */
@Singleton
class DoneNotes
    @Inject
    constructor() {
        private val latest = MutableStateFlow<DoneNote?>(null)
        val pending: StateFlow<DoneNote?> = latest.asStateFlow()

        fun post(note: DoneNote) {
            latest.value = note
        }

        /** Home showed [note] for its 4 s; a newer note stays. */
        fun shown(note: DoneNote) {
            latest.compareAndSet(note, null)
        }
    }
