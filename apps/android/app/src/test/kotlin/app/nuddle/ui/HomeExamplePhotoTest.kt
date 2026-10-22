package app.nuddle.ui

import android.graphics.Bitmap
import android.net.Uri
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
import app.nuddle.data.TaskDraft
import app.nuddle.data.WriteOutcome
import app.nuddle.data.example.ExamplePhotos
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
import app.nuddle.ui.screens.home.HomeViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** What the bar records of the example photo (NUD-138). */
private class RecordingExamplePhotos(
    private val picture: ByteArray,
) : ExamplePhotos {
    val attached = mutableListOf<Pair<String, ByteArray>>()
    val queued = mutableMapOf<String, ByteArray>()

    override suspend fun read(uri: Uri): ByteArray = picture

    override suspend fun attach(
        taskId: String,
        jpeg: ByteArray,
    ) {
        attached += taskId to jpeg
    }

    override suspend fun keepWithQueued(
        writeKey: String,
        jpeg: ByteArray,
    ) {
        queued[writeKey] = jpeg
    }

    override suspend fun takeQueued(writeKey: String): ByteArray? = queued.remove(writeKey)
}

/** The sender's example photo in the compose bar (NUD-138, docs/ux/screens.md 2a): attached, sent with the task. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeExamplePhotoTest {
    @get:Rule val main = MainDispatcherRule()

    private val istanbul = TimeZone.of("Europe/Istanbul")
    private val clock = FakeClock(Instant.parse("2026-10-11T20:00:00+03:00"))
    private val sessions = InMemorySessionStore(aSession())
    private val tasks = FakeTaskRepository()
    private val connectivity = FakeConnectivity(online = true)
    private val queued = InMemoryQueuedWriteDao()
    private val jpeg =
        ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    private val examples = RecordingExamplePhotos(jpeg)
    private val picked = Uri.parse("content://media/picker/1")
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
            RecordingErrorReporter(),
            clock,
            istanbul,
            InMemoryTaskEventDao(),
            { flowOf(Unit) },
            RecordingTaskAcks(),
            tasks.finishSwitcher,
            examples,
        )
    }

    private suspend fun TestScope.ready() {
        sessions.saveMe("Sibel", QuietHours.default(istanbul))
        backgroundScope.launch { vm.state.collect {} }
    }

    private fun send(text: String) {
        vm.onComposeTextChange(text)
        vm.onComposeSend()
    }

    @Test
    fun `a picked picture shows in the bar and goes out with the task written on the direct path`() =
        runTest(main.dispatcher) {
            ready()
            vm.composePhoto.onAttach(picked)
            assertNotNull(vm.state.value?.composePhoto)

            send("eren markete gitsin fotodakileri alsın")

            assertEquals(listOf(Cast.TASK_ID), examples.attached.map { it.first })
            assertEquals(
                jpeg.toList(),
                examples.attached
                    .single()
                    .second
                    .toList(),
            )
            assertNull(vm.state.value?.composePhoto, "the bar is empty for the next task")
        }

    @Test
    fun `the ✕ takes it off, and a task written then goes without one`() =
        runTest(main.dispatcher) {
            ready()
            vm.composePhoto.onAttach(picked)
            vm.composePhoto.onRemove()
            assertNull(vm.state.value?.composePhoto)
            send("eren markete gitsin")
            assertEquals(emptyList(), examples.attached)
        }

    @Test
    fun `with a question it stays through the preview and goes with the task Kur creates`() =
        runTest(main.dispatcher) {
            ready()
            tasks.outcome = WriteOutcome.Ask(WriteTaskResult.Question.DEADLINE, TaskDraft("Markete git", null, FinishMode.DONE))
            vm.composePhoto.onAttach(picked)
            send("markete git fotodakileri al")
            assertEquals(emptyList(), examples.attached)
            assertNotNull(vm.state.value?.composePhoto)

            vm.previewSheet.onChoice(
                vm.state.value!!
                    .preview!!
                    .choices
                    .first(),
            )
            vm.onPreviewConfirm()
            assertEquals(listOf(Cast.TASK_ID), examples.attached.map { it.first })
            assertNull(vm.state.value?.composePhoto)
        }

    @Test
    fun `a failed send keeps it in the bar for Tekrar dene`() =
        runTest(main.dispatcher) {
            ready()
            tasks.outcome = WriteOutcome.Failed
            vm.composePhoto.onAttach(picked)
            send("eren markete gitsin")
            assertNotNull(vm.state.value?.composePhoto)
            assertEquals(emptyList(), examples.attached)
        }

    @Test
    fun `offline it waits with the text and goes with the task once the text is sent`() =
        runTest(main.dispatcher) {
            connectivity.online.value = false
            ready()
            tasks.outcome = WriteOutcome.Failed
            vm.composePhoto.onAttach(picked)
            send("eren markete gitsin fotodakileri alsın")
            val key = queued.all.single().idempotencyKey
            assertEquals(setOf(key), examples.queued.keys)
            assertNull(vm.state.value?.composePhoto)

            tasks.outcome = WriteOutcome.Created(Cast.TASK_ID)
            connectivity.online.value = true
            assertEquals(listOf(Cast.TASK_ID), examples.attached.map { it.first })
            assertEquals(emptyMap(), examples.queued)
        }
}
