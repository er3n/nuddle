package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.api.model.EdgeType
import app.nuddle.data.SetupGate
import app.nuddle.data.db.InviteEntity
import app.nuddle.domain.Ago
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeInviteRepository
import app.nuddle.testing.FakePermissions
import app.nuddle.testing.InMemoryInviteDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemorySetupStore
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aSession
import app.nuddle.ui.screens.consent.ConsentEffect
import app.nuddle.ui.screens.consent.ConsentViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Consent (docs/ux/screens.md 0f, 1d; docs/product.md, Connections). */
class ConsentViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock()
    private val inviteDao = InMemoryInviteDao()
    private val invites = FakeInviteRepository()
    private val permissions = FakePermissions()

    private fun vm(fromBell: Boolean = false) =
        ConsentViewModel(
            SavedStateHandle(mapOf(ConsentViewModel.ARG_INVITE to "i1", ConsentViewModel.ARG_FROM_BELL to fromBell)),
            inviteDao,
            InMemorySessionStore(aSession()),
            invites,
            SetupGate(InMemorySetupStore(), permissions),
            IdGenerator(clock),
            clock,
            TimeZone.of("Europe/Istanbul"),
        )

    private suspend fun received(
        relationship: String = "parent",
        status: String = "open",
    ) = inviteDao.upsert(
        listOf(
            InviteEntity(
                id = "i1",
                code = "abc",
                sent = false,
                name = "Sibel",
                phone = null,
                inviterId = Cast.SIBEL_ID,
                prefilledName = "Eren",
                relationship = relationship,
                status = status,
                createdAt = (clock.now - 2.hours).toEpochMilliseconds(),
            ),
        ),
    )

    private fun TestScope.collect(vm: ConsentViewModel) = backgroundScope.launch { vm.state.collect {} }

    @Test
    fun `my side starts as the inverse of the inviter's pick and Değiştir changes it`() =
        runTest(main.dispatcher) {
            received(relationship = "parent")
            val vm = vm()
            collect(vm)
            val state = vm.state.value!!
            assertEquals("Sibel", state.inviterName)
            assertEquals(EdgeType.PARENT, state.addedAs)
            assertEquals(EdgeType.CHILD, state.myPick, "I am Sibel's parent, so Sibel is my child")
            assertEquals("23:00" to "07:00", state.quietStart to state.quietEnd)
            assertEquals(Ago.Hours(2), state.ago)

            vm.onChangeRelationship()
            assertTrue(vm.state.value!!.picking)
            vm.onPick(EdgeType.OTHER)
            assertEquals(EdgeType.OTHER, vm.state.value!!.myPick)
        }

    @Test
    fun `İzin ver accepts with my pick, then setup when a permission is missing`() =
        runTest(main.dispatcher) {
            received(relationship = "spouse")
            permissions.granted = GrantedPermissions(notifications = false, exactAlarm = true, fullScreen = true)
            val vm = vm()
            collect(vm)
            vm.effects.test {
                vm.onAllow()
                assertEquals(ConsentEffect.Accepted("Sibel", setupNeeded = true), awaitItem())
            }
            assertEquals(listOf("abc" to EdgeType.SPOUSE), invites.accepted)
        }

    @Test
    fun `a failed answer stays on the screen and says so`() =
        runTest(main.dispatcher) {
            received()
            invites.answerOk = false
            val vm = vm(fromBell = true)
            collect(vm)
            vm.onDecline()
            assertTrue(vm.state.value!!.failed)
            assertEquals(emptyList(), invites.declined)
        }

    @Test
    fun `Reddet declines and closes, Şimdi değil only closes`() =
        runTest(main.dispatcher) {
            received()
            val vm = vm(fromBell = true)
            collect(vm)
            vm.effects.test {
                vm.onDecline()
                assertEquals(ConsentEffect.Closed, awaitItem())
                vm.onLater()
                assertEquals(ConsentEffect.Closed, awaitItem())
            }
            assertEquals(listOf("abc"), invites.declined)
        }

    @Test
    fun `an invite that is no longer open cannot be accepted`() =
        runTest(main.dispatcher) {
            received(status = "revoked")
            val vm = vm()
            collect(vm)
            assertTrue(vm.state.value!!.gone)
            vm.onAllow()
            assertEquals(emptyList(), invites.accepted)
        }
}
