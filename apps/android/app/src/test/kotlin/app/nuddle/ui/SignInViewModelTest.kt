package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.data.CodeChannel
import app.nuddle.data.InvitePreview
import app.nuddle.data.SendOtpOutcome
import app.nuddle.data.SmsResendOutcome
import app.nuddle.data.VerifyOutcome
import app.nuddle.data.sync.Syncer
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeAuthRepository
import app.nuddle.testing.FakeInvitePreviews
import app.nuddle.testing.FakeWhatsAppOtp
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingStarter
import app.nuddle.ui.screens.code.CodeViewModel
import app.nuddle.ui.screens.code.SignedInDestination
import app.nuddle.ui.screens.name.NameUiState
import app.nuddle.ui.screens.name.NameViewModel
import app.nuddle.ui.screens.phone.CodeSent
import app.nuddle.ui.screens.phone.PhoneViewModel
import app.nuddle.ui.screens.welcome.WelcomeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
    private val whatsApp = FakeWhatsAppOtp()
    private val starter = RecordingStarter()
    private var syncs = 0
    private var sync = Syncer { true }
    private val syncer =
        Syncer {
            syncs++
            sync.sync()
        }

    private val istanbul = TimeZone.of("Europe/Istanbul")

    /** 21:15 in Istanbul: the end of the wait in the 6c tests. */
    private val waitEnds = Instant.parse("2026-10-11T21:15:00+03:00")

    private fun codeViewModel(channel: CodeChannel? = null) =
        CodeViewModel(
            SavedStateHandle(
                listOfNotNull(CodeViewModel.ARG_PHONE to Cast.EREN_PHONE, channel?.let { CodeViewModel.ARG_CHANNEL to it.name }).toMap(),
            ),
            auth,
            starter,
            syncer,
            istanbul,
            whatsApp,
        )

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
                assertEquals(CodeSent(Cast.EREN_PHONE, CodeChannel.Sms), awaitItem())
            }
            assertEquals(listOf(Cast.EREN_PHONE), auth.sentTo)
        }

    @Test
    fun `phone screen opens the code screen in the channel the code went by (ADR 0029)`() =
        runTest(main.dispatcher) {
            auth.sendOutcome = SendOtpOutcome.Sent(CodeChannel.WhatsApp)
            val vm = PhoneViewModel(auth, istanbul)
            vm.codeSent.test {
                vm.onNumberChange("5320000002")
                vm.onSend()
                assertEquals(CodeSent(Cast.EREN_PHONE, CodeChannel.WhatsApp), awaitItem())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `an SMS code shows the SMS variant and never offers SMS ile gönder`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel(CodeChannel.Sms)
            assertEquals(CodeChannel.Sms, vm.state.value.channel)
            advanceTimeBy(30.seconds)
            assertFalse(vm.state.value.smsOffered)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `a WhatsApp code shows the WhatsApp variant, SMS ile gönder comes after 5 seconds (open-questions 66)`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel(CodeChannel.WhatsApp)
            assertEquals(CodeChannel.WhatsApp, vm.state.value.channel)
            advanceTimeBy(CodeViewModel.SMS_OFFER_AFTER - 1.seconds)
            runCurrent()
            assertFalse(vm.state.value.smsOffered)
            vm.onSendSms()
            assertTrue(auth.smsResentTo.isEmpty())
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(vm.state.value.smsOffered)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `SMS ile gönder sends the same code by SMS, switches to the SMS variant and the SMS reader listens again`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel(CodeChannel.WhatsApp)
            advanceTimeBy(CodeViewModel.SMS_OFFER_AFTER)
            runCurrent()
            val listened = vm.codesSent.value
            vm.onSendSms()
            assertEquals(listOf(Cast.EREN_PHONE), auth.smsResentTo)
            assertEquals(CodeChannel.Sms, vm.state.value.channel)
            assertTrue(vm.state.value.alsoSentBySms)
            assertFalse(vm.state.value.smsOffered)
            assertEquals(listened + 1, vm.codesSent.value)
            assertTrue(auth.sentTo.isEmpty())
            assertTrue(vm.state.value.resendInSeconds > 0)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `no code to send by SMS hides the button and the countdown goes on`() =
        runTest(main.dispatcher) {
            auth.smsResendOutcome = SmsResendOutcome.Unavailable
            val vm = codeViewModel(CodeChannel.WhatsApp)
            advanceTimeBy(CodeViewModel.SMS_OFFER_AFTER)
            runCurrent()
            val left = vm.state.value.resendInSeconds
            vm.onSendSms()
            assertFalse(vm.state.value.smsOffered)
            assertEquals(CodeChannel.WhatsApp, vm.state.value.channel)
            assertFalse(vm.state.value.alsoSentBySms)
            assertFalse(vm.state.value.unreachable)
            assertEquals(left, vm.state.value.resendInSeconds)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(left - 1, vm.state.value.resendInSeconds)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `an unreachable server keeps SMS ile gönder and shows the connection error`() =
        runTest(main.dispatcher) {
            auth.smsResendOutcome = SmsResendOutcome.Failed
            val vm = codeViewModel(CodeChannel.WhatsApp)
            advanceTimeBy(CodeViewModel.SMS_OFFER_AFTER)
            runCurrent()
            vm.onSendSms()
            assertTrue(vm.state.value.unreachable)
            assertTrue(vm.state.value.smsOffered)
            assertFalse(vm.state.value.sendingSms)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `a new code over WhatsApp starts the SMS offer wait again`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel(CodeChannel.Sms)
            advanceTimeBy(61.seconds)
            runCurrent()
            auth.sendOutcome = SendOtpOutcome.Sent(CodeChannel.WhatsApp)
            vm.onResend()
            assertEquals(CodeChannel.WhatsApp, vm.state.value.channel)
            assertFalse(vm.state.value.smsOffered)
            advanceTimeBy(CodeViewModel.SMS_OFFER_AFTER)
            runCurrent()
            assertTrue(vm.state.value.smsOffered)
        }

    @Test
    fun `a code from WhatsApp fills the boxes and signs in`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel(CodeChannel.WhatsApp)
            vm.signedIn.test {
                whatsApp.handOver("482913")
                assertEquals(SignedInDestination.Home, awaitItem())
            }
            assertEquals(listOf(Cast.EREN_PHONE to "482913"), auth.verified)
            assertEquals("482913", vm.state.value.code)
            assertEquals(listOf("482913"), whatsApp.taken)
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
            assertEquals(1, syncs)
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
            assertEquals(0, syncs)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: runCurrent lets sign-in reach the sync before it ends.
    @Test
    fun `home opens once the first sync is in, the code spinner stays until then (NUD-105)`() =
        runTest(main.dispatcher) {
            val synced = CompletableDeferred<Boolean>()
            sync = Syncer { synced.await() }
            val vm = codeViewModel()
            vm.signedIn.test {
                vm.onComplete("123456")
                runCurrent()
                expectNoEvents()
                assertTrue(vm.state.value.verifying)
                synced.complete(true)
                assertEquals(SignedInDestination.Home, awaitItem())
            }
            assertEquals(1, syncs)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy moves past the wait for a sync that does not answer.
    @Test
    fun `a sync that does not answer still opens home after the wait`() =
        runTest(main.dispatcher) {
            sync = Syncer { awaitCancellation() }
            val vm = codeViewModel()
            vm.signedIn.test {
                vm.onComplete("123456")
                advanceTimeBy(CodeViewModel.FIRST_SYNC_WAIT - 1.seconds)
                expectNoEvents()
                advanceTimeBy(2.seconds)
                assertEquals(SignedInDestination.Home, awaitItem())
            }
        }

    @Test
    fun `a failed sync opens home at once`() =
        runTest(main.dispatcher) {
            sync = Syncer { false }
            val vm = codeViewModel()
            vm.signedIn.test {
                vm.onComplete("123456")
                assertEquals(SignedInDestination.Home, awaitItem())
            }
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

    @Test
    fun `phone screen says when a new code can go over the daily SMS cap (ADR 0024)`() =
        runTest(main.dispatcher) {
            auth.sendOutcome = SendOtpOutcome.Limited(waitEnds)
            val vm = PhoneViewModel(auth, istanbul)
            vm.codeSent.test {
                vm.onNumberChange("5320000002")
                vm.onSend()
                assertEquals("21:15", vm.state.value.limitedUntil)
                assertFalse(vm.state.value.failed)
                expectNoEvents()
                vm.onNumberChange("5320000003")
                assertEquals(null, vm.state.value.limitedUntil)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `a resend over the daily SMS cap says when and asks no more, the sent code still works`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel()
            advanceTimeBy(61.seconds)
            auth.sendOutcome = SendOtpOutcome.Limited(waitEnds)
            vm.onResend()
            assertEquals("21:15", vm.state.value.limitedUntil)
            assertFalse(vm.state.value.unreachable)
            val asked = auth.sentTo.size
            vm.onResend()
            assertEquals(asked, auth.sentTo.size)
            vm.onCodeChange("1")
            assertEquals("21:15", vm.state.value.limitedUntil)
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

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `a new code makes the SMS reader listen again`() =
        runTest(main.dispatcher) {
            val vm = codeViewModel()
            assertEquals(0, vm.codesSent.value)
            advanceTimeBy(60.seconds)
            runCurrent()

            vm.onResend()
            assertEquals(1, vm.codesSent.value)
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
            val previews = FakeInvitePreviews(mapOf("abc" to InvitePreview(inviterName = "Sibel", prefilledName = "Eren")))
            val welcome = WelcomeViewModel(previews)
            assertNull(welcome.state.value.inviterName)

            welcome.onInviteLink("abc")
            assertEquals("Sibel", welcome.state.value.inviterName)

            val name = NameViewModel(auth, starter, previews)
            assertEquals(NameUiState(name = "Eren", invitedBy = "Sibel", invitedAs = "Eren"), name.state.value)
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
