package app.nuddle.ui.screens.task

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.TaskRepository
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.SessionStore
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.deadlineText
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
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/** "Ben hallettim", "Gerek kalmadı" (7d). Optional; the chosen one goes to the recipient as the note. */
enum class CancelReason { HandledIt, NotNeeded }

/** 7c: my task to someone else as I see it. The plan is hidden; only how far pokes reach them shows. */
@Immutable
data class TaskDetailUiState(
    val myName: String,
    val title: String,
    val rawText: String?,
    val toName: String,
    val reachability: Reachability,
    val deadline: DeadlineText,
    val detail: String?,
    val photo: Boolean,
    /** 7d is open. */
    val cancelling: Boolean = false,
    val reason: CancelReason? = null,
)

@HiltViewModel
class TaskDetailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        taskDao: TaskDao,
        connectionDao: ConnectionDao,
        sessions: SessionStore,
        private val tasks: TaskRepository,
        clock: Clock,
        timeZone: TimeZone,
    ) : ViewModel() {
        private val taskId: String = checkNotNull(savedState[ARG_TASK])

        private data class Local(
            val cancelling: Boolean = false,
            val reason: CancelReason? = null,
        )

        private val local = MutableStateFlow(Local())
        private val closeChannel = Channel<Unit>(Channel.CONFLATED)

        /** The task is gone: cancelled here, or closed elsewhere (done, cancelled by sync). */
        val close: Flow<Unit> = closeChannel.receiveAsFlow()

        val state: StateFlow<TaskDetailUiState?> =
            combine(
                taskDao.observeAll().map { all -> all.firstOrNull { it.id == taskId } },
                connectionDao.observeAll(),
                sessions.displayName,
                local,
            ) { task, connections, myName, local ->
                if (task == null || task.status != TaskStatus.SCHEDULED.value) {
                    closeChannel.trySend(Unit)
                    return@combine null
                }
                val to = connections.firstOrNull { it.personId == task.recipientId }
                TaskDetailUiState(
                    myName = myName.orEmpty(),
                    title = task.title,
                    rawText = task.rawText,
                    toName = to?.displayName.orEmpty(),
                    reachability = to?.reachability?.let { Reachability.decode(it) } ?: Reachability.NONE,
                    deadline = deadlineText(Instant.fromEpochMilliseconds(task.deadline), clock.now(), timeZone),
                    detail = task.detail,
                    photo = task.finishMode == FinishMode.PHOTO.value,
                    cancelling = local.cancelling,
                    reason = local.reason,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun onCancel() {
            local.update { it.copy(cancelling = true) }
        }

        /** A second tap on the chosen reason clears it (ChoiceChip, docs/ux/components.md 5). */
        fun onReason(reason: CancelReason) {
            local.update { it.copy(reason = if (it.reason == reason) null else reason) }
        }

        fun onCancelDismiss() {
            local.update { Local() }
        }

        /** İptal et: closed on this phone at once, sent through the outbox; the note is the reason's text. */
        fun onCancelConfirm(note: String?) {
            local.update { it.copy(cancelling = false) }
            viewModelScope.launch { tasks.cancel(taskId, note) }
        }

        companion object {
            const val ARG_TASK = "taskId"
        }
    }
