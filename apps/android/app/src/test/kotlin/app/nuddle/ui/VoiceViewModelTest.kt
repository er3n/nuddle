package app.nuddle.ui

import app.cash.turbine.test
import app.nuddle.data.voice.Heard
import app.nuddle.testing.FakeSpeechInput
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.ui.components.VoiceNotice
import app.nuddle.ui.screens.home.VoiceViewModel
import app.nuddle.ui.theme.DurtMotion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 4h: hold to speak, the live transcript, release to send, slide left to cancel (open-questions#6, #17, #40). */
class VoiceViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val speech = FakeSpeechInput()
    private val vm by lazy { VoiceViewModel(speech) }

    @Test
    fun `while holding the transcript grows, release sends the final text`() =
        runTest(main.dispatcher) {
            vm.transcripts.test {
                vm.onStart()
                assertEquals("", vm.listening.value)
                speech.hear(Heard.Partial("eren yarın"))
                assertEquals("eren yarın", vm.listening.value)

                vm.onEnd(cancelled = false)
                assertEquals(1, speech.stops)
                speech.hear(Heard.Final("eren yarın akşam çöpü atsın"))
                assertEquals("eren yarın akşam çöpü atsın", awaitItem())
                assertNull(vm.listening.value)
            }
        }

    @Test
    fun `sliding left cancels, nothing is sent`() =
        runTest(main.dispatcher) {
            vm.transcripts.test {
                vm.onStart()
                speech.hear(Heard.Partial("eren yarın"))
                vm.onEnd(cancelled = true)
                assertNull(vm.listening.value)
                assertEquals(0, speech.stops)
                expectNoEvents()
            }
        }

    @Test
    fun `a session that ends by silence while holding is sent on release`() =
        runTest(main.dispatcher) {
            vm.transcripts.test {
                vm.onStart()
                speech.hear(Heard.Final("çöpü at"))
                assertEquals("", vm.listening.value)
                expectNoEvents()

                vm.onEnd(cancelled = false)
                assertEquals("çöpü at", awaitItem())
                assertEquals(0, speech.stops)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `nothing heard shows a note for 2 s, nothing is sent`() =
        runTest(main.dispatcher) {
            vm.transcripts.test {
                vm.onStart()
                vm.onEnd(cancelled = false)
                speech.hear(Heard.Final(""))
                assertEquals(VoiceNotice.NotHeard, vm.notice.value)
                advanceTimeBy(DurtMotion.HINT_BUBBLE_MS + 1)
                assertNull(vm.notice.value)
                expectNoEvents()
            }
        }

    @Test
    fun `a failed recognizer keeps what was heard so far`() =
        runTest(main.dispatcher) {
            vm.transcripts.test {
                vm.onStart()
                speech.hear(Heard.Partial("çöpü at"))
                vm.onEnd(cancelled = false)
                speech.hear(Heard.Failed)
                assertEquals("çöpü at", awaitItem())
            }
        }

    @Test
    fun `a phone without speech recognition says so, a refused microphone too`() =
        runTest(main.dispatcher) {
            speech.canListen = false
            vm.onStart()
            assertEquals(VoiceNotice.Unavailable, vm.notice.value)
            assertNull(vm.listening.value)
            assertEquals(0, speech.sessions)

            vm.onPermissionDenied()
            assertEquals(VoiceNotice.NoPermission, vm.notice.value)
        }
}
