package app.nuddle.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speech of the `e2e` flavor (docs/testing.md, Android `e2e` flavor): what is "heard" is the text the orchestrator put
 * at `files/e2e-speech.txt`, so a flow can speak on an emulator without a microphone. Only bound when
 * `BuildConfig.INJECTED_SPEECH` is set. Like the real recognizer after the person goes quiet (NUD-48), it ends by
 * itself once the text is said; a tap on stop before that ends it too (NUD-60).
 */
@Singleton
class InjectedSpeechInput
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SpeechInput {
        private val stopped = MutableStateFlow(false)

        override fun available(): Boolean = true

        override fun listen(): Flow<Heard> =
            flow {
                stopped.value = false
                val text = withContext(Dispatchers.IO) { File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.trim() }.orEmpty()
                emit(Heard.Partial(text))
                withTimeoutOrNull(SILENCE_MS) { stopped.first { it } }
                emit(Heard.Final(text))
            }

        override fun stop() {
            stopped.value = true
        }

        companion object {
            const val FILE = "e2e-speech.txt"

            /** How long the injected speech "talks" before the silence that ends it. */
            const val SILENCE_MS = 1_500L
        }
    }
