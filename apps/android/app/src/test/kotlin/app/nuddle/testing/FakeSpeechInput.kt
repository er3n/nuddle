package app.nuddle.testing

import app.nuddle.data.voice.Heard
import app.nuddle.data.voice.SpeechInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Speech a test says with [hear]; a session ends with a Final or Failed. [stop] records the release. */
class FakeSpeechInput(
    var canListen: Boolean = true,
) : SpeechInput {
    private var events = Channel<Heard>(Channel.UNLIMITED)
    var stops = 0
    var sessions = 0

    override fun available(): Boolean = canListen

    override fun listen(): Flow<Heard> =
        flow {
            sessions++
            for (event in events) {
                emit(event)
                if (event !is Heard.Partial) break
            }
        }

    override fun stop() {
        stops++
    }

    fun hear(event: Heard) {
        events.trySend(event)
    }
}
