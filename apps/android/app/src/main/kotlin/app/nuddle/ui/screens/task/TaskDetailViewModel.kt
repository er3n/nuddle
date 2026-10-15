package app.nuddle.ui.screens.task

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.data.Delivery
import app.nuddle.data.DoneNotes
import app.nuddle.data.EventTypes
import app.nuddle.data.TaskCloser
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.db.shownTime
import app.nuddle.data.delivery
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.TaskAcks
import app.nuddle.data.sinceLastDeadline
import app.nuddle.domain.IdGenerator
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.HOUR_MINUTE
import app.nuddle.ui.screens.home.MissedUi
import app.nuddle.ui.screens.home.NewDeadlineUi
import app.nuddle.ui.screens.home.RepeatUi
import app.nuddle.ui.screens.home.SentIssueUi
import app.nuddle.ui.screens.home.issues
import app.nuddle.ui.screens.home.nextOccurrence
import app.nuddle.ui.screens.home.quickDeadlines
import app.nuddle.ui.screens.home.repeat
import app.nuddle.ui.screens.home.timeText
import app.nuddle.ui.screens.home.writtenText
import app.nuddle.ui.screens.poke.DropUi
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
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/** "Ben hallettim", "Gerek kalmadı" (7d). Optional; the chosen one goes to the recipient as the note. */
enum class CancelReason { HandledIt, NotNeeded }

/** The rows of 7c that can be changed; Kime and Detay stay as written (open-questions#34). */
enum class EditRow { Deadline, Finish }

/**
 * The detail of an open task from either side (docs/ux/screens.md, Task detail; 7c for my task to someone else). The
 * plan is hidden; only how far pokes reach the recipient shows.
 */
@Immutable
data class TaskDetailUiState(
    val myName: String,
    val title: String,
    val rawText: String?,
    /** The recipient of my task; empty otherwise. */
    val toName: String,
    val reachability: Reachability,
    /** A task to self, opened from home (NUD-47): no recipient to tell or reach. */
    val self: Boolean = false,
    val deadline: DeadlineText,
    val detail: String?,
    val photo: Boolean,
    /** A one-time message (NUD-79): "Tamam" only, no Bugün olmaz, no edit of how it ends. */
    val notice: Boolean = false,
    val side: DetailSide = if (self) DetailSide.ToSelf else DetailSide.Mine,
    val status: DetailStatus = DetailStatus.Scheduled,
    /** Who wrote a task to me, when, and their number for Ara and Mesaj (4c). */
    val from: FromUi? = null,
    /** "22:37": a snooze I chose is on until then (NUD-63). */
    val snoozedUntil: String? = null,
    /** The sender's note with Tekrar iste (4g). */
    val reRequestNote: String? = null,
    /** "08:24": when my photo went out (4e). */
    val photoSentAt: String? = null,
    /** A missed task to me or to self (7e). */
    val missed: MissedUi? = null,
    /** My task to someone that they dropped or missed (5d). */
    val sentIssue: SentIssueUi? = null,
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
    /** 7f, while open. */
    val drop: DropUi? = null,
    /** The 5d deadline sheet, while open. */
    val newDeadline: NewDeadlineUi? = null,
    /** My task to someone: on their phone, or seen by them (NUD-68). */
    val delivery: Delivery? = null,
    /** "09:00": a task to me that has not rung yet starts poking then (NUD-68). */
    val pokeStartsAt: String? = null,
    /** My task to someone: the pokes that rang so far, once one has (NUD-81). */
    val pokesSoFar: PokesSoFar? = null,
    /** The sheet that moves the start (NUD-78); open after a tap on the start line. */
    val start: NewDeadlineUi? = null,
    /** The task's own time (the moment of an `at` task, else the deadline): the latest start allowed. */
    val taskTime: Instant? = null,
    /** A repeating task's rule (ADR 0021): the "Tekrar" row; null for a one-off. */
    val repeat: RepeatUi? = null,
) {
    val actions: List<DetailAction> get() = actionsFor(side, status, photo, notice)
    val editable: Boolean get() = editable(side, status)
}

