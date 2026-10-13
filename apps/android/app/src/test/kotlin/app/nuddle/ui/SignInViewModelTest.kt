package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.data.InvitePreview
import app.nuddle.data.SendOtpOutcome
import app.nuddle.data.VerifyOutcome
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeAuthRepository
import app.nuddle.testing.FakeInvitePreviews
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingStarter
import app.nuddle.ui.screens.code.CodeViewModel
import app.nuddle.ui.screens.code.SignedInDestination
import app.nuddle.ui.screens.name.NameUiState
import app.nuddle.ui.screens.name.NameViewModel
import app.nuddle.ui.screens.phone.PhoneViewModel
import app.nuddle.ui.screens.welcome.WelcomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SignInViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val starter = RecordingStarter()

    private val istanbul = TimeZone.of("Europe/Istanbul")

    /** 21:15 in Istanbul: the end of the wait in the 6c tests. */
    private val waitEnds = Instant.parse("2026-10-11T21:15:00+03:00")

    private fun codeViewModel() =
        CodeViewModel(SavedStateHandle(mapOf(CodeViewModel.ARG_PHONE to Cast.EREN_PHONE)), auth, starter, istanbul)

    @Test
    fun `phone screen sends the code only for a valid mobile number`() =
        runTest(main.dispatcher) {
            val vm = PhoneViewModel(auth, istanbul)
            vm.onNumberChange("0532 000 00")
            vm.onSend()
            assertTrue(auth.sentTo.isEmpty())

            vm.codeSent.test {
                vm.onNumberChange("0532 000 00 02")
                assertEquals("5320000002", vm.state.value.national)
                vm.onSend()
                assertEquals(Cast.EREN_PHONE, awaitItem())
            }
            assertEquals(listOf(Cast.EREN_PHONE), auth.sentTo)
        }

    @Test
    fun `phone screen shows the connection error and keeps the number`() =
        runTest(main.dispatcher) {
            auth.sendOutcome = SendOtpOutcome.Failed
            val vm = PhoneViewModel(auth, istanbul)
            vm.onNumberChange("5320000002")
            vm.onSend()
            assertTrue(vm.state.value.failed)
            assertEquals("5320000002", vm.state.value.national)
        }

    @Test
    fun `a registered person goes home and the session starts`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel()
            vm.signedIn.test {
                vm.onComplete("123456")
                assertEquals(SignedInDestination.Home, awaitItem())
            }
            assertEquals(listOf(Cast.EREN_PHONE to "123456"), auth.verified)
            assertEquals(1, starter.starts)
        }

    @Test
    fun `a new person goes to the name screen`() =
        runTest(main.dispatcher) {
            auth.verifyOutcome = VerifyOutcome.SignedIn(registered = false)
            val vm = codeViewModel()
            vm.signedIn.test {
                vm.onComplete("123456")
                assertEquals(SignedInDestination.Name, awaitItem())
            }
            assertEquals(0, starter.starts)
        }

    @Test
    fun `a wrong code shows 6c and typing again clears it`() =
        runTest(main.dispatcher) {
            auth.verifyOutcome = VerifyOutcome.WrongCode(remaining = 2)
            val vm = codeViewModel()
            vm.onComplete("000000")
            assertTrue(vm.state.value.wrongCode)
            assertEquals(2, vm.state.value.remainingAttempts)
            assertFalse(vm.state.value.verifying)
            vm.onCodeChange("1")
            assertFalse(vm.state.value.wrongCode)
        }

    @Test
    fun `the last wrong code starts the wait, no new code is asked for until it ends (6c)`() =
        runTest(main.dispatcher) {
            auth.verifyOutcome = VerifyOutcome.Locked(waitEnds)
            val vm = codeViewModel()
            vm.onComplete("000000")
            assertEquals("21:15", vm.state.value.lockedUntil)
            assertTrue(vm.state.value.wrongCode)
            assertEquals(0, vm.state.value.resendInSeconds)

            vm.onResend()
            assertTrue(auth.sentTo.isEmpty())
        }

    @Test
    fun `phone screen says when a number waiting after wrong codes can ask again`() =
        runTest(main.dispatcher) {
            auth.sendOutcome = SendOtpOutcome.Locked(waitEnds)
            val vm = PhoneViewModel(auth, istanbul)
            vm.codeSent.test {
                vm.onNumberChange("5320000002")
                vm.onSend()
                assertEquals("21:15", vm.state.value.lockedUntil)
                assertFalse(vm.state.value.failed)
                expectNoEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `a new code can be asked for after the one minute wait`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel()
            assertEquals(60, vm.state.value.resendInSeconds)
            vm.onResend()
            assertTrue(auth.sentTo.isEmpty())

            advanceTimeBy(42.seconds)
            runCurrent()
            assertEquals(18, vm.state.value.resendInSeconds)
            advanceTimeBy(18.seconds)
            runCurrent()
            assertEquals(0, vm.state.value.resendInSeconds)

            vm.onResend()
            assertEquals(listOf(Cast.EREN_PHONE), auth.sentTo)
            assertEquals(60, vm.state.value.resendInSeconds)
        }

    @Test
    fun `the name screen registers the trimmed name, then the session starts`() =
        runTest(main.dispatcher) {
            val vm = NameViewModel(auth, starter, FakeInvitePreviews())
            vm.onNameChange("   ")
            vm.onContinue()
            assertTrue(auth.registeredNames.isEmpty())

            vm.registered.test {
                vm.onNameChange(" Eren ")
                vm.onContinue()
                awaitItem()
            }
            assertEquals(listOf("Eren"), auth.registeredNames)
            assertEquals(1, starter.starts)
        }

    @Test
    fun `an invite link opened while signed out shows the inviter on welcome, then prefills the name`() =
        runTest(main.dispatcher) {
            val previews = FakeInvitePreviews(mapOf("abc" to InvitePreview(inviterName = "Selin", prefilledName = "Eren")))
            val welcome = WelcomeViewModel(previews)
            assertNull(welcome.state.value.inviterName)

            welcome.onInviteLink("abc")
            assertEquals("Selin", welcome.state.value.inviterName)

            val name = NameViewModel(auth, starter, previews)
            assertEquals(NameUiState(name = "Eren", invitedBy = "Selin", invitedAs = "Eren"), name.state.value)
            name.onNameChange("Eren Can")
            assertEquals("Eren", name.state.value.invitedAs)
        }

    @Test
    fun `a link whose invite is not open keeps the plain welcome and an empty name`() =
        runTest(main.dispatcher) {
            val previews = FakeInvitePreviews()
            val welcome = WelcomeViewModel(previews)
            welcome.onInviteLink("gone")
            assertEquals(listOf("gone"), previews.loaded)
            assertNull(welcome.state.value.inviterName)
            assertEquals(NameUiState(), NameViewModel(auth, starter, previews).state.value)
        }

    @Test
    fun `a failed register keeps the name and shows the error`() =
        runTest(main.dispatcher) {
            auth.registerOk = false
            val vm = NameViewModel(auth, starter, FakeInvitePreviews())
            vm.onNameChange("Eren")
            vm.onContinue()
            assertTrue(vm.state.value.failed)
            assertFalse(vm.state.value.saving)
            assertEquals(0, starter.starts)
        }
}
