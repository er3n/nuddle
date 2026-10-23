package app.nuddle.alarm

import android.app.Application
import android.app.NotificationManager
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.PokeLevel
import app.nuddle.testing.Cast
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** The task is said aloud only with the audio focus, never over a call (NUD-151) or on a silenced phone (NUD-164). */
@OptIn(ExperimentalCoroutinesApi::class) // Reason: virtual time drives the voice's 3 and 10 second pauses.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PokeSpeechCallTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val audio = app.getSystemService(AudioManager::class.java)
    private val toSelf = aTask().copy(senderId = Cast.EREN_ID, finishMode = "done")

    private fun TestScope.speech() = AndroidPokeSpeech(app, InMemoryConnectionDao(), backgroundScope, Diagnostics.None)

    /** The engine starts on the first round; Robolectric leaves its init to the test. */
    private fun TestScope.firstRound(): TextToSpeech {
        ShadowTextToSpeech.addLanguageAvailability(Locale.forLanguageTag("tr-TR"))
        advanceTimeBy(3.seconds + 1.seconds)
        runCurrent()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        return tts
    }

    @Test
    fun `with the focus the task is said`() =
        runTest(dispatcher) {
            speech().start(toSelf, PokeLevel.SOUND) { true }

            val tts = firstRound()

            assertEquals(toSelf.title, shadowOf(tts).lastSpokenText)
        }

    @Test
    fun `a call that refuses the focus hears nothing`() =
        runTest(dispatcher) {
            shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
            speech().start(toSelf, PokeLevel.SOUND) { true }

            val tts = firstRound()

            assertNull(shadowOf(tts).lastSpokenText)
        }

    @Test
    fun `on silent or vibrate the poke rings but the task is not said (NUD-164)`() =
        runTest(dispatcher) {
            audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            speech().start(toSelf, PokeLevel.SOUND) { true }

            val tts = firstRound()

            assertNull(shadowOf(tts).lastSpokenText)
        }

    @Test
    fun `with Do Not Disturb on the task is not said (NUD-164)`() =
        runTest(dispatcher) {
            val notifications = app.getSystemService(NotificationManager::class.java)
            notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            try {
                speech().start(toSelf, PokeLevel.SOUND) { true }

                val tts = firstRound()

                assertNull(shadowOf(tts).lastSpokenText)
            } finally {
                notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }

    @Test
    fun `in a call the task is not said even when the focus is granted`() =
        runTest(dispatcher) {
            audio.mode = AudioManager.MODE_IN_CALL
            speech().start(toSelf, PokeLevel.SOUND) { true }

            val tts = firstRound()

            assertNull(shadowOf(tts).lastSpokenText)
        }
}
