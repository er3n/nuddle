package app.nuddle.ui.screens.approve

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.Approvals
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.photo.PhotoLoader
import app.nuddle.ui.screens.home.HOUR_MINUTE
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/** Where the photo is: on its way, here, or not to be had (the screen offers to try again). */
sealed interface PhotoView {
    data object Loading : PhotoView

    data class Shown(
        val image: ImageBitmap,
    ) : PhotoView

    data object Failed : PhotoView
}

/** 4f: the photo the recipient sent, with Onayla, or Tekrar iste and an optional note. */
@Immutable
data class ApproveUiState(
    val title: String,
    val fromName: String,
    /** "08:24"; null until sync brings it. */
    val sentAt: String?,
    val detail: String?,
    val photo: PhotoView = PhotoView.Loading,
    val note: String = "",
    val sending: Boolean = false,
)

@HiltViewModel
class ApproveViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        taskDao: TaskDao,
        connectionDao: ConnectionDao,
        private val photos: PhotoLoader,
        private val approvals: Approvals,
        timeZone: TimeZone,
    ) : ViewModel() {
        private val taskId: String = checkNotNull(savedState[ARG_TASK])

        private data class Local(
            val photo: PhotoView = PhotoView.Loading,
            val note: String = "",
            val sending: Boolean = false,
        )

        private val local = MutableStateFlow(Local())
        private val closeChannel = Channel<Unit>(Channel.CONFLATED)

        /** Answered here, or the task no longer waits for an answer (auto-approved, cancelled). */
        val close: Flow<Unit> = closeChannel.receiveAsFlow()

        val state: StateFlow<ApproveUiState?> =
            combine(
                taskDao.observeAll().map { all -> all.firstOrNull { it.id == taskId } },
                connectionDao.observeAll(),
                local,
            ) { task, connections, local ->
                if (task == null || task.status != TaskStatus.AWAITING_APPROVAL.value) {
                    closeChannel.trySend(Unit)
                    return@combine null
                }
                ApproveUiState(
                    title = task.title,
                    fromName = connections.firstOrNull { it.personId == task.recipientId }?.displayName.orEmpty(),
                    sentAt =
                        task.photoSentAt?.let {
                            Instant
                                .fromEpochMilliseconds(it)
                                .toLocalDateTime(timeZone)
                                .time
                                .format(HOUR_MINUTE)
                        },
                    detail = task.detail,
                    photo = local.photo,
                    note = local.note,
                    sending = local.sending,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        init {
            loadPhoto()
        }

        fun loadPhoto() {
            local.update { it.copy(photo = PhotoView.Loading) }
            viewModelScope.launch {
                val image = photos.load(taskId)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                local.update { it.copy(photo = image?.let(PhotoView::Shown) ?: PhotoView.Failed) }
            }
        }

        fun onNoteChange(note: String) {
            local.update { it.copy(note = note) }
        }

        fun onApprove() {
            if (local.value.sending) return
            local.update { it.copy(sending = true) }
            viewModelScope.launch { approvals.approve(taskId) }
        }

        /** Tekrar iste: the note goes with it when there is one. */
        fun onReRequest() {
            if (local.value.sending) return
            local.update { it.copy(sending = true) }
            val note =
                local.value.note
                    .trim()
                    .ifEmpty { null }
            viewModelScope.launch { approvals.reRequest(taskId, note) }
        }

        companion object {
            const val ARG_TASK = "taskId"
        }
    }
