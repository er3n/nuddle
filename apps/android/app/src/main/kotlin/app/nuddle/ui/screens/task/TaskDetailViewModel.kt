package app.nuddle.ui.screens.task

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.HOUR_MINUTE
import app.nuddle.ui.screens.home.deadlineText
import app.nuddle.ui.screens.home.nextOccurrence
import app.nuddle.ui.screens.home.quickDeadlines
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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/** "Ben hallettim", "Gerek kalmadı" (7d). Optional; the chosen one goes to the recipient as the note. */
enum class CancelReason { HandledIt, NotNeeded }

/** The rows of 7c that can be changed; Kime and Detay stay as written (open-questions#34). */
enum class EditRow { Deadline, Finish }

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
    /** The row whose sheet is open, with what is chosen on it so far. */
    val editing: EditRow? = null,
    val deadlineChoices: List<DeadlineChoice> = emptyList(),
    val pickedDeadline: DeadlineChoice? = null,
    val pickedPhoto: Boolean = photo,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
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
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val taskId: String = checkNotNull(savedState[ARG_TASK])

        private data class Local(
            val cancelling: Boolean = false,
            val reason: CancelReason? = null,
            val editing: EditRow? = null,
            val pickedDeadline: DeadlineChoice? = null,
            val pickedPhoto: Boolean? = null,
            val saving: Boolean = false,
            val saveFailed: Boolean = false,
        )

        /** Kept while the same change is retried (ADR 0006). */
        private var editKey: String? = null

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
                // Still going: to do, or asked again for a photo (4g). A photo waiting is answered on 4f.
                if (task == null || task.status !in setOf(TaskStatus.SCHEDULED.value, TaskStatus.REJECTED.value)) {
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
                    editing = local.editing,
                    deadlineChoices = quickDeadlines(clock.now(), timeZone),
                    pickedDeadline = local.pickedDeadline,
                    pickedPhoto = local.pickedPhoto ?: (task.finishMode == FinishMode.PHOTO.value),
                    saving = local.saving,
                    saveFailed = local.saveFailed,
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

        /** A tap on Son zaman or Nasıl bitsin opens that row's sheet. */
        fun onEdit(row: EditRow) {
            editKey = null
            local.update { it.copy(editing = row, pickedDeadline = null, pickedPhoto = null, saveFailed = false) }
        }

        /** A second tap on the chosen deadline clears it (ChoiceChip, docs/ux/components.md 5). */
        fun onDeadlineChoice(choice: DeadlineChoice) {
            editKey = null
            local.update { it.copy(pickedDeadline = if (it.pickedDeadline == choice) null else choice) }
        }

        fun onTimePicked(time: LocalTime) {
            editKey = null
            val picked = DeadlineChoice.Picked(nextOccurrence(time, clock.now(), timeZone), time.format(HOUR_MINUTE))
            local.update { it.copy(pickedDeadline = picked) }
        }

        fun onPhotoChoice(photo: Boolean) {
            editKey = null
            local.update { it.copy(pickedPhoto = photo) }
        }

        fun onEditDismiss() {
            local.update { it.copy(editing = null, saving = false, saveFailed = false) }
        }

        /**
         * Kaydet: the change goes to the server at once (the recipient's plan follows it) and to the recipient as a
         * short note. A failed save keeps the sheet open for another try with the same key.
         */
        fun onEditSave() {
            val current = local.value
            val row = current.editing ?: return
            if (current.saving) return
            val deadline = if (row == EditRow.Deadline) current.pickedDeadline?.at ?: return else null
            val finishMode =
                if (row == EditRow.Finish) (if (state.value?.pickedPhoto == true) FinishMode.PHOTO else FinishMode.DONE) else null
            val key = editKey ?: ids.next().also { editKey = it }
            local.update { it.copy(saving = true, saveFailed = false) }
            viewModelScope.launch {
                val ok = tasks.edit(taskId, deadline, finishMode, key) is WriteOutcome.Created
                if (ok) editKey = null
                local.update { if (ok) it.copy(editing = null, saving = false) else it.copy(saving = false, saveFailed = true) }
            }
        }

        companion object {
            const val ARG_TASK = "taskId"
        }
    }
