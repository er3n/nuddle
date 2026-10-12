package app.nuddle.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Android's SpeechRecognizer in Turkish, with partial results for the live transcript (4h). */
@Singleton
class AndroidSpeechInput
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SpeechInput {
        private var current: SpeechRecognizer? = null

        override fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

        override fun listen(): Flow<Heard> =
            callbackFlow {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                current = recognizer
                recognizer.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onPartialResults(partialResults: Bundle) {
                            partialResults.best()?.let { trySend(Heard.Partial(it)) }
                        }

                        override fun onResults(results: Bundle) {
                            trySend(Heard.Final(results.best().orEmpty()))
                            close()
                        }

                        override fun onError(error: Int) {
                            val nothingHeard = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            trySend(if (nothingHeard) Heard.Final("") else Heard.Failed)
                            close()
                        }

                        override fun onReadyForSpeech(params: Bundle?) = Unit

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(rmsdB: Float) = Unit

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() = Unit

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?,
                        ) = Unit
                    },
                )
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        // The UI is Turkish only for now (docs/glossary.md); English comes with the global launch.
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
                )
                awaitClose {
                    recognizer.destroy()
                    if (current === recognizer) current = null
                }
            }.flowOn(Dispatchers.Main.immediate)

        override fun stop() {
            current?.stopListening()
        }

        private fun Bundle.best(): String? = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

        private companion object {
            const val LANGUAGE = "tr-TR"
        }
    }
