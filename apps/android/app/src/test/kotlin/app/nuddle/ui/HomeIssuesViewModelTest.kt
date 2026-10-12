package app.nuddle.ui

import app.nuddle.data.WriteOutcome
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeTaskRepository
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.InMemoryTaskEventDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aSession
import app.nuddle.testing.aTask
import app.nuddle.testing.selinConnection
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.HomeIssuesViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The answers on home's 5d and 7e cards (docs/domain.md, Task states; open-questions#8, #9, #36). */
class HomeIssuesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-12T10:00:00+03:00"))
    private val taskDao = InMemoryTaskDao()
    private val connections = InMemoryConnectionDao()
    private val tasks = FakeTaskRepository()
    private val vm by lazy {
        HomeIssuesViewModel(
            InMemorySessionStore(aSession()),
            taskDao,
            InMemoryTaskEventDao(),
            connections,
            tasks,
            IdGenerator(clock),
            clock,
            istanbul,
        )
    }

    private suspend fun TestScope.ready() {
        connections.upsert(listOf(selinConnection()))
        taskDao.upsert(listOf(aTask(status = "MISSED").copy(senderId = Cast.EREN_ID, recipientId = Cast.SELIN_ID)))
        backgroundScope.launch { vm.issues.collect {} }
    }

    @Test
    fun `Süreyi uzat opens the deadline picks for that task and saves the pick`() =
        runTest(main.dispatcher) {
            ready()
            assertEquals(1, vm.issues.value.sent.size)

            vm.onNewDeadline(Cast.TASK_ID)
            val sheet = vm.newDeadline.value!!
            assertEquals("Selin", sheet.toName)
            val evening = DeadlineChoice.TonightEvening(Instant.parse("2026-10-12T19:00:00+03:00"))
            assertTrue(evening in sheet.choices)

            vm.onDeadlineChoice(evening)
            vm.onSave()

            assertEquals(listOf(Cast.TASK_ID to evening.at), tasks.newDeadlines)
            assertNull(vm.newDeadline.value)
        }

    @Test
    fun `a failed save keeps the sheet open and says so`() =
        runTest(main.dispatcher) {
            ready()
            tasks.newDeadlineOutcome = WriteOutcome.Failed
            vm.onNewDeadline(Cast.TASK_ID)
            vm.onDeadlineChoice(DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-13T09:00:00+03:00")))
            vm.onSave()

            val sheet = vm.newDeadline.value!!
            assertTrue(sheet.failed)
            assertEquals(false, sheet.saving)
        }

    @Test
    fun `the other answers go to the repository`() =
        runTest(main.dispatcher) {
            ready()
            vm.onDidItLater("missed-to-me")
            vm.onSenderHandles("dropped")
            vm.onCancel(Cast.TASK_ID)

            assertEquals(listOf("missed-to-me"), tasks.didItLater)
            assertEquals(listOf("dropped"), tasks.senderHandled)
            assertEquals(listOf<String?>(null), tasks.cancelNotes)
        }
}
