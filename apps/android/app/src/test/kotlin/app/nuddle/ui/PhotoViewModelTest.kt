package app.nuddle.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.data.TaskCloser
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.data.photo.PhotoSender
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakePhotoUploads
import app.nuddle.testing.FakeTaskRepository
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aTask
import app.nuddle.testing.sibelConnection
import app.nuddle.ui.screens.photo.CameraSource
import app.nuddle.ui.screens.photo.PhotoStage
import app.nuddle.ui.screens.photo.PhotoViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Taking the photo (docs/ux/screens.md 4d) and the plain Bitti fallback without a camera (docs/domain.md). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val temp = TemporaryFolder()

    private val taskDao = InMemoryTaskDao()
    private val connectionDao = InMemoryConnectionDao()
    private val tasks = FakeTaskRepository()
    private val photoTaken = mutableListOf<String>()
    private val uploads = FakePhotoUploads()

    private val shot: ByteArray =
        ByteArrayOutputStream().use {
            Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
            it.toByteArray()
        }

    private val camera =
        object : CameraSource {
            var next: ByteArray? = shot

            @Composable
            override fun Viewfinder(modifier: Modifier) = Unit

            override suspend fun capture(): ByteArray? = next
        }

    private val closer =
        object : TaskCloser {
            override suspend fun done(
                taskId: String,
                cameraUnavailable: Boolean,
            ) = Unit

            override suspend fun photoTaken(taskId: String) {
                photoTaken += taskId
            }

            override suspend fun cancel(
                taskId: String,
                note: String?,
            ) = Unit
        }

    private val vm by lazy {
        PhotoViewModel(
            SavedStateHandle(mapOf(PhotoViewModel.ARG_TASK to Cast.TASK_ID)),
            taskDao,
            connectionDao,
            camera,
            PhotoSender(PhotoFiles(temp.newFolder("photos")), closer, uploads, IdGenerator(FakeClock()), FakeClock()),
            tasks,
        )
    }

    private suspend fun TestScope.ready() {
        taskDao.upsert(listOf(aTask()))
        connectionDao.upsert(listOf(sibelConnection()))
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `the photo goes to Sibel with the amount she asked for`() =
        runTest(main.dispatcher) {
            ready()
            val state = vm.state.value!!
            assertEquals("Sibel" to "1.000 TL", state.toName to state.detail)
            assertEquals(PhotoStage.Camera, state.stage)
        }

    @Test
    fun `shoot, check it, Gönder stops the pokes, schedules the upload and closes`() =
        runTest(main.dispatcher) {
            ready()
            vm.close.test {
                vm.onShoot()
                assertIs<PhotoStage.Review>(vm.state.value?.stage)
                vm.onSend()
                awaitItem()
            }
            assertEquals(listOf(Cast.TASK_ID), photoTaken)
            assertEquals(listOf(Cast.TASK_ID), uploads.started)
        }

    @Test
    fun `Tekrar çek goes back to the camera, and a failed shot stays there`() =
        runTest(main.dispatcher) {
            ready()
            vm.onShoot()
            vm.onRetake()
            assertEquals(PhotoStage.Camera, vm.state.value?.stage)
            camera.next = null
            vm.onShoot()
            assertEquals(PhotoStage.Camera, vm.state.value?.stage)
            assertEquals(false, vm.state.value?.busy)
        }

    @Test
    fun `without a camera the task can be finished without a photo`() =
        runTest(main.dispatcher) {
            ready()
            vm.onCameraDenied()
            assertEquals(PhotoStage.NoCamera, vm.state.value?.stage)
            vm.close.test {
                vm.onDoneWithoutPhoto()
                awaitItem()
            }
            assertEquals(listOf(Cast.TASK_ID), tasks.doneWithoutCamera)
            assertTrue(photoTaken.isEmpty())
        }
}
