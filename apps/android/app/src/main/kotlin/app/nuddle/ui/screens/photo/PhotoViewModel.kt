package app.nuddle.ui.screens.photo

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.TaskRepository
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.photo.GalleryImages
import app.nuddle.data.photo.PhotoSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** 4d: the live camera, the shot to check, or no camera (the plain Bitti fallback, docs/domain.md). */
sealed interface PhotoStage {
    data object Camera : PhotoStage

    data class Review(
        val image: ImageBitmap,
    ) : PhotoStage

    data object NoCamera : PhotoStage
}

@Immutable
data class PhotoUiState(
    val title: String,
    /** Who approves it; null for a task to self, where the photo closes the task (open-questions#7). */
    val toName: String?,
    val detail: String?,
    val stage: PhotoStage = PhotoStage.Camera,
    val busy: Boolean = false,
)

@HiltViewModel
class PhotoViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        taskDao: TaskDao,
        connectionDao: ConnectionDao,
        /** The route renders its viewfinder; shots go through [onShoot]. */
        val camera: CameraSource,
        private val gallery: GalleryImages,
        private val sender: PhotoSender,
        private val tasks: TaskRepository,
    ) : ViewModel() {
        private val taskId: String = checkNotNull(savedState[ARG_TASK])

        private data class Local(
            val stage: PhotoStage = PhotoStage.Camera,
            val busy: Boolean = false,
        )

        /** The shot on the review stage, as it will be sent. */
        private var jpeg: ByteArray? = null

        private val local = MutableStateFlow(Local())
        private val closeChannel = Channel<Unit>(Channel.CONFLATED)

        /** Sent, closed without a photo, or the task is no longer waiting for one. */
        val close: Flow<Unit> = closeChannel.receiveAsFlow()

        val state: StateFlow<PhotoUiState?> =
            combine(
                taskDao.observeAll().map { all -> all.firstOrNull { it.id == taskId } },
                connectionDao.observeAll(),
                local,
            ) { task, connections, local ->
                task ?: return@combine null
                PhotoUiState(
                    title = task.title,
                    toName =
                        if (task.senderId == task.recipientId) {
                            null
                        } else {
                            connections.firstOrNull { it.personId == task.senderId }?.displayName.orEmpty()
                        },
                    detail = task.detail,
                    stage = local.stage,
                    busy = local.busy,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun onShoot() = take { camera.capture() }

        /** "Kamerayı çevir": the other camera (NUD-73). */
        fun onFlip() {
            if (local.value.stage == PhotoStage.Camera) camera.flip()
        }

        /** "Galeriden seç" came back; null when the person picked nothing (NUD-73). */
        fun onPicked(uri: Uri?) {
            uri ?: return
            take { gallery.read(uri) }
        }

        /** A shot or a picked picture: on the review stage when it could be read, else back to the camera. */
        private fun take(bytes: suspend () -> ByteArray?) {
            if (local.value.busy) return
            local.update { it.copy(busy = true) }
            viewModelScope.launch {
                val shot = bytes()
                val image = shot?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                if (shot != null && image != null) {
                    jpeg = shot
                    local.value = Local(PhotoStage.Review(image))
                } else {
                    local.update { it.copy(busy = false) }
                }
            }
        }

        fun onRetake() {
            jpeg = null
            local.value = Local()
        }

        /** The camera permission was refused: a photo task falls back to plain Bitti (docs/domain.md). */
        fun onCameraDenied() {
            local.value = Local(PhotoStage.NoCamera)
        }

        /** Gönder: kept on the phone, pokes stop, the upload follows when there is a network. */
        fun onSend() {
            val shot = jpeg ?: return
            if (local.value.busy) return
            local.update { it.copy(busy = true) }
            viewModelScope.launch {
                sender.send(taskId, shot)
                closeChannel.send(Unit)
            }
        }

        fun onDoneWithoutPhoto() {
            if (local.value.busy) return
            local.update { it.copy(busy = true) }
            viewModelScope.launch {
                tasks.done(taskId, cameraUnavailable = true)
                closeChannel.send(Unit)
            }
        }

        companion object {
            const val ARG_TASK = "taskId"
        }
    }