@HiltViewModel
class TaskDetailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        taskDao: TaskDao,
        connectionDao: ConnectionDao,
        eventDao: TaskEventDao,
        stepDao: PokeStepDao,
        sessions: SessionStore,
        private val acks: TaskAcks,
        doneNotes: DoneNotes,
        closer: TaskCloser,
        private val tasks: TaskRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        val taskId: String = checkNotNull(savedState[ARG_TASK])

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

        /** The one-tap answers: Şimdiden bitti, Sonradan yaptım, Ben hallederim and the like. */
        val answers = TaskAnswers(taskId, tasks, closer, doneNotes, viewModelScope)

        /** 7f over the detail. */
        val drop = DropSheetHolder()

        /** The detail of a task to me is on screen: its sender sees "gördü" (NUD-68). */
        private val seen = SeenOnce(taskId, acks, viewModelScope)

        /** Yeni zaman ver and Süreyi uzat (5d). */
        val newDeadline = NewDeadlineHolder(taskId, tasks, ids, clock, timeZone, viewModelScope)

        /** The recipient moves when the pokes begin (NUD-78). */
        val start = StartHolder(taskId, tasks, ids, clock, timeZone, viewModelScope)

        /** The task is gone from my lists: answered here, or closed elsewhere (done, cancelled by sync). */
        val close: Flow<Unit> = closeChannel.receiveAsFlow()

        private val sheets = combine(local, drop.open, newDeadline.open, start.open, ::Sheets)

        private data class Sheets(
            val local: Local,
            val drop: DropUi?,
            val newDeadline: NewDeadlineUi?,
            val start: NewDeadlineUi?,
        )

        val state: StateFlow<TaskDetailUiState?> =
            combine(
                taskDao.observeAll().map { all -> all.firstOrNull { it.id == taskId } },
                connectionDao.observeAll(),
                combine(
                    eventDao.observeAll().map { all -> all.filter { it.taskId == taskId } },
                    stepDao.observeAll().map { all -> all.filter { it.taskId == taskId } },
                    ::Pair,
                ),
                combine(sessions.session.map { it?.personId }, sessions.displayName, ::Pair),
                sheets,
            ) { task, connections, (events, steps), (me, myName), sheets ->
                val shown = if (task == null || me == null) null else detailOf(task, me)
                if (task == null || me == null || shown == null) {
                    closeChannel.trySend(Unit)
                    return@combine null
                }
                val (side, status) = shown
                if (side == DetailSide.ToMe) seen.tell()
                val now = clock.now()
                val byId = connections.associateBy { it.personId }
                val to = byId[task.recipientId]
                val sender = byId[task.senderId]?.takeIf { side == DetailSide.ToMe }
                val issues = issues(listOf(task), mapOf(taskId to events), me, byId, now, timeZone)
                val local = sheets.local
                TaskDetailUiState(
                    myName = myName.orEmpty(),
                    title = task.title,
                    rawText = task.rawText,
                    toName =
                        to
                            ?.displayName
                            .orEmpty()
                            .takeIf { side == DetailSide.Mine }
                            .orEmpty(),
                    reachability = to?.reachability?.let { Reachability.decode(it) } ?: Reachability.NONE,
                    self = side == DetailSide.ToSelf,
                    deadline = task.timeText(now, timeZone),
                    detail = task.detail,
                    photo = task.finishMode == FinishMode.PHOTO.value,
                    notice = task.finishMode == FinishMode.NOTICE.value,
                    repeat = task.repeat,
                    side = side,
                    status = status,
                    from =
                        sender?.let {
                            FromUi(it.displayName, writtenText(Instant.fromEpochMilliseconds(task.createdAt), now, timeZone), it.phone)
                        },
                    snoozedUntil =
                        task.snoozedUntil
                            ?.let(Instant::fromEpochMilliseconds)
                            ?.takeIf { it > now }
                            ?.hourMinute(),
                    reRequestNote = task.reRequestNote,
                    photoSentAt = task.photoSentAt?.let(Instant::fromEpochMilliseconds)?.hourMinute(),
                    missed = issues.missed.firstOrNull(),
                    sentIssue = issues.sent.firstOrNull(),
                    cancelling = local.cancelling,
                    reason = local.reason,
                    editing = local.editing,
                    deadlineChoices = quickDeadlines(now, timeZone),
                    pickedDeadline = local.pickedDeadline,
                    pickedPhoto = local.pickedPhoto ?: (task.finishMode == FinishMode.PHOTO.value),
                    saving = local.saving,
                    saveFailed = local.saveFailed,
                    drop = sheets.drop,
                    newDeadline = sheets.newDeadline,
                    start = sheets.start,
                    taskTime = Instant.fromEpochMilliseconds(task.shownTime),
                    delivery = events.delivery().takeIf { side == DetailSide.Mine },
                    pokesSoFar =
                        events
                            .sinceLastDeadline()
                            .filter { it.type == EventTypes.POKE_FIRED }
                            .takeIf { side == DetailSide.Mine && status.open && it.isNotEmpty() }
                            ?.let { fired -> PokesSoFar(fired.size, Instant.fromEpochMilliseconds(fired.maxOf { it.at }).hourMinute()) },
                    pokeStartsAt =
                        pokeStart(steps, task.planVersion, now)
                            ?.takeIf { side != DetailSide.Mine && status == DetailStatus.Scheduled && task.snoozedUntil == null }
                            ?.hourMinute(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private fun Instant.hourMinute(): String =
            toLocalDateTime(timeZone)
                .time
                .format(HOUR_MINUTE)

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
