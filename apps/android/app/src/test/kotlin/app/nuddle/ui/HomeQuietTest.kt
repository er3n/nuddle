package app.nuddle.ui

import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.api.model.RepeatRule
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.DoneNotes
import app.nuddle.data.QuietAsk
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A moment inside the recipient's quiet hours (NUD-120): the preview waits for the end of quiet hours or "Yine de". */
class HomeQuietTest {
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

    private val five = Instant.parse("2026-10-12T05:00:00+03:00")
    private val six = Instant.parse("2026-10-12T06:00:00+03:00")

    private suspend fun TestScope.asked(
        recipient: String = "self",
        repeat: RepeatRule? = null,
    ) {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        backgroundScope.launch { vm.state.collect {} }
        tasks.outcome =
            WriteOutcome.Ask(
                WriteTaskResult.Question.QUIET_HOURS,
                TaskDraft(
                    "Uyan",
                    null,
                    FinishMode.DONE,
                    recipient = recipient,
                    deadline = five,
                    moment = true,
                    missAfterMinutes = 15,
                    leadMinutes = 10,
                    repeat = repeat,
                ),
                QuietAsk("23:00", "06:00", six),
            )
        vm.onComposeTextChange("yarın sabah beni 5te uyandır")
        vm.onComposeSend()
    }

    @Test
    fun `Kur waits for a choice, and the end of quiet hours moves the moment`() =
        runTest(main.dispatcher) {
            asked()
            val preview = vm.state.value?.preview!!
            assertEquals("05:00", preview.quiet?.askedTime)
            assertEquals("06:00", preview.quiet?.suggestedTime)
            assertFalse(preview.ready, "nothing is picked at first")

            vm.previewSheet.onQuiet(keep = false)
            assertTrue(
                vm.state.value
                    ?.preview!!
                    .ready,
            )
            vm.onPreviewConfirm()
            assertEquals(six, tasks.created.single().second)
            assertEquals(true to 15, tasks.createdMoments.single())
        }

    @Test
    fun `Yine de keeps the asked time`() =
        runTest(main.dispatcher) {
            asked()
            vm.previewSheet.onQuiet(keep = false)
            vm.previewSheet.onQuiet(keep = true)
            vm.onPreviewConfirm()
            assertEquals(five, tasks.created.single().second)
            assertEquals(listOf<Int?>(10), tasks.createdLeads, "the lead goes with Kur (NUD-121)")
        }

    @Test
    fun `a second tap clears the choice`() =
        runTest(main.dispatcher) {
            asked()
            vm.previewSheet.onQuiet(keep = false)
            vm.previewSheet.onQuiet(keep = false)
            assertFalse(
                vm.state.value
                    ?.preview!!
                    .ready,
            )
            vm.onPreviewConfirm()
            assertEquals(emptyList(), tasks.created)
        }

    @Test
    fun `a repeating task moves its time with the moment`() =
        runTest(main.dispatcher) {
            asked(repeat = RepeatRule(RepeatRule.Every.DAY, time = "05:00"))
            vm.previewSheet.onQuiet(keep = false)
            vm.onPreviewConfirm()
            assertEquals("06:00", tasks.createdRepeats.single()?.time)
        }

    @Test
    fun `for a connection the preview names them and Gönder creates for them`() =
        runTest(main.dispatcher) {
            connectionDao.upsert(listOf(sibelConnection().copy(reachability = "SOUND")))
            asked(recipient = "Sibel")
            val preview = vm.state.value?.preview!!
            assertEquals("Sibel", preview.to)
            assertEquals(Reachability.SOUND, preview.reach)
            vm.previewSheet.onQuiet(keep = true)
            vm.onPreviewConfirm()
            assertEquals(listOf<String?>(Cast.SIBEL_ID), tasks.createdFor)
        }
}
