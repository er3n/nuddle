package app.nuddle.ui

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.nuddle.data.WriteOutcome
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeTaskRepository
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aSession
import app.nuddle.testing.aTask
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.HomeViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

/** Home (docs/ux/screens.md 2a, 2f): writing a task to self and the "Sıradaki" card. */
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val tasks = FakeTaskRepository()
    private val taskDao = InMemoryTaskDao()
    private val vm by lazy { HomeViewModel(sessions, taskDao, tasks, IdGenerator(clock), clock, istanbul) }

    private suspend fun signedIn() = sessions.saveMe("Eren", QuietHours.default(istanbul))

    private suspend fun TestScope.ready() {
        signedIn()
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `a task the server created clears the bar`() =
        runTest(main.dispatcher) {
            ready()
            vm.onComposeTextChange("1000 tl çek yarın 9da")
            vm.onComposeSend()
            assertEquals(listOf("1000 tl çek yarın 9da"), tasks.written.map { it.first })
            assertEquals(ComposeState.Idle, vm.state.value?.compose)
        }

    @Test
    fun `a failed send keeps the text and the retry reuses the idempotency key`() =
        runTest(main.dispatcher) {
            ready()
            tasks.outcome = WriteOutcome.Failed
            vm.onComposeTextChange("1000 tl çek")
            vm.onComposeSend()
            assertEquals(ComposeState.Failed("1000 tl çek"), vm.state.value?.compose)

            tasks.outcome = WriteOutcome.Created
            vm.onComposeSend()
            assertEquals(ComposeState.Idle, vm.state.value?.compose)
            assertEquals(2, tasks.written.size)
            assertEquals(tasks.written[0].second, tasks.written[1].second)
        }

    @Test
    fun `changed text after a failure is a new command`() =
        runTest(main.dispatcher) {
            ready()
            tasks.outcome = WriteOutcome.Failed
            vm.onComposeTextChange("1000 tl çek")
            vm.onComposeSend()
            vm.onComposeTextChange("2000 tl çek")
            vm.onComposeSend()
            assertNotEquals(tasks.written[0].second, tasks.written[1].second)
        }

    @Test
    fun `when the server asks, the text stays until the preview arrives`() =
        runTest(main.dispatcher) {
            ready()
            tasks.outcome = WriteOutcome.Ask(null)
            vm.onComposeTextChange("1000 tl çek")
            vm.onComposeSend()
            assertEquals(ComposeState.Typing("1000 tl çek"), vm.state.value?.compose)
        }

    @Test
    fun `a blank text is not sent`() =
        runTest(main.dispatcher) {
            ready()
            vm.onComposeTextChange("   ")
            vm.onComposeSend()
            assertEquals(emptyList(), tasks.written)
        }

    @Test
    fun `the task to self due first is next up and Şimdiden bitti closes it`() =
        runTest(main.dispatcher) {
            signedIn()
            taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID)))
            vm.state.test {
                val card = awaitItemWhere { it?.nextUp != null }?.nextUp
                assertEquals(Cast.TASK_ID, card?.taskId)
                assertEquals(DeadlineText.Tomorrow("09:00"), card?.deadline)
                vm.onFinishNextUp()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(Cast.TASK_ID), tasks.done)
        }

    private suspend fun <T> ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
