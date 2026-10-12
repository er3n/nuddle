package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.voice.Heard
import app.nuddle.data.voice.SpeechInput
import app.nuddle.ui.components.VoiceNotice
import app.nuddle.ui.theme.DurtMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hold the mic to speak (4h): the live transcript while the finger is down, then the text goes to home as if typed
 * (open-questions#17). Sliding left cancels (open-questions#6). Nothing heard, no permission or no recognizer on the
 * phone shows a short note by the mic (open-questions#40).
 */
@HiltViewModel
class VoiceViewModel
    @Inject
    constructor(
        private val speech: SpeechInput,
    ) : ViewModel() {
        private val heard = MutableStateFlow<String?>(null)

        /** What was heard so far while listening; null when not listening. */
        val listening: StateFlow<String?> = heard.asStateFlow()

        private val shown = MutableStateFlow<VoiceNotice?>(null)
        val notice: StateFlow<VoiceNotice?> = shown.asStateFlow()

        private val spoken = Channel<String>(Channel.BUFFERED)

        /** The final transcript of each session, for home to write. */
        val transcripts: Flow<String> = spoken.receiveAsFlow()

        private var session: Job? = null
        private var released = false

        /** The session ended by itself (silence) while the finger is still down: sent on release. */
        private var ended: String? = null

        fun onStart() {
            if (!speech.available()) {
                show(VoiceNotice.Unavailable)
                return
            }
            session?.cancel()
            released = false
            ended = null
            heard.value = ""
            session =
                viewModelScope.launch {
                    var final: String? = null
                    speech.listen().collect { event ->
                        when (event) {
                            is Heard.Partial -> heard.value = event.text
                            is Heard.Final -> final = event.text
                            Heard.Failed -> final = ""
                        }
                    }
                    val text = (final?.takeIf { it.isNotBlank() } ?: heard.value).orEmpty().trim()
                    if (released) finish(text) else ended = text
                }
        }

        /** The finger was lifted, or slid left ([cancelled]). */
        fun onEnd(cancelled: Boolean) {
            if (heard.value == null) return
            if (cancelled) {
                session?.cancel()
                heard.value = null
                return
            }
            released = true
            val done = ended
            if (done != null) finish(done) else speech.stop()
        }

        /** The microphone permission was refused. */
        fun onPermissionDenied() = show(VoiceNotice.NoPermission)

        private fun finish(text: String) {
            heard.value = null
            ended = null
            if (text.isBlank()) show(VoiceNotice.NotHeard) else spoken.trySend(text)
        }

        private fun show(notice: VoiceNotice) {
            shown.value = notice
            viewModelScope.launch {
                delay(DurtMotion.HINT_BUBBLE_MS)
                if (shown.value == notice) shown.value = null
            }
        }
    }
