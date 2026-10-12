package app.nuddle.data.voice

import kotlinx.coroutines.flow.Flow

/** What the recognizer heard so far (4h). A session ends with [Final] or [Failed]. */
sealed interface Heard {
    data class Partial(
        val text: String,
    ) : Heard

    /** Empty when nothing was understood. */
    data class Final(
        val text: String,
    ) : Heard

    data object Failed : Heard
}

/** Speech to text on the phone (open-questions#17): the transcript is then written like typed text. */
interface SpeechInput {
    /** Whether the phone can recognize speech at all. */
    fun available(): Boolean

    /** Listens until [stop] or silence. Cancelling the collection cancels the session. */
    fun listen(): Flow<Heard>

    /** The finger was lifted: stop listening and deliver the final result. */
    fun stop()
}
