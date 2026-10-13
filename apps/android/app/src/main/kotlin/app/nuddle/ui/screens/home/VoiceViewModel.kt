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
 * Tap the mic to dictate (4h, NUD-48): the live transcript while listening, then the final text goes into the compose
 * box, where the person fixes it and sends it (open-questions#17, #40). A second tap or silence ends a session. Nothing
 * heard, no permission or no recognizer on the phone shows a short note by the mic (open-questions#40).
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

        /** The final transcript of each session, for home to put into the compose box. */
        val transcripts: Flow<String> = spoken.receiveAsFlow()

        private var session: Job? = null

        /** A tap on the mic. */
        fun onStart() {
            if (!speech.available()) {
                show(VoiceNotice.Unavailable)
                return
            }
            session?.cancel()
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
                    finish((final?.takeIf { it.isNotBlank() } ?: heard.value).orEmpty().trim())
                }
        }

        /** The second tap stops listening; the final text follows. [cancelled] drops what was heard. */
        fun onEnd(cancelled: Boolean) {
            if (heard.value == null) return
            if (cancelled) {
                session?.cancel()
                heard.value = null
                return
            }
            speech.stop()
        }

        /** The microphone permission was refused. */
        fun onPermissionDenied() = show(VoiceNotice.NoPermission)

        private fun finish(text: String) {
            heard.value = null
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
