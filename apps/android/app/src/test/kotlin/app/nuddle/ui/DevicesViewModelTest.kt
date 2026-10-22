package app.nuddle.ui

import app.nuddle.data.SessionsRepository
import app.nuddle.data.SignedInPhone
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.FakeClock
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.ui.screens.devices.DevicesViewModel
import app.nuddle.ui.screens.devices.PhoneRowUi
import app.nuddle.ui.screens.devices.PhonesList
import app.nuddle.ui.screens.devices.SignOutOthersSheet
import app.nuddle.ui.screens.home.WrittenText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Answers with [phones] (null: the list did not come) and records the keys sign-out was sent with. */
private class FakeSessions : SessionsRepository {
    var phones: List<SignedInPhone>? = emptyList()
    var signOutOk = true
    var gate: CompletableDeferred<Unit>? = null
    val keys = mutableListOf<String>()
    var loads = 0

    override suspend fun phones(): List<SignedInPhone>? {
        loads++
        return phones
    }

    override suspend fun signOutOthers(idempotencyKey: String): Boolean {
        keys += idempotencyKey
        gate?.await()
        return signOutOk
    }
}

/** 5e Cihazlarım (NUD-142). */
class DevicesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(Instant.parse("2026-10-23T15:00:00+03:00"))
    private val sessions = FakeSessions()
    private val pixel = SignedInPhone("Google Pixel 8", "0.1.7", Instant.parse("2026-10-23T14:32:00+03:00"), current = true)
    private val samsung = SignedInPhone("Samsung SM-S911B", "0.1.6", Instant.parse("2026-10-22T09:10:00+03:00"), current = false)

    private fun vm() = DevicesViewModel(sessions, IdGenerator(clock), clock, TimeZone.of("Europe/Istanbul"))

    @Test
    fun `lists my phones with when each was last seen`() =
        runTest(main.dispatcher) {
            sessions.phones = listOf(pixel, samsung)
            val vm = vm()
            assertEquals(
                PhonesList.Shown(
                    listOf(
                        PhoneRowUi("Google Pixel 8", "0.1.7", WrittenText.Today("14:32"), current = true),
                        PhoneRowUi("Samsung SM-S911B", "0.1.6", WrittenText.Yesterday("09:10"), current = false),
                    ),
                ),
                vm.state.value.list,
            )
            assertTrue(vm.state.value.canSignOutOthers)
        }

    @Test
    fun `with only this phone there is nothing to sign out`() =
        runTest(main.dispatcher) {
            sessions.phones = listOf(pixel)
            val vm = vm()
            assertFalse(vm.state.value.canSignOutOthers)
            vm.onSignOutOthers()
            assertNull(vm.state.value.sheet)
        }

    @Test
    fun `a list that did not come shows the alert, and Tekrar dene loads again`() =
        runTest(main.dispatcher) {
            sessions.phones = null
            val vm = vm()
            assertEquals(PhonesList.Failed, vm.state.value.list)
            sessions.phones = listOf(pixel)
            vm.onRetry()
            assertEquals(2, sessions.loads)
            assertTrue(vm.state.value.list is PhonesList.Shown)
        }

    @Test
    fun `sign out asks first, goes out once, then the list shows only this phone`() =
        runTest(main.dispatcher) {
            sessions.phones = listOf(pixel, samsung)
            val vm = vm()
            vm.onSignOutOthers()
            assertEquals(SignOutOthersSheet(), vm.state.value.sheet)
            assertTrue(sessions.keys.isEmpty())

            val gate = CompletableDeferred<Unit>()
            sessions.gate = gate
            vm.onConfirm()
            assertEquals(SignOutOthersSheet(busy = true), vm.state.value.sheet)
            // Not while it is on its way: no second command, no dismissing.
            vm.onConfirm()
            vm.onDismiss()
            assertEquals(1, sessions.keys.size)
            assertEquals(SignOutOthersSheet(busy = true), vm.state.value.sheet)

            sessions.phones = listOf(pixel)
            gate.complete(Unit)
            assertNull(vm.state.value.sheet)
            assertEquals(2, sessions.loads)
            assertFalse(vm.state.value.canSignOutOthers)
        }

    @Test
    fun `a sign-out that did not go keeps the sheet with the alert and retries with the same key`() =
        runTest(main.dispatcher) {
            sessions.phones = listOf(pixel, samsung)
            sessions.signOutOk = false
            val vm = vm()
            vm.onSignOutOthers()
            vm.onConfirm()
            assertEquals(SignOutOthersSheet(failed = true), vm.state.value.sheet)
            assertEquals(1, sessions.loads)

            sessions.signOutOk = true
            vm.onConfirm()
            assertNull(vm.state.value.sheet)
            assertEquals(2, sessions.keys.size)
            assertEquals(sessions.keys[0], sessions.keys[1])
        }

    @Test
    fun `Vazgeç closes the sheet and a new one takes a new key`() =
        runTest(main.dispatcher) {
            sessions.phones = listOf(pixel, samsung)
            sessions.signOutOk = false
            val vm = vm()
            vm.onSignOutOthers()
            vm.onConfirm()
            vm.onDismiss()
            assertNull(vm.state.value.sheet)
            vm.onSignOutOthers()
            vm.onConfirm()
            assertEquals(2, sessions.keys.toSet().size)
        }
}
