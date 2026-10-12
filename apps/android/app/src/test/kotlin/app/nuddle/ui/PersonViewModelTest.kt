package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.ConnectedFor
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeAccountRepository
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aTask
import app.nuddle.testing.selinConnection
import app.nuddle.ui.screens.person.PersonSheet
import app.nuddle.ui.screens.person.PersonViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Person detail (5b). */
class PersonViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val connections = InMemoryConnectionDao()
    private val tasks = InMemoryTaskDao()
    private val account = FakeAccountRepository()
    private val clock = FakeClock(Instant.parse("2026-10-12T15:00:00+03:00"))
    private val vm by lazy {
        PersonViewModel(
            SavedStateHandle(mapOf(PersonViewModel.ARG_PERSON to Cast.SELIN_ID)),
            connections,
            tasks,
            account,
            IdGenerator(clock),
            clock,
            TimeZone.of("Europe/Istanbul"),
        )
    }

    private suspend fun connected() =
        connections.upsert(
            listOf(selinConnection().copy(connectedAt = Instant.parse("2026-07-11T14:32:00+03:00").toEpochMilliseconds())),
        )

    private fun TestScope.watch() = backgroundScope.launch { vm.state.collect {} }

    @Test
    fun `shows since when, the relationship, my switch and every task between us`() =
        runTest(main.dispatcher) {
            connected()
            tasks.upsert(
                listOf(
                    aTask(id = "to-me"),
                    aTask(id = "from-me").copy(senderId = Cast.EREN_ID, recipientId = Cast.SELIN_ID, status = "CANCELLED"),
                    aTask(id = "self").copy(senderId = Cast.EREN_ID, recipientId = Cast.EREN_ID),
                ),
            )
            watch()
            val state = vm.state.value!!
            assertEquals("Selin", state.name)
            assertEquals(EdgeType.SPOUSE, state.relationship)
            assertEquals(ConnectedFor.Months(3), state.since)
            assertTrue(state.notifyMe)
            assertEquals(2, state.tasks)
        }

    @Test
    fun `a connection that is not active shows nothing`() =
        runTest(main.dispatcher) {
            connections.upsert(listOf(selinConnection().copy(status = "removed")))
            watch()
            assertNull(vm.state.value)
        }

    @Test
    fun `the switch shows at once and goes back with the alert when it did not go`() =
        runTest(main.dispatcher) {
            connected()
            watch()
            val gate = CompletableDeferred<Unit>().also { account.gate = it }
            account.ok = false
            vm.onNotifyMe(false)
            assertFalse(vm.state.value!!.notifyMe)
            gate.complete(Unit)
            assertTrue(vm.state.value!!.notifyMe)
            assertTrue(vm.state.value!!.failed)
            assertEquals(listOf("notifyMe ${Cast.SELIN_ID} false"), account.calls)
        }

    @Test
    fun `İlişki starts from the current one and Kaydet sends the new one`() =
        runTest(main.dispatcher) {
            connected()
            watch()
            vm.onRelationship()
            assertEquals(PersonSheet.Relationship(EdgeType.SPOUSE), vm.state.value!!.sheet)
            vm.onSelect(EdgeType.ROOMMATE)
            vm.onConfirm()
            assertEquals(listOf("relationship ${Cast.SELIN_ID} roommate"), account.calls)
            assertNull(vm.state.value!!.sheet)
        }

    @Test
    fun `removing asks first, goes back once done, and a retry reuses the key`() =
        runTest(main.dispatcher) {
            connected()
            watch()
            vm.onRemove()
            account.ok = false
            vm.onConfirm()
            assertEquals(PersonSheet.Remove(failed = true), vm.state.value!!.sheet)
            account.ok = true
            vm.closed.test {
                vm.onConfirm()
                awaitItem()
            }
            val (first, second) = account.calls
            assertTrue(first.startsWith("remove ${Cast.SELIN_ID} "))
            assertEquals(first, second)
        }
}
