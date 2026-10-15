package app.nuddle.ui

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.nuddle.data.Delivery
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
import app.nuddle.data.db.TaskEventEntity
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeConnectivity
import app.nuddle.testing.FakePermissions
import app.nuddle.testing.FakePhotoLoader
import app.nuddle.testing.FakePhotoUploads
import app.nuddle.testing.FakeTaskRepository
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryInviteDao
import app.nuddle.testing.InMemoryQueuedWriteDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemorySetupStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.InMemoryTaskEventDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingErrorReporter
import app.nuddle.testing.RecordingTaskAcks
import app.nuddle.testing.aSession
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.HomeTab
import app.nuddle.ui.screens.home.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** NUD-68 on home: the last hour counts down, my sent rows carry the tick, and Bana tells the senders "gördü". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeDeliveryTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val taskDao = InMemoryTaskDao()
    private val connectionDao = InMemoryConnectionDao()
    private val events = InMemoryTaskEventDao()
    private val acks = RecordingTaskAcks()
    private val ticks = MutableStateFlow(0)
    private val vm by lazy {
        HomeViewModel(
            sessions,
            taskDao,
            InMemoryInviteDao(),
            connectionDao,
            FakeTaskRepository(),
            IdGenerator(clock),
            SetupGate(InMemorySetupStore(), FakePermissions()),
            DoneNotes(),
            FakePhotoLoader(),
            FakePhotoUploads(),
            FakeConnectivity(),
            InMemoryQueuedWriteDao(),
            RecordingErrorReporter(),
            clock,
            istanbul,
            events,
            { ticks.map { } },
            acks,
        )
    }

    private suspend fun signedIn() = sessions.saveMe("Eren", QuietHours.default(istanbul))

    @Test
    fun `the last hour counts down on home and moves with each minute (NUD-68)`() =
        runTest(main.dispatcher) {
            signedIn()
            connectionDao.upsert(listOf(sibelConnection()))
            val soon = Instant.parse("2026-10-11T20:35:00+03:00")
            taskDao.upsert(listOf(aTask(deadline = soon).copy(finishMode = "done"), aTask(id = "later").copy(finishMode = "done")))
            vm.state.test {
                val home = awaitItemWhere { it?.nextUp != null && it.later.isNotEmpty() }
                assertEquals(DeadlineText.Today("20:35", minutesLeft = 35), home?.nextUp?.deadline)
                assertNull(
                    home
                        ?.later
                        ?.single()
                        ?.deadline
                        ?.minutesLeft,
                    "more than an hour away: the clock time alone",
                )
                clock.advance(10.minutes)
                ticks.value++
                assertEquals(25, awaitItemWhere { it?.nextUp?.deadline?.minutesLeft == 25 }?.nextUp?.deadline?.minutesLeft)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `my task to Sibel shows reached, then seen (NUD-68)`() =
        runTest(main.dispatcher) {
            signedIn()
            connectionDao.upsert(listOf(sibelConnection()))
            taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)))
            vm.state.test {
                assertEquals(Delivery.Sent, awaitItemWhere { it?.sent?.isNotEmpty() == true }?.sent?.single()?.delivery)
                events.upsert(listOf(TaskEventEntity("e1", Cast.TASK_ID, Cast.SIBEL_ID, "delivered", "{}", 1)))
                assertEquals(Delivery.Reached, awaitItemWhere { it?.sent?.single()?.delivery != Delivery.Sent }?.sent?.single()?.delivery)
                events.upsert(listOf(TaskEventEntity("e2", Cast.TASK_ID, Cast.SIBEL_ID, "seen", "{}", 2)))
                assertEquals(Delivery.Seen, awaitItemWhere { it?.sent?.single()?.delivery == Delivery.Seen }?.sent?.single()?.delivery)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tasks from others on Bana are told as seen while home is shown, not on Gönderdiklerim or to self (NUD-68)`() =
        runTest(main.dispatcher) {
            signedIn()
            connectionDao.upsert(listOf(sibelConnection()))
            val due = aTask().deadline
            taskDao.upsert(
                listOf(
                    aTask().copy(id = "first", finishMode = "done", deadline = due),
                    aTask().copy(id = "second", finishMode = "done", deadline = due + 60_000),
                    aTask().copy(id = "gym", senderId = Cast.EREN_ID, deadline = due + 120_000),
                ),
            )
            vm.onTab(HomeTab.Sent)
            vm.state.test {
                awaitItemWhere { it?.later?.size == 2 }
                assertEquals(emptyList(), acks.seen)
                vm.onTab(HomeTab.Mine)
                awaitItemWhere { it?.tab == HomeTab.Mine }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(setOf("first", "second"), acks.seen.toSet())
        }

    private suspend fun <T> ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
