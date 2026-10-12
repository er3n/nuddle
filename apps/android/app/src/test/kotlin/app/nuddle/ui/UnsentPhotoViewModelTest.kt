package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.data.photo.PendingPhoto
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.testing.Cast
import app.nuddle.testing.FakePhotoUploads
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.ui.screens.photo.UnsentPhotoViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** 6e: the photo kept after a failed upload (docs/product.md, Errors). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnsentPhotoViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val temp = TemporaryFolder()

    private val uploads = FakePhotoUploads()

    private fun vm(files: PhotoFiles) =
        UnsentPhotoViewModel(
            SavedStateHandle(mapOf(UnsentPhotoViewModel.ARG_TASK to Cast.TASK_ID)),
            InMemoryTaskDao(),
            InMemoryConnectionDao(),
            files,
            uploads,
        )

    @Test
    fun `Şimdi tekrar dene starts the upload now and closes`() =
        runTest(main.dispatcher) {
            val files = PhotoFiles(temp.newFolder("photos"))
            files.save(Cast.TASK_ID, byteArrayOf(1, 2), PendingPhoto("key-1", Instant.parse("2026-10-12T08:58:00+03:00")))
            val vm = vm(files)
            vm.close.test {
                vm.onRetry()
                awaitItem()
            }
            assertEquals(listOf(Cast.TASK_ID), uploads.started)
        }

    @Test
    fun `a photo that went out meanwhile closes the screen`() =
        runTest(main.dispatcher) {
            vm(PhotoFiles(temp.newFolder("photos"))).close.test { awaitItem() }
        }
}
