package app.nuddle.ui

import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
import app.nuddle.data.TaskDraft
import app.nuddle.data.WriteOutcome
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
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.home.HomeViewModel
import app.nuddle.ui.screens.home.RecipientChoice
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A tap on the Kime row of a deadline-only preview (NUD-108): the extraction may read the person wrong ("beni ara" as a
 * task to self), so the "Kime?" choices of 4a open before Kur. The rest of the preview is in [HomeViewModelTest].
 */
class HomeRecipientTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val tasks = FakeTaskRepository()
    private val connectionDao = InMemoryConnectionDao()
    private val vm by lazy {
        HomeViewModel(
            sessions,
            InMemoryTaskDao(),
            InMemoryInviteDao(),
            connectionDao,
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

    private val sibel = RecipientChoice.Person(Cast.SIBEL_ID, "Sibel")

    private suspend fun TestScope.asked(text: String) {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        backgroundScope.launch { vm.state.collect {} }
        tasks.outcome =
            WriteOutcome.Ask(WriteTaskResult.Question.DEADLINE, TaskDraft("Beni ara", null, FinishMode.DONE, recipient = "self"))
        vm.onComposeTextChange(text)
        vm.onComposeSend()
    }

    @Test
    fun `a tap on Kime turns a task to self into one for a connection before Kur`() =
        runTest(main.dispatcher) {
            connectionDao.upsert(listOf(sibelConnection().copy(reachability = "SOUND")))
            asked("beni ara")
            assertEquals(
                emptyList(),
                vm.state.value
                    ?.preview
                    ?.recipients,
                "Kime is a row until it is tapped",
            )

            vm.previewSheet.onChangeRecipient()
            val preview = vm.state.value?.preview!!
            assertEquals(listOf(RecipientChoice.Self, sibel), preview.recipients)
            assertEquals(RecipientChoice.Self, preview.recipient)

            vm.previewSheet.onRecipient(sibel)
            assertEquals(
                Reachability.SOUND,
                vm.state.value
                    ?.preview
                    ?.reach,
            )
            vm.previewSheet.onChoice(preview.choices.single())
            vm.onPreviewConfirm()
            assertEquals(listOf<String?>(Cast.SIBEL_ID), tasks.createdFor)
        }

    @Test
    fun `without a connection the Kime row offers nothing`() =
        runTest(main.dispatcher) {
            asked("beni ara")
            assertEquals(
                emptyList(),
                vm.state.value
                    ?.preview
                    ?.recipientOptions,
            )
            vm.previewSheet.onChangeRecipient()
            assertEquals(
                emptyList(),
                vm.state.value
                    ?.preview
                    ?.recipients,
            )
        }
}
