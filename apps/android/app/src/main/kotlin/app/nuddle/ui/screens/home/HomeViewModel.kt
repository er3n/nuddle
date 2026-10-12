package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.theme.DurtMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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

/** Home (1a, 2a, 2f): the person's name, the task due first, and writing a task to self. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val sessions: SessionStore,
        private val taskDao: TaskDao,
        private val inviteDao: InviteDao,
        private val tasks: TaskRepository,
        private val ids: IdGenerator,
        private val setupGate: SetupGate,
        private val doneNotes: DoneNotes,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val compose = MutableStateFlow<ComposeState>(ComposeState.Idle)
        private val preview = MutableStateFlow<OpenPreview?>(null)
        private val undo = MutableStateFlow<UndoUi?>(null)
        private val setup = Channel<Unit>(Channel.CONFLATED)

        /**
         * Opens setup (2c to 2e) once the first task to self stands: the undo window is over, or Kur in the preview
         * set it (docs/product.md, Permissions and setup).
         */
        val openSetup: Flow<Unit> = setup.receiveAsFlow()

        /** The waiting done note, shown for 4 s while home is collected (domain.md, Done note). */
        private val doneNote: Flow<DoneNoteUi?> =
            channelFlow {
                doneNotes.pending.collectLatest { note ->
                    if (note == null) {
                        send(null)
                    } else {
                        send(DoneNoteUi(note.title, note.caughtAt))
                        delay(DurtMotion.DONE_NOTE_MS)
                        doneNotes.shown(note)
                    }
                }
            }

        /** The preview on screen plus what is needed to create the task from it, or to edit [editing]. */
        private data class OpenPreview(
            val ui: PreviewUi,
            val finishMode: FinishMode,
            val key: String,
            val editing: String? = null,
        )

        /** Kept while the same text is retried, so a write the server got but did not answer is not created twice. */
        private var pendingKey: String? = null

        private val nextUp =
            combine(sessions.session.map { it?.personId }.filterNotNull(), taskDao.observeAll()) { me, all ->
                nextUp(all, me, clock.now(), timeZone)
            }

        val state: StateFlow<HomeUiState?> =
            combine(
                combine(sessions.displayName.filterNotNull(), compose, nextUp, ::Triple),
                preview,
                undo,
                doneNote,
                inviteDao.observeAll().map { pendingInvite(it, clock.now(), timeZone) },
            ) { (name, compose, nextUp), preview, undo, doneNote, pendingInvite ->
                HomeUiState(
                    myName = name,
                    compose = compose,
                    nextUp = nextUp,
                    preview = preview?.ui,
                    undo = undo,
                    doneNote = doneNote,
                    pendingInvite = pendingInvite,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun onComposeTextChange(text: String) {
            pendingKey = null
            compose.update { if (text.isEmpty()) ComposeState.Idle else ComposeState.Typing(text) }
        }

        fun onComposeSend() {
            val text =
                when (val current = compose.value) {
                    is ComposeState.Typing -> current.text
                    is ComposeState.Failed -> current.text
                    ComposeState.Idle, ComposeState.Sending -> return
                }.trim()
            if (text.isEmpty()) return
            val key = pendingKey ?: ids.next().also { pendingKey = it }
            compose.value = ComposeState.Sending
            viewModelScope.launch {
                when (val outcome = tasks.write(text, key)) {
                    is WriteOutcome.Created -> {
                        pendingKey = null
                        compose.value = ComposeState.Idle
                        // 5 s to take it back or change it (domain.md, Undo toast). Synced right after the write;
                        // without the task on the device there is nothing to describe, and it stays as created.
                        undo.value = taskDao.get(outcome.taskId)?.let { undoFor(it, text, clock.now(), timeZone) }
                    }

                    // The text stays under the preview, so Vazgeç goes back to it.
                    is WriteOutcome.Ask -> {
                        pendingKey = null
                        compose.value = ComposeState.Typing(text)
                        openPreview(text, outcome)
                    }

                    WriteOutcome.Failed -> {
                        compose.value = ComposeState.Failed(text)
                    }
                }
            }
        }

        /**
         * 2b for the deadline question. Asking for the recipient comes with tasks for others (slice 3); a missing
         * title has no design yet, so for both the text simply stays in the bar.
         */
        private suspend fun openPreview(
            text: String,
            ask: WriteOutcome.Ask,
        ) {
            val title = ask.draft?.title
            if (ask.question != WriteTaskResult.Question.DEADLINE || title == null) return
            val quiet = sessions.quietHours() ?: QuietHours.default(timeZone)
            preview.value =
                OpenPreview(
                    ui = previewFor(text, title, ask.draft.detail, quiet, clock.now()),
                    finishMode = ask.draft.finishMode,
                    key = ids.next(),
                )
        }

        /** A second tap on the selected choice clears it (ChoiceChip, docs/ux/components.md 5). */
        fun onPreviewChoice(choice: DeadlineChoice) {
            preview.update { it?.copy(ui = it.ui.copy(selected = if (it.ui.selected == choice) null else choice)) }
        }

        fun onPreviewTimePicked(time: LocalTime) {
            val picked = DeadlineChoice.Picked(nextOccurrence(time, clock.now(), timeZone), time.format(HOUR_MINUTE))
            preview.update { it?.copy(ui = it.ui.copy(selected = picked)) }
        }

        fun onPreviewDismiss() {
            preview.value = null
        }

        /**
         * Kur: creates the task, or moves the deadline of the one being edited. A failed create closes the sheet and
         * leaves the text with "Tekrar dene" (6b).
         */
        fun onPreviewConfirm() {
            val open = preview.value ?: return
            val deadline = open.ui.selected ?: return
            if (open.ui.sending) return
            preview.value = open.copy(ui = open.ui.copy(sending = true))
            viewModelScope.launch {
                val outcome =
                    if (open.editing != null) {
                        tasks.editDeadline(open.editing, deadline.at, open.key)
                    } else {
                        tasks.createForSelf(
                            title = open.ui.title,
                            detail = open.ui.detail,
                            deadline = deadline.at,
                            finishMode = open.finishMode,
                            rawText = open.ui.rawText,
                            idempotencyKey = open.key,
                        )
                    }
                when {
                    outcome is WriteOutcome.Created -> {
                        preview.value = null
                        compose.value = ComposeState.Idle
                        if (setupGate.needed()) setup.send(Unit)
                    }

                    // The task exists: Tekrar dene on the bar would write a second one, so Kur stays for a retry.
                    open.editing != null -> {
                        preview.value = open
                    }

                    else -> {
                        preview.value = null
                        compose.value = ComposeState.Failed(open.ui.rawText)
                    }
                }
            }
        }

        fun onUndo() {
            val shown = undo.value ?: return
            undo.value = null
            viewModelScope.launch { tasks.cancel(shown.taskId) }
        }

        /** Timeout confirms: the task already exists, so the toast just goes. */
        fun onUndoTimeout() {
            undo.value = null
            viewModelScope.launch { if (setupGate.needed()) setup.send(Unit) }
        }

        /** Düzenle: the preview again, now changing the deadline of the task that went out. */
        fun onUndoEdit() {
            val shown = undo.value ?: return
            undo.value = null
            viewModelScope.launch {
                val quiet = sessions.quietHours() ?: QuietHours.default(timeZone)
                preview.value =
                    OpenPreview(
                        ui = previewFor(shown.rawText, shown.title, shown.detail, quiet, clock.now()),
                        finishMode = if (shown.photo) FinishMode.PHOTO else FinishMode.DONE,
                        key = ids.next(),
                        editing = shown.taskId,
                    )
            }
        }

        /** Şimdiden bitti, then the done note for 4 s (domain.md, Done note). */
        fun onFinishNextUp() {
            val task = state.value?.nextUp ?: return
            viewModelScope.launch {
                doneNotes.post(DoneNote(task.taskId, task.title, tasks.done(task.taskId)))
            }
        }
    }
