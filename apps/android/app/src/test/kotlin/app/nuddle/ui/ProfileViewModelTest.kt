package app.nuddle.ui

import app.cash.turbine.test
import app.nuddle.api.model.EdgeType
import app.nuddle.data.db.InviteEntity
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeAccountRepository
import app.nuddle.testing.FakeAuthRepository
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakePermissions
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryInviteDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.profile.PersonRowUi
import app.nuddle.ui.screens.profile.ProfileSheet
import app.nuddle.ui.screens.profile.ProfileViewModel
import app.nuddle.ui.screens.profile.QuietEdge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Profile and settings (5a, 7g, 7h, 7i). */
class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val sessions = InMemorySessionStore()
    private val connections = InMemoryConnectionDao()
    private val invites = InMemoryInviteDao()
    private val permissions = FakePermissions()
    private val account = FakeAccountRepository()
    private val auth = FakeAuthRepository()
    private val vm by lazy {
        ProfileViewModel(sessions, connections, invites, permissions, account, auth, IdGenerator(FakeClock()))
    }

    private suspend fun signedIn() = sessions.saveMe("Eren", QuietHours.default(TimeZone.of("Europe/Istanbul")))

    private fun kotlinx.coroutines.test.TestScope.watch() = backgroundScope.launch { vm.state.collect {} }

    @Test
    fun `shows me, active connections with their relationship, open invites and the quiet hours`() =
        runTest(main.dispatcher) {
            signedIn()
            connections.upsert(
                listOf(sibelConnection(), sibelConnection().copy(personId = "gone", displayName = "Ayşe", status = "removed")),
            )
            invites.upsert(
                listOf(
                    InviteEntity("i1", "c1", true, "Defne", "+905320000009", null, "Defne", "child", "open", 1),
                    InviteEntity("i2", "c2", true, "Mert", "+905320000008", null, "Mert", "other", "revoked", 1),
                ),
            )
            watch()
            val state = vm.state.value!!
            assertEquals("Eren" to "+90 532 123 45 67", state.name to state.phone)
            assertEquals(listOf(PersonRowUi(Cast.SIBEL_ID, "Sibel", EdgeType.SPOUSE)), state.people)
            assertEquals(listOf("Defne"), state.pending)
            assertEquals(LocalTime(23, 0) to LocalTime(7, 0), state.quietStart to state.quietEnd)
        }

    @Test
    fun `a switch shows at once, and goes back with the alert when it did not reach the server`() =
        runTest(main.dispatcher) {
            signedIn()
            watch()
            val gate = CompletableDeferred<Unit>().also { account.gate = it }
            account.ok = false
            vm.onUseConnections(false)
            assertFalse(vm.state.value!!.useConnectionsForMyTasks)
            gate.complete(Unit)
            val state = vm.state.value!!
            assertTrue(state.useConnectionsForMyTasks)
            assertTrue(state.failed)
            assertEquals(listOf("useConnections false"), account.calls)
        }

    @Test
    fun `permissions are read again on coming back from settings`() =
        runTest(main.dispatcher) {
            signedIn()
            permissions.granted = GrantedPermissions(notifications = true, exactAlarm = true, fullScreen = false)
            watch()
            assertFalse(
                vm.state.value!!
                    .permissions.fullScreen,
            )
            permissions.granted = GrantedPermissions(notifications = true, exactAlarm = true, fullScreen = true)
            vm.onResume()
            assertTrue(
                vm.state.value!!
                    .permissions.fullScreen,
            )
        }

    @Test
    fun `quiet hours are set one box at a time, and Kaydet sends both and closes`() =
        runTest(main.dispatcher) {
            signedIn()
            watch()
            vm.onQuietHours()
            vm.sheets.onQuietTime(LocalTime(22, 30))
            vm.sheets.onQuietEdge(QuietEdge.END)
            vm.sheets.onQuietTime(LocalTime(6, 30))
            assertEquals(ProfileSheet.Quiet(LocalTime(22, 30), LocalTime(6, 30), QuietEdge.END), vm.state.value!!.sheet)
            vm.sheets.onConfirm()
            assertEquals(listOf("quiet 22:30-06:30"), account.calls)
            assertNull(vm.state.value!!.sheet)
        }

    @Test
    fun `a failed save keeps the sheet open with the alert`() =
        runTest(main.dispatcher) {
            signedIn()
            watch()
            auth.registerOk = false
            vm.onEditName()
            vm.sheets.onNameChange("  Eren Ö ")
            vm.sheets.onConfirm()
            assertEquals(listOf("Eren Ö"), auth.registeredNames)
            assertEquals(ProfileSheet.Rename("  Eren Ö ", failed = true), vm.state.value!!.sheet)
        }

    @Test
    fun `log out signs out only once the server said so, and a retry reuses the same key`() =
        runTest(main.dispatcher) {
            signedIn()
            watch()
            vm.onLogOut()
            account.ok = false
            vm.sheets.onConfirm()
            assertEquals(ProfileSheet.LogOut(failed = true), vm.state.value!!.sheet)
            account.ok = true
            vm.signedOut.test {
                vm.sheets.onConfirm()
                awaitItem()
            }
            val (first, second) = account.calls
            assertTrue(first.startsWith("logOut "))
            assertEquals(first, second)
        }

    @Test
    fun `a sheet on its way cannot be dismissed`() =
        runTest(main.dispatcher) {
            signedIn()
            watch()
            account.gate = CompletableDeferred()
            vm.onDelete()
            vm.sheets.onConfirm()
            vm.sheets.onDismiss()
            assertEquals(ProfileSheet.Delete(busy = true), vm.state.value!!.sheet)
        }
}
