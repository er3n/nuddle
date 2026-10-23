package app.nuddle.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.nuddle.R
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.di.ApplicationScope
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeUtterance
import app.nuddle.domain.PokeUtterances
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/** Says the task aloud while a poke rings (NUD-124, docs/ux/platform.md, Task read aloud). */
interface PokeSpeech {
    /** Starts speaking [task] for a poke at [level] while [ringing] holds; replaces what was said for it before. */
    fun start(
        task: TaskEntity,
        level: PokeLevel,
        ringing: () -> Boolean,
    )

    fun stop(taskId: String)

    /** Says nothing: tests that build the notifications without caring about the voice. */
    object None : PokeSpeech {
        override fun start(
            task: TaskEntity,
            level: PokeLevel,
            ringing: () -> Boolean,
        ) = Unit

        override fun stop(taskId: String) = Unit
    }
}

/**
 * Android `TextToSpeech` on the alarm stream with a Turkish voice. The tone stays the channel's and keeps ringing; the
 * voice asks for transient focus that may duck it. No Turkish voice or no engine: nothing is said, the tone rings alone.
 * Diagnostics get only fixed words and the opaque task id, never the spoken text (ADR 0023).
 */
@Singleton
class AndroidPokeSpeech
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val connections: ConnectionDao,
        @param:ApplicationScope private val scope: CoroutineScope,
        private val diagnostics: Diagnostics,
    ) : PokeSpeech {
        private val jobs = ConcurrentHashMap<String, Job>()
        private val engineLock = Mutex()
        private val spoken = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        private var engine: TextToSpeech? = null

        override fun start(
            task: TaskEntity,
            level: PokeLevel,
            ringing: () -> Boolean,
        ) {
            jobs.remove(task.id)?.cancel()
            // TODO(open-questions#56): on for everyone, no switch, although a shared room hears the task.
            val toSelf = task.senderId == task.recipientId
            val job =
                scope.launch {
                    val sender = if (toSelf) null else connections.get(task.senderId)?.displayName
                    val utterance = PokeUtterances.of(level, task.title, toSelf, sender) ?: return@launch
                    val text =
                        when (utterance) {
                            is PokeUtterance.FromSender -> context.getString(R.string.poke_speech_from, utterance.sender, utterance.task)
                            is PokeUtterance.TaskOnly -> utterance.task
                        }
                    repeat(TIMES) { round ->
                        delay(if (round == 0) FIRST_AFTER else BETWEEN)
                        if (!ringing()) return@launch
                        val tts = engine() ?: return@launch
                        say(tts, text)
                    }
                }
            jobs[task.id] = job
            job.invokeOnCompletion {
                jobs.remove(task.id, job)
                if (jobs.isEmpty()) scope.launch { release() }
            }
        }

        override fun stop(taskId: String) {
            jobs.remove(taskId)?.cancel()
        }

        /** The engine with a Turkish voice, started once and kept while a poke speaks; null without one. */
        private suspend fun engine(): TextToSpeech? =
            engineLock.withLock {
                engine?.let { return@withLock it }
                val started = withContext(Dispatchers.Main) { withTimeoutOrNull(START_TIMEOUT) { startEngine() } }
                if (started == null) {
                    diagnostics.log("speech no engine")
                    return@withLock null
                }
                if (started.isLanguageAvailable(TURKISH) < TextToSpeech.LANG_AVAILABLE) {
                    diagnostics.log("speech no turkish voice")
                    started.shutdown()
                    return@withLock null
                }
                started.setLanguage(TURKISH)
                started.setAudioAttributes(ATTRIBUTES)
                started.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) = Unit

                        override fun onDone(utteranceId: String) {
                            spoken.remove(utteranceId)?.complete(Unit)
                        }

                        @Deprecated("Deprecated in the platform, still called on older engines.")
                        override fun onError(utteranceId: String) {
                            spoken.remove(utteranceId)?.complete(Unit)
                        }

                        override fun onError(
                            utteranceId: String,
                            errorCode: Int,
                        ) {
                            spoken.remove(utteranceId)?.complete(Unit)
                        }
                    },
                )
                engine = started
                started
            }

        private suspend fun startEngine(): TextToSpeech? =
            suspendCancellableCoroutine { continuation ->
                var created: TextToSpeech? = null
                // onInit comes later on the main thread, after the constructor has returned.
                created =
                    TextToSpeech(context) { status ->
                        val tts = created
                        if (status == TextToSpeech.SUCCESS && tts != null) {
                            continuation.resume(tts)
                        } else {
                            tts?.shutdown()
                            continuation.resume(null)
                        }
                    }
                continuation.invokeOnCancellation { created.shutdown() }
            }

        private suspend fun say(
            tts: TextToSpeech,
            text: String,
        ) {
            val audio = context.getSystemService(AudioManager::class.java)
            // The person silenced the phone (silent, vibrate or Do Not Disturb): the tone still rings as an alarm, but
            // the task is not said out loud where they asked for quiet (NUD-164).
            if (audio != null && silenced(audio)) {
                diagnostics.log("speech skipped silenced")
                return
            }
            val focus =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(ATTRIBUTES)
                    .build()
            // A phone call (or a voice chat) holds the focus and refuses it: the task is not said over the call, this
            // round is skipped and the tone rings as the system allows during a call (NUD-151).
            if (audio != null && (audio.inCall() || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)) {
                diagnostics.log("speech skipped in call")
                return
            }
            val id = UUID.randomUUID().toString()
            val done = CompletableDeferred<Unit>().also { spoken[id] = it }
            try {
                if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) {
                    diagnostics.log("speech refused")
                    return
                }
                withTimeoutOrNull(SPEAK_TIMEOUT) { done.await() }
            } finally {
                spoken.remove(id)
                // Bitti, a sync or a swipe cancels the job: the voice stops with the tone.
                if (!done.isCompleted) tts.stop()
                audio?.abandonAudioFocusRequest(focus)
            }
        }

        private fun silenced(audio: AudioManager): Boolean {
            val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            val doNotDisturb =
                filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || doNotDisturb
        }

        private fun AudioManager.inCall() = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION

        private suspend fun release() =
            engineLock.withLock {
                if (jobs.isNotEmpty()) return@withLock
                engine?.shutdown()
                engine = null
            }

        private companion object {
            val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

            // On the alarm stream, so it follows the alarm volume a poke raises (NUD-68) and pierces Do Not Disturb.
            val ATTRIBUTES: AudioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

            // docs/ux/platform.md, Task read aloud: the tone first, then the task 3 times, 10 seconds apart.
            const val TIMES = 3
            val FIRST_AFTER = 3.seconds
            val BETWEEN = 10.seconds
            val START_TIMEOUT = 5.seconds
            val SPEAK_TIMEOUT = 15.seconds
        }
    }
