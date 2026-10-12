package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.data.WriteOutcome
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeTaskRepository
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aSession
import app.nuddle.testing.aTask
import app.nuddle.testing.selinConnection
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.task.CancelReason
import app.nuddle.ui.screens.task.EditRow
import app.nuddle.ui.screens.task.TaskDetailViewModel
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

/** The sender's task detail and cancel (docs/ux/screens.md 7c, 7d; docs/product.md, Sender side). */
class TaskDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val taskDao = InMemoryTaskDao()
    private val connectionDao = InMemoryConnectionDao()
    private val tasks = FakeTaskRepository()
    private val vm by lazy {
        TaskDetailViewModel(
            SavedStateHandle(mapOf(TaskDetailViewModel.ARG_TASK to Cast.TASK_ID)),
            taskDao,
            connectionDao,
            sessions,
            tasks,
            IdGenerator(clock),
            clock,
            istanbul,
        )
    }

    private val toSelin = aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SELIN_ID, rawText = "selin yarın 9dan önce 1000 çeksin")

    private suspend fun TestScope.ready() {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        connectionDao.upsert(listOf(selinConnection()))
        taskDao.upsert(listOf(toSelin))
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `shows what I wrote, to whom, how far pokes reach her, and the details`() =
        runTest(main.dispatcher) {
            ready()
            val state = vm.state.value!!
            assertEquals("Eren", state.myName)
            assertEquals("selin yarın 9dan önce 1000 çeksin", state.rawText)
            assertEquals("Selin", state.toName)
            assertEquals(Reachability.FULL_SCREEN, state.reachability)
            assertEquals(DeadlineText.Tomorrow("09:00"), state.deadline)
            assertEquals("1.000 TL", state.detail)
            assertTrue(state.photo)
        }

    @Test
    fun `İptal et sends the chosen reason as the note, and the screen closes once the task is cancelled`() =
        runTest(main.dispatcher) {
            ready()
            vm.close.test {
                vm.onCancel()
                assertTrue(vm.state.value!!.cancelling)
                vm.onReason(CancelReason.HandledIt)
                assertEquals(CancelReason.HandledIt, vm.state.value?.reason)
                vm.onCancelConfirm("Ben hallettim")
                assertEquals(listOf(Cast.TASK_ID), tasks.cancelled)
                assertEquals(listOf<String?>("Ben hallettim"), tasks.cancelNotes)

                taskDao.upsert(listOf(toSelin.copy(status = "CANCELLED")))
                awaitItem()
            }
        }

    @Test
    fun `the reason is optional, a second tap clears it, and Vazgeç sends nothing`() =
        runTest(main.dispatcher) {
            ready()
            vm.onCancel()
            vm.onReason(CancelReason.NotNeeded)
            vm.onReason(CancelReason.NotNeeded)
            assertNull(vm.state.value?.reason)
            vm.onCancelDismiss()
            assertEquals(false, vm.state.value?.cancelling)
            assertTrue(tasks.cancelled.isEmpty())
        }

    @Test
    fun `Son zaman takes a new deadline, sent with the rest unchanged`() =
        runTest(main.dispatcher) {
            ready()
            vm.onEdit(EditRow.Deadline)
            val morning =
                vm.state.value!!
                    .deadlineChoices
                    .single()
            vm.onDeadlineChoice(morning)
            vm.onEditSave()
            assertEquals(listOf<Pair<Instant?, FinishMode?>>(morning.at to null), tasks.rowEdits)
            assertNull(vm.state.value?.editing)
        }

    @Test
    fun `Nasıl bitsin switches photo off, and a failed save keeps the sheet for another try`() =
        runTest(main.dispatcher) {
            ready()
            vm.onEdit(EditRow.Finish)
            assertTrue(vm.state.value!!.pickedPhoto, "starts at what the task has")
            vm.onPhotoChoice(false)
            tasks.editOutcome = WriteOutcome.Failed
            vm.onEditSave()
            assertEquals(EditRow.Finish, vm.state.value?.editing)
            assertTrue(vm.state.value!!.saveFailed)

            tasks.editOutcome = WriteOutcome.Created(Cast.TASK_ID)
            vm.onEditSave()
            assertEquals(listOf<Pair<Instant?, FinishMode?>>(null to FinishMode.DONE, null to FinishMode.DONE), tasks.rowEdits)
            assertNull(vm.state.value?.editing)
        }
}
