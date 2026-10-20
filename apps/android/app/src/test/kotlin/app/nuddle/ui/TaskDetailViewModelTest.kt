package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.data.Delivery
import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.db.TaskEventEntity
import app.nuddle.data.db.shownTime
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeTaskCloser
import app.nuddle.testing.FakeTaskRepository
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryPokeStepDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.InMemoryTaskEventDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingTaskAcks
import app.nuddle.testing.aSession
import app.nuddle.testing.aStep
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.RecipientChoice
import app.nuddle.ui.screens.poke.DropReason
import app.nuddle.ui.screens.task.CancelReason
import app.nuddle.ui.screens.task.DetailAction
import app.nuddle.ui.screens.task.DetailSide
import app.nuddle.ui.screens.task.DetailStatus
import app.nuddle.ui.screens.task.EditRow
import app.nuddle.ui.screens.task.PokesSoFar
import app.nuddle.ui.screens.task.TaskDetailViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The task detail from either side (docs/ux/screens.md 7c, 7d, Task detail; docs/product.md, Sender side). */
class TaskDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val taskDao = InMemoryTaskDao()
    private val connectionDao = InMemoryConnectionDao()
    private val tasks = FakeTaskRepository()
    private val events = InMemoryTaskEventDao()
    private val doneNotes = DoneNotes()
    private val closer = FakeTaskCloser()
    private val steps = InMemoryPokeStepDao()
    private val acks = RecordingTaskAcks()
    private val vm by lazy {
        TaskDetailViewModel(
            SavedStateHandle(mapOf(TaskDetailViewModel.ARG_TASK to Cast.TASK_ID)),
            taskDao,
            connectionDao,
            events,
            steps,
            sessions,
            tasks.reassigner,
            acks,
            doneNotes,
            closer,
            tasks,
            IdGenerator(clock),
            clock,
            istanbul,
        )
    }

    private val toSibel = aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID, rawText = "sibel yarın 9dan önce 1000 çeksin")

    private suspend fun TestScope.ready() {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        connectionDao.upsert(listOf(sibelConnection()))
        taskDao.upsert(listOf(toSibel))
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `shows what I wrote, to whom, how far pokes reach her, and the details`() =
        runTest(main.dispatcher) {
            ready()
            val state = vm.state.value!!
            assertEquals("Eren", state.myName)
            assertEquals("sibel yarın 9dan önce 1000 çeksin", state.rawText)
            assertEquals("Sibel", state.toName)
            assertEquals(Reachability.FULL_SCREEN, state.reachability)
            assertEquals(DeadlineText.Tomorrow("09:00"), state.deadline)
            assertEquals("1.000 TL", state.detail)
            assertTrue(state.photo)
        }

    @Test
    fun `my task to Sibel says how far it got, the recipient's side is not told seen by it (NUD-68)`() =
        runTest(main.dispatcher) {
            ready()
            assertEquals(Delivery.Sent, vm.state.value?.delivery)
            events.upsert(listOf(TaskEventEntity("e1", Cast.TASK_ID, Cast.SIBEL_ID, "delivered", "{}", 1)))
            assertEquals(Delivery.Reached, vm.state.value?.delivery)
            events.upsert(listOf(TaskEventEntity("e2", Cast.TASK_ID, Cast.SIBEL_ID, "seen", "{}", 2)))
            assertEquals(Delivery.Seen, vm.state.value?.delivery)
            assertNull(vm.state.value?.pokeStartsAt, "no start time on my side")
            assertEquals(emptyList(), acks.seen)
        }

    @Test
    fun `Sibel's task to me is told seen and says when poking starts, until a step rang (NUD-68)`() =
        runTest(main.dispatcher) {
            sessions.saveMe("Eren", QuietHours.default(istanbul))
            connectionDao.upsert(listOf(sibelConnection()))
            taskDao.upsert(listOf(aTask().copy(finishMode = "done")))
            steps.insertIgnoringExisting(listOf(aStep(0, "2026-10-12T08:00:00+03:00"), aStep(1, "2026-10-12T08:30:00+03:00")))
            backgroundScope.launch { vm.state.collect {} }

            assertEquals("08:00", vm.state.value?.pokeStartsAt)
            assertNull(vm.state.value?.delivery)
            assertEquals(listOf(Cast.TASK_ID), acks.seen)
            steps.markFired(Cast.TASK_ID, 1, 0, 1)
            assertNull(vm.state.value?.pokeStartsAt, "once it rang, the start is old news")
            assertEquals(listOf(Cast.TASK_ID), acks.seen, "told once")
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

                taskDao.upsert(listOf(toSibel.copy(status = "CANCELLED")))
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

    @Test
    fun `Kime gives my task to someone else, or back to me (NUD-114)`() =
        runTest(main.dispatcher) {
            ready()
            val state = vm.state.value!!
            assertEquals(RecipientChoice.Person(Cast.SIBEL_ID, "Sibel"), state.recipient)
            assertEquals(listOf(RecipientChoice.Self, RecipientChoice.Person(Cast.SIBEL_ID, "Sibel")), state.recipientOptions)
            assertTrue(state.recipientEditable)

            vm.onEdit(EditRow.Recipient)
            assertEquals(state.recipient, vm.state.value?.pickedRecipient, "starts at who it is for now")
            // The same person again changes nothing.
            vm.onEditSave()
            assertEquals(emptyList(), tasks.reassigns)
            assertNull(vm.state.value?.editing)

            vm.onEdit(EditRow.Recipient)
            vm.recipientEdit.onChoice(RecipientChoice.Self)
            vm.onEditSave()
            assertEquals(listOf(Cast.TASK_ID to Cast.EREN_ID), tasks.reassigns)
            assertNull(vm.state.value?.editing)
        }

    @Test
    fun `Kime is not tappable once the task left Kurulu, or with no one else to give it to`() =
        runTest(main.dispatcher) {
            sessions.saveMe("Eren", QuietHours.default(istanbul))
            taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.EREN_ID)))
            backgroundScope.launch { vm.state.collect {} }
            assertFalse(vm.state.value!!.recipientEditable, "no connection: only Kendine")

            connectionDao.upsert(listOf(sibelConnection()))
            assertTrue(vm.state.value!!.recipientEditable)
            taskDao.upsert(listOf(aTask(status = "REJECTED").copy(senderId = Cast.EREN_ID, recipientId = Cast.EREN_ID)))
            assertFalse(vm.state.value!!.recipientEditable)
        }

    private suspend fun TestScope.open(task: TaskEntity) {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        connectionDao.upsert(listOf(sibelConnection()))
        taskDao.upsert(listOf(task))
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `a task from Sibel shows who wrote it, her number, and Bitti, çek with Bugün yapamayacağım`() =
        runTest(main.dispatcher) {
            open(aTask())
            val state = vm.state.value!!
            assertEquals(DetailSide.ToMe, state.side)
            assertEquals("Sibel", state.from?.name)
            assertEquals(Cast.SIBEL_PHONE, state.from?.phone)
            assertEquals(listOf(DetailAction.TakePhoto, DetailAction.CantToday), state.actions)
            assertFalse(state.editable, "the recipient does not change the task")
        }

    @Test
    fun `Şimdiden bitti closes the task and leaves the done note for home`() =
        runTest(main.dispatcher) {
            open(aTask().copy(finishMode = "done"))
            assertEquals(
                DetailAction.DoneEarly,
                vm.state.value!!
                    .actions
                    .first(),
            )
            tasks.caughtAt = 2
            vm.close.test {
                vm.answers.onDone("Temizlikçi için para çek")
                assertEquals(listOf(Cast.TASK_ID), tasks.done)
                assertEquals(DoneNote(Cast.TASK_ID, "Temizlikçi için para çek", 2), doneNotes.pending.value)
                taskDao.upsert(listOf(aTask(status = "DONE")))
                awaitItem()
            }
        }

    @Test
    fun `Bugün yapamayacağım opens 7f for Sibel and sends the chosen note`() =
        runTest(main.dispatcher) {
            open(aTask())
            vm.drop.onOpen("Sibel")
            vm.drop.onReason(DropReason.Tomorrow)
            assertEquals(
                DropReason.Tomorrow,
                vm.state.value
                    ?.drop
                    ?.reason,
            )
            vm.drop.onReason(DropReason.Tomorrow)
            assertNull(
                vm.state.value
                    ?.drop
                    ?.reason,
                "a second tap clears the chip",
            )
            vm.drop.onDismiss()
            vm.answers.onCantToday("Yarın hallederim")
            assertEquals(listOf<Pair<String, String?>>(Cast.TASK_ID to "Yarın hallederim"), closer.cantToday)
        }

    @Test
    fun `a task to self has Şimdiden bitti and Görevi iptal et, and can be edited`() =
        runTest(main.dispatcher) {
            open(aTask().copy(senderId = Cast.EREN_ID, finishMode = "done"))
            val state = vm.state.value!!
            assertEquals(DetailSide.ToSelf, state.side)
            assertTrue(state.self)
            assertEquals(listOf(DetailAction.DoneEarly, DetailAction.CancelTask), state.actions)
            assertTrue(state.editable)
        }

    @Test
    fun `my missed task to me has Sonradan yaptım and Listeden kaldır`() =
        runTest(main.dispatcher) {
            open(aTask(status = "MISSED"))
            val state = vm.state.value!!
            assertEquals(DetailStatus.Missed, state.status)
            assertEquals("Sibel", state.missed?.fromName)
            assertEquals(listOf(DetailAction.DidItLater, DetailAction.RemoveMissed), state.actions)
            vm.answers.onDidItLater()
            vm.answers.onRemoveMissed()
            assertEquals(listOf(Cast.TASK_ID), tasks.didItLater)
            assertEquals(listOf(Cast.TASK_ID), tasks.removedMissed)
        }

    @Test
    fun `my task to Sibel says how many pokes rang and when the last one was (NUD-81)`() =
        runTest(main.dispatcher) {
            open(toSibel)
            assertEquals(null, vm.state.value!!.pokesSoFar)
            events.upsert(
                listOf(
                    TaskEventEntity(
                        "p1",
                        Cast.TASK_ID,
                        null,
                        "poke_fired",
                        "{}",
                        Instant.parse("2026-10-12T09:00:00+03:00").toEpochMilliseconds(),
                    ),
                    TaskEventEntity(
                        "p2",
                        Cast.TASK_ID,
                        null,
                        "poke_fired",
                        "{}",
                        Instant.parse("2026-10-12T09:10:00+03:00").toEpochMilliseconds(),
                    ),
                ),
            )
            assertEquals(PokesSoFar(2, "09:10"), vm.state.value!!.pokesSoFar)
        }

    @Test
    fun `Sibel's dropped task shows her note, Yeni zaman ver saves the pick, Ben hallederim takes it over`() =
        runTest(main.dispatcher) {
            open(toSibel.copy(status = "DROPPED"))
            events.upsert(
                listOf(
                    TaskEventEntity(
                        "d1",
                        Cast.TASK_ID,
                        Cast.SIBEL_ID,
                        "dropped",
                        """{"note":"Yarın hallederim"}""",
                        clock.now().toEpochMilliseconds(),
                    ),
                ),
            )
            val state = vm.state.value!!
            assertEquals(DetailStatus.Dropped, state.status)
            assertEquals("Yarın hallederim", state.sentIssue?.note)
            assertEquals(listOf(DetailAction.NewTime, DetailAction.SenderHandles), state.actions)

            vm.newDeadline.onOpen(state.toName)
            val sheet = vm.state.value!!.newDeadline!!
            assertEquals("Sibel", sheet.toName)
            val pick = sheet.choices.first()
            vm.newDeadline.onChoice(pick)
            vm.newDeadline.onSave()
            assertEquals(listOf(Cast.TASK_ID to pick.at), tasks.newDeadlines)
            assertNull(vm.state.value?.newDeadline)

            vm.answers.onSenderHandles()
            assertEquals(listOf(Cast.TASK_ID), tasks.senderHandled)
        }

    @Test
    fun `a failed new deadline keeps the sheet and says so, and İptal et on a missed task needs no sheet`() =
        runTest(main.dispatcher) {
            open(toSibel.copy(status = "MISSED"))
            assertEquals(listOf(DetailAction.Extend, DetailAction.CancelNow), vm.state.value!!.actions)
            tasks.newDeadlineOutcome = WriteOutcome.Failed
            vm.newDeadline.onOpen("Sibel")
            vm.newDeadline.onChoice(
                vm.state.value!!
                    .newDeadline!!
                    .choices
                    .first(),
            )
            vm.newDeadline.onSave()
            val sheet = vm.state.value!!.newDeadline!!
            assertTrue(sheet.failed)
            assertEquals(false, sheet.saving)

            vm.answers.onCancelNow()
            assertEquals(listOf<String?>(null), tasks.cancelNotes)
        }

    @Test
    fun `a task that is no longer mine to see closes the detail`() =
        runTest(main.dispatcher) {
            vm.close.test {
                open(aTask().copy(senderId = Cast.SIBEL_ID, recipientId = "someone-else"))
                awaitItem()
            }
        }

    @Test
    fun `the start line opens a sheet whose time must be before the task's time, and saving sends set_start (NUD-78)`() =
        runTest(main.dispatcher) {
            ready()
            val taskTime = vm.state.value?.taskTime
            assertEquals(Instant.fromEpochMilliseconds(aTask().shownTime), taskTime)
            vm.start.onOpen(taskTime ?: error("no task time"))
            assertEquals(
                taskTime,
                vm.state.value
                    ?.start
                    ?.limit,
            )
            vm.start.onTimePicked(LocalTime(10, 0))
            assertEquals(
                true,
                vm.state.value
                    ?.start
                    ?.pickedTooLate,
            )
            vm.start.onSave()
            assertEquals(emptyList(), tasks.starts, "a start past the task's time is not sent")
            vm.start.onTimePicked(LocalTime(8, 0))
            assertEquals(
                false,
                vm.state.value
                    ?.start
                    ?.pickedTooLate,
            )
            vm.start.onSave()
            assertEquals(1, tasks.starts.size)
            assertEquals(Cast.TASK_ID, tasks.starts.single().first)
            assertNull(vm.state.value?.start, "the sheet closes once the server took it")
        }
}
