package app.nuddle.ui.screens.photo

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.photo.PhotoFiles
import app.nuddle.data.photo.PhotoUploads
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** 6e: the photo kept on the phone after its upload failed. */
@Immutable
data class UnsentPhotoUiState(
    val toName: String,
    val photo: ImageBitmap?,
)

@HiltViewModel
class UnsentPhotoViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        taskDao: TaskDao,
        connectionDao: ConnectionDao,
        private val files: PhotoFiles,
        private val uploads: PhotoUploads,
    ) : ViewModel() {
        private val taskId: String = checkNotNull(savedState[ARG_TASK])
        private val photo = MutableStateFlow<ImageBitmap?>(null)
        private val closeChannel = Channel<Unit>(Channel.CONFLATED)

        /** Retried, or the photo is no longer here (it went out meanwhile). */
        val close: Flow<Unit> = closeChannel.receiveAsFlow()

        val state: StateFlow<UnsentPhotoUiState?> =
            combine(
                taskDao.observeAll().map { all -> all.firstOrNull { it.id == taskId } },
                connectionDao.observeAll(),
                photo,
            ) { task, connections, photo ->
                task ?: return@combine null
                UnsentPhotoUiState(connections.firstOrNull { it.personId == task.senderId }?.displayName.orEmpty(), photo)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        init {
            viewModelScope.launch {
                val kept = files.of(taskId)
                val bytes = withContext(Dispatchers.IO) { kept.takeIf { it.exists() }?.readBytes() }
                if (bytes == null) {
                    closeChannel.send(Unit)
                } else {
                    photo.value = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
        }

        /** Şimdi tekrar dene: the waiting try goes now, with the same key and moment (ADR 0006). */
        fun onRetry() {
            uploads.uploadSoon(taskId)
            closeChannel.trySend(Unit)
        }

        companion object {
            const val ARG_TASK = "taskId"
        }
    }
