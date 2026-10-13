package app.nuddle.ui

import app.nuddle.api.model.FinishMode
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
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingErrorReporter
import app.nuddle.testing.aSession
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.screens.home.HomeViewModel
import app.nuddle.ui.screens.home.QueuedUi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Home without a connection (6a), a send that failed (6b) and a server that broke (6f): open-questions#39. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeOfflineTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val tasks = FakeTaskRepository()
    private val connectivity = FakeConnectivity(online = false)
    private val queued = InMemoryQueuedWriteDao()
    private val errors = RecordingErrorReporter()
    private val vm by lazy {
        HomeViewModel(
            sessions,
            InMemoryTaskDao(),
            InMemoryInviteDao(),
            InMemoryConnectionDao(),
            tasks,
            IdGenerator(clock),
            SetupGate(InMemorySetupStore(), FakePermissions()),
            DoneNotes(),
            FakePhotoLoader(),
            FakePhotoUploads(),
            connectivity,
            queued,
            errors,
            clock,
            istanbul,
        )
    }

    private suspend fun TestScope.ready() {
        sessions.saveMe("Eren", QuietHours.default(istanbul))
        backgroundScope.launch { vm.state.collect {} }
    }

    /** Without a network the call gets no answer. */
    private fun write(text: String) {
        tasks.outcome = WriteOutcome.Failed
        vm.onComposeTextChange(text)
        vm.onComposeSend()
    }

    @Test
    fun `offline, the banner shows and a text waits on the phone as Sırada, the bar empty again`() =
        runTest(main.dispatcher) {
            ready()
            write("sibel akşama kadar faturayı ödesin")

            val state = vm.state.value!!
            assertTrue(state.offline)
            assertEquals(ComposeState.Idle, state.compose)
            assertEquals(listOf("sibel akşama kadar faturayı ödesin"), state.queued.map(QueuedUi::text))
        }

    @Test
    fun `a write that gets no answer while there is a network is 6b, not queued`() =
        runTest(main.dispatcher) {
            connectivity.online.value = true
            ready()
            write("para çek")
            assertEquals(ComposeState.Failed("para çek"), vm.state.value?.compose)
            assertEquals(emptyList(), vm.state.value?.queued)
        }

    @Test
    fun `back online, queued texts go out oldest first with the key they got`() =
        runTest(main.dispatcher) {
            ready()
            write("sibel akşama kadar faturayı ödesin")
            clock.now += 1.minutes
            write("yarın 8de spor yap")
            val keys = queued.all.map { it.idempotencyKey }
            tasks.written.clear()
            tasks.outcome = WriteOutcome.Created(Cast.TASK_ID)

            connectivity.online.value = true

            assertEquals(listOf("sibel akşama kadar faturayı ödesin", "yarın 8de spor yap"), tasks.written.map { it.first })
            assertEquals(keys, tasks.written.map { it.second })
            assertEquals(emptyList(), vm.state.value?.queued)
            assertEquals(false, vm.state.value?.offline)
        }

    @Test
    fun `a queued text that needs an answer opens the preview, the next one waits for it`() =
        runTest(main.dispatcher) {
            ready()
            write("para çek")
            clock.now += 1.minutes
            write("yarın 8de spor yap")
            tasks.written.clear()
            tasks.outcome =
                WriteOutcome.Ask(WriteTaskResult.Question.DEADLINE, TaskDraft("Para çek", null, FinishMode.DONE, recipient = "self"))

            connectivity.online.value = true

            assertNotNull(vm.state.value?.preview)
            assertEquals(ComposeState.Typing("para çek"), vm.state.value?.compose)
            assertEquals(listOf("para çek"), tasks.written.map { it.first })
            assertEquals(
                listOf("yarın 8de spor yap"),
                vm.state.value
                    ?.queued
                    ?.map(QueuedUi::text),
            )
        }

    @Test
    fun `a queued text the server could not take stays queued`() =
        runTest(main.dispatcher) {
            ready()
            write("para çek")
            tasks.written.clear()

            connectivity.online.value = true

            assertEquals(1, tasks.written.size)
            assertEquals(
                listOf("para çek"),
                vm.state.value
                    ?.queued
                    ?.map(QueuedUi::text),
            )
        }

    @Test
    fun `6f shows the server's code, Tekrar dene sends the same text with the same key`() =
        runTest(main.dispatcher) {
            connectivity.online.value = true
            ready()
            tasks.outcome = WriteOutcome.Broken("DRT-5001")
            vm.onComposeTextChange("1000 tl çek")
            vm.onComposeSend()
            assertEquals("DRT-5001", vm.state.value?.unrecoverable)
            assertEquals(listOf("DRT-5001"), errors.reported)

            tasks.outcome = WriteOutcome.Created(Cast.TASK_ID)
            vm.onUnrecoverableClose(retry = true)
            assertNull(vm.state.value?.unrecoverable)
            assertEquals(ComposeState.Idle, vm.state.value?.compose)
            assertEquals(tasks.written[0].second, tasks.written[1].second)
        }

    @Test
    fun `6f, Ana ekrana dön leaves the text in the bar with Tekrar dene (6b)`() =
        runTest(main.dispatcher) {
            connectivity.online.value = true
            ready()
            tasks.outcome = WriteOutcome.Broken("DRT-5001")
            vm.onComposeTextChange("1000 tl çek")
            vm.onComposeSend()

            vm.onUnrecoverableClose(retry = false)
            assertNull(vm.state.value?.unrecoverable)
            assertEquals(ComposeState.Failed("1000 tl çek"), vm.state.value?.compose)
            assertEquals(1, tasks.written.size)
        }
}
