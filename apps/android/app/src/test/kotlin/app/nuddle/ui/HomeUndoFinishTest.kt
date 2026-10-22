package app.nuddle.ui

import app.nuddle.api.model.FinishMode
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
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
import app.nuddle.ui.screens.home.HomeViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The "Bitti" | "Fotoğrafla" switch on the undo toast (4i, NUD-137). The rest of the toast is in [HomeViewModelTest]. */
class HomeUndoFinishTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val taskDao = InMemoryTaskDao()
    private val tasks = FakeTaskRepository()
    private val vm by lazy {
        HomeViewModel(
            sessions,
            taskDao,
            InMemoryInviteDao(),
            InMemoryConnectionDao(),
            tasks,
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
            InMemoryTaskEventDao(),
            { flowOf(Unit) },
            RecordingTaskAcks(),
            tasks.finishSwitcher,
        )
    }

    private suspend fun TestScope.sent(finishMode: String) {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        backgroundScope.launch { vm.state.collect {} }
        taskDao.upsert(listOf(aTask().copy(senderId = Cast.EREN_ID, recipientId = Cast.SIBEL_ID, finishMode = finishMode)))
        vm.onComposeTextChange("sibel yarın 9dan önce 1000 tl çeksin")
        vm.onComposeSend()
    }

    @Test
    fun `one tap sends exactly one edit and keeps the toast, a second tap sends it back`() =
        runTest(main.dispatcher) {
            sent("done")
            assertEquals(
                false,
                vm.state.value
                    ?.undo
                    ?.photo,
            )

            vm.undoFinish.onPhoto(true)
            assertEquals(listOf(Cast.TASK_ID to FinishMode.PHOTO), tasks.finishChanges)
            assertEquals(
                true,
                vm.state.value
                    ?.undo
                    ?.photo,
                "the toast stays, now with Fotoğrafla",
            )

            vm.undoFinish.onPhoto(true)
            assertEquals(1, tasks.finishChanges.size, "a tap on the current choice sends nothing")

            vm.undoFinish.onPhoto(false)
            assertEquals(listOf(Cast.TASK_ID to FinishMode.PHOTO, Cast.TASK_ID to FinishMode.DONE), tasks.finishChanges)
            assertEquals(
                false,
                vm.state.value
                    ?.undo
                    ?.photo,
            )
            assertEquals(emptyList(), tasks.cancelled)
            assertEquals(emptyList(), tasks.rowEdits, "not the online row edit of 7c")
        }

    @Test
    fun `a one-time message has no finish switch to send`() =
        runTest(main.dispatcher) {
            sent("notice")
            assertEquals(
                true,
                vm.state.value
                    ?.undo
                    ?.notice,
            )

            vm.undoFinish.onPhoto(true)
            assertEquals(emptyList(), tasks.finishChanges)
        }

    @Test
    fun `no toast, nothing to switch`() =
        runTest(main.dispatcher) {
            vm.undoFinish.onPhoto(true)
            assertEquals(emptyList(), tasks.finishChanges)
        }
}
