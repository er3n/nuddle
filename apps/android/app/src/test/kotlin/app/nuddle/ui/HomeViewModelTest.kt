package app.nuddle.ui

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
import app.nuddle.data.TaskDraft
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.InviteEntity
import app.nuddle.domain.GrantedPermissions
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
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingErrorReporter
import app.nuddle.testing.aSession
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.DoneNoteUi
import app.nuddle.ui.screens.home.HomeTab
import app.nuddle.ui.screens.home.HomeViewModel
import app.nuddle.ui.screens.home.NextUpStatus
import app.nuddle.ui.screens.home.RecipientChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Home (docs/ux/screens.md 2a, 2f, 4e): writing a task and the "Sıradaki" card. Robolectric for the photo of a task
 * awaiting approval (BitmapFactory).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val tasks = FakeTaskRepository()
    private val taskDao = InMemoryTaskDao()
    private val permissions = FakePermissions()
    private val setup = InMemorySetupStore()
    private val doneNotes = DoneNotes()
    private val inviteDao = InMemoryInviteDao()
    private val connectionDao = InMemoryConnectionDao()
    private val photos = FakePhotoLoader()
    private val uploads = FakePhotoUploads()
    private val connectivity = FakeConnectivity()
    private val queued = InMemoryQueuedWriteDao()
    private val errors = RecordingErrorReporter()
    private val vm by lazy {
        HomeViewModel(
            sessions,
            taskDao,
            inviteDao,
            connectionDao,
            tasks,
            IdGenerator(clock),
            SetupGate(setup, permissions),
            doneNotes,
            photos,
            uploads,
            connectivity,
            queued,
            errors,
            clock,
            istanbul,
        )
    }

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

            tasks.outcome = WriteOutcome.Created(Cast.TASK_ID)
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
    fun `when the server asks something the preview cannot ask yet, the text stays`() =
        runTest(main.dispatcher) {
            ready()
            tasks.outcome = WriteOutcome.Ask(WriteTaskResult.Question.TITLE, TaskDraft("Para çek", null, FinishMode.DONE))
            vm.onComposeTextChange("1000 tl çek")
            vm.onComposeSend()
            assertEquals(ComposeState.Typing("1000 tl çek"), vm.state.value?.compose)
            assertNull(vm.state.value?.preview)
        }

    @Test
    fun `text that is not a task shows the reply above an empty bar until it times out or the bar changes (NUD-52)`() =
        runTest(main.dispatcher) {
            ready()
            tasks.outcome = WriteOutcome.Chat("Merhaba! Kime ne hatırlatayım?")
            vm.onComposeTextChange("merhaba")
            vm.onComposeSend()
            assertEquals(ComposeState.Idle, vm.state.value?.compose)
            assertEquals("Merhaba! Kime ne hatırlatayım?", vm.state.value?.reply)
            assertNull(vm.state.value?.undo)

            vm.onReplyTimeout()
            assertNull(vm.state.value?.reply)

            vm.onComposeTextChange("merhaba")
            vm.onComposeSend()
            vm.onComposeTextChange("e")
            assertNull(vm.state.value?.reply)
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
    fun `a task from Sibel is next up once her connection is known`() =
        runTest(main.dispatcher) {
            signedIn()
            taskDao.upsert(listOf(aTask().copy(finishMode = "done")))
            vm.state.test {
                val before = awaitItemWhere { it != null }
                assertNull(before?.nextUp)
                assertFalse(before?.connected ?: true)
                connectionDao.upsert(listOf(sibelConnection()))
                val after = awaitItemWhere { it?.nextUp != null }
                assertEquals("Sibel", after?.nextUp?.from?.name)
                assertTrue(after?.connected ?: false, "no invite card once someone is connected")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a photo waiting for Sibel shows on the card, loaded once`() =
        runTest(main.dispatcher) {
            signedIn()
            connectionDao.upsert(listOf(sibelConnection()))
            taskDao.upsert(listOf(aTask(status = "AWAITING_APPROVAL")))
            vm.state.test {
                val state = awaitItemWhere { it?.nextUpPhoto != null }
                assertEquals(NextUpStatus.AwaitingApproval, state?.nextUp?.status)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(Cast.TASK_ID), photos.loaded)
        }

    @Test
    fun `a photo whose upload failed says so on the card (6e)`() =
        runTest(main.dispatcher) {
            signedIn()
            connectionDao.upsert(listOf(sibelConnection()))
            taskDao.upsert(listOf(aTask(status = "AWAITING_APPROVAL")))
            uploads.failing.value = true
            vm.state.test {
                assertEquals(true, awaitItemWhere { it?.nextUp?.uploadFailed == true }?.nextUp?.uploadFailed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the task to self due first is next up`() =
        runTest(main.dispatcher) {
            signedIn()
            taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID)))
            vm.state.test {
                val card = awaitItemWhere { it?.nextUp != null }?.nextUp
                assertEquals(Cast.TASK_ID, card?.taskId)
                assertEquals(DeadlineText.Tomorrow("09:00"), card?.deadline)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `every other task to me is listed after the next-up card, from others first, then to self (NUD-61)`() =
        runTest(main.dispatcher) {
            signedIn()
            connectionDao.upsert(listOf(sibelConnection()))
            val due = aTask().deadline
            taskDao.upsert(
                listOf(
                    aTask().copy(id = "first", finishMode = "done", deadline = due),
                    aTask().copy(id = "wake", title = "Beni uyandır", finishMode = "done", deadline = due + 60_000),
                    aTask().copy(id = "gym", senderId = Cast.EREN_ID, title = "Spor yap", deadline = due + 120_000),
                ),
            )
            vm.state.test {
                val home = awaitItemWhere { it?.later?.size == 2 }
                assertEquals("first", home?.nextUp?.taskId)
                assertEquals(listOf("wake" to "Sibel", "gym" to null), home?.later?.map { it.taskId to it.fromName })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a snoozed next-up card says when the poke comes back, and not after (NUD-63)`() =
        runTest(main.dispatcher) {
            signedIn()
            connectionDao.upsert(listOf(sibelConnection()))
            val until = Instant.parse("2026-10-12T08:30:00+03:00").toEpochMilliseconds()
            taskDao.upsert(listOf(aTask().copy(finishMode = "done", snoozedUntil = until)))
            vm.state.test {
                assertEquals("08:30", awaitItemWhere { it?.nextUp != null }?.nextUp?.snoozedUntil)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun TestScope.asked(ask: WriteOutcome.Ask) {
        connectionDao.upsert(listOf(sibelConnection().copy(reachability = "SOUND")))
        ready()
        tasks.outcome = ask
        vm.onComposeTextChange("sibel yarın 9dan önce 1000 çeksin")
        vm.onComposeSend()
    }

    @Test
    fun `4a, the deadline asked for Sibel shows her name, how far pokes reach her, photo preselected for money, Gönder`() =
        runTest(main.dispatcher) {
            permissions.granted = GrantedPermissions(notifications = false, exactAlarm = true, fullScreen = true)
            vm.openSetup.test {
                asked(
                    WriteOutcome.Ask(
                        WriteTaskResult.Question.DEADLINE,
                        TaskDraft("Temizlikçi için para çek", "1.000 TL", FinishMode.PHOTO, recipient = "sibel"),
                    ),
                )
                val preview = vm.state.value?.preview!!
                assertEquals("Sibel", preview.to)
                assertEquals(Reachability.SOUND, preview.reach)
                assertTrue(preview.photo && preview.photoSuggested && preview.toOther)

                vm.previewSheet.onPhoto(false)
                vm.previewSheet.onChoice(preview.choices.single())
                vm.onPreviewConfirm()
                assertEquals(listOf<String?>(Cast.SIBEL_ID), tasks.createdFor)
                assertEquals(listOf(FinishMode.DONE), tasks.createdFinishModes)
                assertNull(vm.state.value?.preview)
                expectNoEvents()
            }
        }

    @Test
    fun `4a, Kime asked offers Kendine or a connection, the deadline from the text is kept`() =
        runTest(main.dispatcher) {
            val nineTomorrow = Instant.parse("2026-10-12T09:00:00+03:00")
            asked(
                WriteOutcome.Ask(
                    WriteTaskResult.Question.RECIPIENT,
                    TaskDraft("Temizlikçi için para çek", null, FinishMode.PHOTO, recipient = null, deadline = nineTomorrow),
                ),
            )
            val preview = vm.state.value?.preview!!
            assertEquals(listOf(RecipientChoice.Self, RecipientChoice.Person(Cast.SIBEL_ID, "Sibel")), preview.recipients)
            assertEquals(DeadlineText.Tomorrow("09:00"), preview.knownDeadline)
            assertFalse(preview.ready, "nobody chosen yet")

            vm.previewSheet.onRecipient(RecipientChoice.Person(Cast.SIBEL_ID, "Sibel"))
            assertEquals(
                Reachability.SOUND,
                vm.state.value
                    ?.preview
                    ?.reach,
            )
            vm.onPreviewConfirm()
            assertEquals(listOf<String?>(Cast.SIBEL_ID), tasks.createdFor)
            assertEquals(nineTomorrow, tasks.created.single().second)
        }

    @Test
    fun `a name no connection matches leaves the text in the bar`() =
        runTest(main.dispatcher) {
            asked(WriteOutcome.Ask(WriteTaskResult.Question.DEADLINE, TaskDraft("Para çek", null, FinishMode.DONE, recipient = "Defne")))
            assertNull(vm.state.value?.preview)
            assertEquals(ComposeState.Typing("sibel yarın 9dan önce 1000 çeksin"), vm.state.value?.compose)
        }

    private val askDeadline =
        WriteOutcome.Ask(WriteTaskResult.Question.DEADLINE, TaskDraft("Para çek", "1.000 TL", FinishMode.DONE))

    private suspend fun TestScope.previewOpen() {
        ready()
        tasks.outcome = askDeadline
        vm.onComposeTextChange("1000 tl çek")
        vm.onComposeSend()
    }

    @Test
    fun `a missing deadline opens the preview with what was understood`() =
        runTest(main.dispatcher) {
            previewOpen()
            val preview = vm.state.value?.preview
            assertEquals("1000 tl çek", preview?.rawText)
            assertEquals("Para çek", preview?.title)
            assertEquals("1.000 TL", preview?.detail)
            // 20:00 now: this evening is gone, so only "Yarın 09:00".
            assertEquals(listOf(DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00"))), preview?.choices)
            assertNull(preview?.selected)
            assertEquals("23:00" to "07:00", preview?.quietStart to preview?.quietEnd)
        }

    @Test
    fun `Kur creates the task with the chosen deadline and clears the bar`() =
        runTest(main.dispatcher) {
            previewOpen()
            vm.onPreviewConfirm()
            assertEquals(emptyList(), tasks.created, "nothing chosen yet")

            val morning =
                vm.state.value!!
                    .preview!!
                    .choices
                    .single()
            vm.previewSheet.onChoice(morning)
            vm.onPreviewConfirm()
            assertEquals("Para çek" to Instant.parse("2026-10-12T09:00:00+03:00"), tasks.created.single().let { it.first to it.second })
            assertNull(vm.state.value?.preview)
            assertEquals(ComposeState.Idle, vm.state.value?.compose)
        }

    @Test
    fun `a second tap clears the choice and a picked time is its next occurrence`() =
        runTest(main.dispatcher) {
            previewOpen()
            val morning =
                vm.state.value!!
                    .preview!!
                    .choices
                    .single()
            vm.previewSheet.onChoice(morning)
            vm.previewSheet.onChoice(morning)
            assertNull(
                vm.state.value
                    ?.preview
                    ?.selected,
            )

            vm.previewSheet.onTimePicked(LocalTime(18, 30))
            assertEquals(
                DeadlineChoice.Picked(Instant.parse("2026-10-12T18:30:00+03:00"), "18:30"),
                vm.state.value
                    ?.preview
                    ?.selected,
            )
        }

    @Test
    fun `Vazgeç closes the preview and keeps the text`() =
        runTest(main.dispatcher) {
            previewOpen()
            vm.previewSheet.onDismiss()
            assertNull(vm.state.value?.preview)
            assertEquals(ComposeState.Typing("1000 tl çek"), vm.state.value?.compose)
        }

    @Test
    fun `a failed create closes the preview and offers Tekrar dene`() =
        runTest(main.dispatcher) {
            previewOpen()
            tasks.createOutcome = WriteOutcome.Failed
            vm.previewSheet.onChoice(
                vm.state.value!!
                    .preview!!
                    .choices
                    .single(),
            )
            vm.onPreviewConfirm()
            assertNull(vm.state.value?.preview)
            assertEquals(ComposeState.Failed("1000 tl çek"), vm.state.value?.compose)
        }

    private suspend fun TestScope.sentDirectly() {
        ready()
        taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, finishMode = "done")))
        vm.onComposeTextChange("1000 tl çek yarın 9da")
        vm.onComposeSend()
    }

    @Test
    fun `a task that went out without a question shows the undo toast`() =
        runTest(main.dispatcher) {
            sentDirectly()
            val undo = vm.state.value?.undo
            assertEquals(Cast.TASK_ID, undo?.taskId)
            assertEquals("Temizlikçi için para çek", undo?.title)
            assertEquals(DeadlineText.Tomorrow("09:00"), undo?.deadline)

            vm.onUndoTimeout()
            assertNull(vm.state.value?.undo)
            assertEquals(emptyList(), tasks.cancelled)
        }

    @Test
    fun `a task that went out to Sibel says Gönderildi with her name, sits in my sent list, and opens no setup`() =
        runTest(main.dispatcher) {
            permissions.granted = GrantedPermissions(notifications = false, exactAlarm = true, fullScreen = true)
            connectionDao.upsert(listOf(sibelConnection()))
            vm.openSetup.test {
                ready()
                taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)))
                vm.onComposeTextChange("sibel yarın 9dan önce 1000 tl çeksin")
                vm.onComposeSend()
                assertEquals(
                    "Sibel",
                    vm.state.value
                        ?.undo
                        ?.to,
                )
                assertEquals(
                    listOf("Sibel"),
                    vm.state.value
                        ?.sent
                        ?.map { it.toName },
                )
                assertNull(vm.state.value?.nextUp, "Sibel's task is not my next up")

                vm.onUndoTimeout()
                expectNoEvents()
            }
        }

    @Test
    fun `home opens on Bana, a task to Sibel switches to Gönderdiklerim, one to self back to Bana (NUD-67)`() =
        runTest(main.dispatcher) {
            connectionDao.upsert(listOf(sibelConnection()))
            ready()
            assertEquals(HomeTab.Mine, vm.state.value?.tab)
            taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)))
            vm.onComposeTextChange("sibel yarın 9dan önce 1000 tl çeksin")
            vm.onComposeSend()
            assertEquals(HomeTab.Sent, vm.state.value?.tab)

            vm.onUndoTimeout()
            taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.EREN_ID)))
            vm.onComposeTextChange("1000 tl çek yarın 9da")
            vm.onComposeSend()
            assertEquals(HomeTab.Mine, vm.state.value?.tab)

            vm.onTab(HomeTab.Sent)
            assertEquals(HomeTab.Sent, vm.state.value?.tab)
        }

    @Test
    fun `Düzenle on a task to Sibel keeps her in the preview`() =
        runTest(main.dispatcher) {
            connectionDao.upsert(listOf(sibelConnection()))
            ready()
            taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID)))
            vm.onComposeTextChange("sibel yarın 9dan önce 1000 tl çeksin")
            vm.onComposeSend()
            vm.onUndoEdit()
            assertEquals(
                "Sibel",
                vm.state.value
                    ?.preview
                    ?.to,
            )
        }

    @Test
    fun `setup opens once the undo window is over and a permission is missing`() =
        runTest(main.dispatcher) {
            permissions.granted = GrantedPermissions(notifications = false, exactAlarm = true, fullScreen = true)
            vm.openSetup.test {
                sentDirectly()
                expectNoEvents()
                vm.onUndoTimeout()
                awaitItem()
            }
        }

    @Test
    fun `setup does not open when everything is granted, when it ran before, or after Geri al`() =
        runTest(main.dispatcher) {
            vm.openSetup.test {
                sentDirectly()
                vm.onUndoTimeout()

                permissions.granted = GrantedPermissions(notifications = true, exactAlarm = false, fullScreen = true)
                setup.done = true
                sentDirectly()
                vm.onUndoTimeout()

                setup.done = false
                sentDirectly()
                vm.onUndo()
                expectNoEvents()
            }
        }

    @Test
    fun `setup opens after Kur in the preview`() =
        runTest(main.dispatcher) {
            permissions.granted = GrantedPermissions(notifications = true, exactAlarm = true, fullScreen = false)
            vm.openSetup.test {
                previewOpen()
                vm.previewSheet.onChoice(
                    vm.state.value!!
                        .preview!!
                        .choices
                        .single(),
                )
                vm.onPreviewConfirm()
                awaitItem()
            }
        }

    @Test
    fun `Geri al cancels the task`() =
        runTest(main.dispatcher) {
            sentDirectly()
            vm.onUndo()
            assertEquals(listOf(Cast.TASK_ID), tasks.cancelled)
            assertNull(vm.state.value?.undo)
        }

    @Test
    fun `no toast when the created task is not on the device yet`() =
        runTest(main.dispatcher) {
            ready()
            vm.onComposeTextChange("1000 tl çek yarın 9da")
            vm.onComposeSend()
            assertNull(vm.state.value?.undo)
            assertEquals(ComposeState.Idle, vm.state.value?.compose)
        }

    @Test
    fun `Düzenle opens the preview and Kur moves the deadline of the same task`() =
        runTest(main.dispatcher) {
            sentDirectly()
            vm.onUndoEdit()
            val preview = vm.state.value?.preview
            assertEquals("1000 tl çek yarın 9da", preview?.rawText)
            assertNull(vm.state.value?.undo)

            vm.previewSheet.onChoice(preview!!.choices.single())
            vm.onPreviewConfirm()
            assertEquals(Cast.TASK_ID to Instant.parse("2026-10-12T09:00:00+03:00"), tasks.edited.single().let { it.first to it.second })
            assertEquals(emptyList(), tasks.created)
            assertNull(vm.state.value?.preview)
        }

    @Test
    fun `a failed edit keeps the preview for another try`() =
        runTest(main.dispatcher) {
            sentDirectly()
            vm.onUndoEdit()
            tasks.editOutcome = WriteOutcome.Failed
            vm.previewSheet.onChoice(
                vm.state.value!!
                    .preview!!
                    .choices
                    .single(),
            )
            vm.onPreviewConfirm()
            assertEquals(
                false,
                vm.state.value
                    ?.preview
                    ?.sending,
            )
            assertEquals(ComposeState.Idle, vm.state.value?.compose)
        }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `the done note shows for 4 seconds`() =
        runTest(main.dispatcher) {
            ready()
            doneNotes.post(DoneNote(Cast.TASK_ID, "Temizlikçi için para çek", caughtAt = 2))
            assertEquals(DoneNoteUi("Temizlikçi için para çek", 2), vm.state.value?.doneNote)

            advanceTimeBy(3_999)
            assertEquals(
                2,
                vm.state.value
                    ?.doneNote
                    ?.caughtAt,
            )
            advanceTimeBy(2)
            assertNull(vm.state.value?.doneNote)
        }

    @Test
    fun `my open invite shows as pending until it is accepted`() =
        runTest(main.dispatcher) {
            ready()
            inviteDao.upsert(listOf(anInvite(status = "open")))
            assertEquals(
                "Sibel",
                vm.state.value
                    ?.pendingInvite
                    ?.name,
            )
            assertEquals(
                DeadlineText.Today("14:32"),
                vm.state.value
                    ?.pendingInvite
                    ?.sharedAt,
            )

            inviteDao.upsert(listOf(anInvite(status = "accepted")))
            assertNull(vm.state.value?.pendingInvite)
        }

    private fun anInvite(status: String) =
        InviteEntity(
            id = "i1",
            code = "abc",
            sent = true,
            name = "Sibel",
            phone = Cast.SIBEL_PHONE,
            inviterId = null,
            prefilledName = "Sibel",
            relationship = "spouse",
            status = status,
            createdAt = Instant.parse("2026-10-11T14:32:00+03:00").toEpochMilliseconds(),
        )

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `Bitti on the poke shows its done note on home for 4 seconds`() =
        runTest(main.dispatcher) {
            doneNotes.post(DoneNote(Cast.TASK_ID, "Para çek", caughtAt = 3))
            ready()
            assertEquals(DoneNoteUi("Para çek", 3), vm.state.value?.doneNote)

            advanceTimeBy(4_001)
            assertNull(vm.state.value?.doneNote)
            assertNull(doneNotes.pending.value, "shown once")
        }

    private suspend fun <T> ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
