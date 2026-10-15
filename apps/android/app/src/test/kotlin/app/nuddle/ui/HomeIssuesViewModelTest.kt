package app.nuddle.ui

import app.nuddle.data.db.TaskEventEntity
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryDismissedRows
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.InMemoryTaskEventDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aSession
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.HomeIssuesViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What on home waits for an answer, and the ✕ of an "iptal etti" row (docs/ux/screens.md, Home tabs; NUD-67). */
class HomeIssuesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val now = Instant.parse("2026-10-12T10:00:00+03:00")
    private val clock = FakeClock(now)
    private val taskDao = InMemoryTaskDao()
    private val events = InMemoryTaskEventDao()
    private val connections = InMemoryConnectionDao()
    private val dismissed = InMemoryDismissedRows()
    private val vm by lazy {
        HomeIssuesViewModel(InMemorySessionStore(aSession()), taskDao, events, connections, dismissed, clock, istanbul)
    }

    @Test
    fun `my missed task to Sibel waits for my answer`() =
        runTest(main.dispatcher) {
            connections.upsert(listOf(sibelConnection()))
            taskDao.upsert(listOf(aTask(status = "MISSED").copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)))
            backgroundScope.launch { vm.issues.collect {} }
            assertEquals(
                listOf(Cast.TASK_ID),
                vm.issues.value.sent
                    .map { it.taskId },
            )
        }

    @Test
    fun `the ✕ takes the cancelled row off home, and only that one`() =
        runTest(main.dispatcher) {
            connections.upsert(listOf(sibelConnection()))
            val cancelledAt = (now - 1.hours).toEpochMilliseconds()
            taskDao.upsert(
                listOf(
                    aTask(status = "CANCELLED").copy(statusChangedAt = cancelledAt),
                    aTask(id = "other", status = "CANCELLED").copy(statusChangedAt = cancelledAt),
                ),
            )
            events.upsert(listOf(Cast.TASK_ID, "other").map { cancelEvent(it, cancelledAt) })
            backgroundScope.launch { vm.issues.collect {} }
            assertEquals(2, vm.issues.value.cancelled.size)

            vm.onDismissCancelled(Cast.TASK_ID)

            assertEquals(
                listOf("other"),
                vm.issues.value.cancelled
                    .map { it.taskId },
            )
            assertTrue(taskDao.get(Cast.TASK_ID)?.status == "CANCELLED", "the task itself is not touched")
        }

    private fun cancelEvent(
        taskId: String,
        at: Long,
    ) = TaskEventEntity(
        id = "cancel-$taskId",
        taskId = taskId,
        actorId = Cast.SIBEL_ID,
        type = "cancelled",
        payload = """{"reason":"sender_cancelled"}""",
        at = at,
    )
}
