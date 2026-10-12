package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.testing.Cast
import app.nuddle.testing.FakePhotoLoader
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingApprovals
import app.nuddle.testing.aTask
import app.nuddle.testing.selinConnection
import app.nuddle.ui.screens.approve.ApproveViewModel
import app.nuddle.ui.screens.approve.PhotoView
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
import kotlin.test.assertIs
import kotlin.time.Instant

/** The sender's approval (docs/ux/screens.md 4f; docs/product.md, Photo and approval). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApproveViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val taskDao = InMemoryTaskDao()
    private val connectionDao = InMemoryConnectionDao()
    private val photos = FakePhotoLoader()
    private val approvals = RecordingApprovals()
    private val vm by lazy {
        ApproveViewModel(
            SavedStateHandle(mapOf(ApproveViewModel.ARG_TASK to Cast.TASK_ID)),
            taskDao,
            connectionDao,
            photos,
            approvals,
            TimeZone.of("Europe/Istanbul"),
        )
    }

    /** Selin's task to Eren, his photo came at 08:24. */
    private val waiting =
        aTask(status = "AWAITING_APPROVAL")
            .copy(senderId = Cast.SELIN_ID, recipientId = Cast.EREN_ID)
            .copy(photoSentAt = Instant.parse("2026-10-12T08:24:00+03:00").toEpochMilliseconds())

    private suspend fun TestScope.ready() {
        taskDao.upsert(listOf(waiting))
        connectionDao.upsert(listOf(selinConnection().copy(personId = Cast.EREN_ID, displayName = "Eren")))
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `shows who sent the photo, when, what was asked, and the photo`() =
        runTest(main.dispatcher) {
            ready()
            val state = vm.state.value!!
            assertEquals(Triple("Eren", "08:24", "1.000 TL"), Triple(state.fromName, state.sentAt, state.detail))
            assertIs<PhotoView.Shown>(state.photo)
        }

    @Test
    fun `Onayla closes the task, and the screen goes once it is no longer waiting`() =
        runTest(main.dispatcher) {
            ready()
            vm.close.test {
                vm.onApprove()
                assertEquals(listOf(Cast.TASK_ID), approvals.approved)
                taskDao.upsert(listOf(waiting.copy(status = "DONE")))
                awaitItem()
            }
        }

    @Test
    fun `Tekrar iste takes the note, a blank one is no note`() =
        runTest(main.dispatcher) {
            ready()
            vm.onNoteChange("  1.500 lazımdı ")
            vm.onReRequest()
            assertEquals(listOf<Pair<String, String?>>(Cast.TASK_ID to "1.500 lazımdı"), approvals.reRequested)
        }

    @Test
    fun `a blank note is sent as none`() =
        runTest(main.dispatcher) {
            ready()
            vm.onNoteChange("   ")
            vm.onReRequest()
            assertEquals(listOf<Pair<String, String?>>(Cast.TASK_ID to null), approvals.reRequested)
        }
}
