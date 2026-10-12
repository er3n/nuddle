package app.nuddle.ui.screens.home

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.Reachability
import app.nuddle.api.model.SyncConnection
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.DoneNote
import app.nuddle.data.DoneNotes
import app.nuddle.data.SetupGate
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.photo.PhotoLoader
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.theme.DurtMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
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
private val ACTIVE = SyncConnection.Status.ACTIVE.value

/** Home (1a, 2a, 2f, 4c): the person's name, the task due first (to self or from someone), and writing a task. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val sessions: SessionStore,
        private val taskDao: TaskDao,
        private val inviteDao: InviteDao,
        private val connectionDao: ConnectionDao,
        private val tasks: TaskRepository,
        private val ids: IdGenerator,
        private val setupGate: SetupGate,
        private val doneNotes: DoneNotes,
        private val photos: PhotoLoader,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val compose = MutableStateFlow<ComposeState>(ComposeState.Idle)

        /** The preview sheet's choices; the route binds its callbacks to this. */
        internal val previewSheet = PreviewHolder(clock, timeZone)
        private val preview = previewSheet.open
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

        /** Kept while the same text is retried, so a write the server got but did not answer is not created twice. */
        private var pendingKey: String? = null

        /** The card due first, my tasks to others, and whether I have anyone to write to (no invite card then). */
        private val tasksPart =
            combine(
                sessions.session.map { it?.personId }.filterNotNull(),
                taskDao.observeAll(),
                connectionDao.observeAll().map { all -> all.filter { it.status == ACTIVE }.associateBy { it.personId } },
            ) { me, all, connections ->
                TasksPart(
                    nextUp = nextUp(all, me, connections, clock.now(), timeZone),
                    sent = sentTasks(all, me, connections, clock.now(), timeZone),
                    connected = connections.isNotEmpty(),
                )
            }

        private data class TasksPart(
            val nextUp: NextUpUi?,
            val sent: List<SentUi>,
            val connected: Boolean,
            val photo: ImageBitmap? = null,
        )

        /** The photo of a next-up task waiting for approval (4e), loaded once per task. */
        @OptIn(ExperimentalCoroutinesApi::class) // Reason: mapLatest drops a load for a task that is no longer next up.
        private val nextUpPhoto: Flow<ImageBitmap?> =
            tasksPart
                .map { part -> part.nextUp?.takeIf { it.status == NextUpStatus.AwaitingApproval }?.taskId }
                .distinctUntilChanged()
                .mapLatest { taskId ->
                    taskId?.let { photos.load(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }.onStart { emit(null) }

        val state: StateFlow<HomeUiState?> =
            combine(
                combine(sessions.displayName.filterNotNull(), compose, tasksPart, nextUpPhoto) { name, compose, part, photo ->
                    Triple(name, compose, part.copy(photo = photo))
                },
                preview,
                undo,
                doneNote,
                inviteDao.observeAll().map { pendingInvite(it, clock.now(), timeZone) },
            ) { (name, compose, tasksPart), preview, undo, doneNote, pendingInvite ->
                HomeUiState(
                    myName = name,
                    compose = compose,
                    nextUp = tasksPart.nextUp,
                    nextUpPhoto = tasksPart.photo,
                    sent = tasksPart.sent,
                    connected = tasksPart.connected,
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
                        undo.value =
                            taskDao.get(outcome.taskId)?.let { task ->
                                // Null for a task to self: "Kuruldu" instead of "Gönderildi" (4i).
                                val to = if (task.recipientId == task.senderId) null else connectionDao.get(task.recipientId)?.displayName
                                undoFor(task, text, to, clock.now(), timeZone)
                            }
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
         * 2b or 4a for the deadline or the recipient question. A missing title has no design yet, nor a recipient the
         * text names but no connection matches: the text simply stays in the bar then.
         */
        private suspend fun openPreview(
            text: String,
            ask: WriteOutcome.Ask,
        ) {
            val quiet = sessions.quietHours() ?: QuietHours.default(timeZone)
            val connections = connectionDao.observeAll().first().filter { it.status == ACTIVE }
            preview.value =
                previewFor(text, ask, connections, quiet, clock.now())?.let { (ui, knownDeadline) ->
                    OpenPreview(
                        ui = ui,
                        finishMode = ask.draft?.finishMode ?: FinishMode.DONE,
                        key = ids.next(),
                        knownDeadline = knownDeadline,
                        reaches = connections.associate { it.personId to (Reachability.decode(it.reachability) ?: Reachability.NONE) },
                    )
                }
        }

        /**
         * Kur: creates the task, or moves the deadline of the one being edited. A failed create closes the sheet and
         * leaves the text with "Tekrar dene" (6b).
         */
        fun onPreviewConfirm() {
            val open = preview.value ?: return
            val deadline = open.deadline ?: return
            if (open.ui.sending || !open.ui.ready) return
            preview.value = open.copy(ui = open.ui.copy(sending = true))
            viewModelScope.launch {
                val outcome =
                    if (open.editing != null) {
                        tasks.editDeadline(open.editing, deadline, open.key)
                    } else {
                        tasks.create(
                            title = open.ui.title,
                            detail = open.ui.detail,
                            deadline = deadline,
                            finishMode = open.chosenFinishMode,
                            rawText = open.ui.rawText,
                            idempotencyKey = open.key,
                            recipientId = (open.ui.recipient as? RecipientChoice.Person)?.personId,
                        )
                    }
                when {
                    outcome is WriteOutcome.Created -> {
                        preview.value = null
                        compose.value = ComposeState.Idle
                        // Setup follows a task to self only (open-questions#30).
                        if (!open.ui.toOther && setupGate.needed()) setup.send(Unit)
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

        /** Timeout confirms: the task already exists, so the toast just goes. Setup follows a task to self only. */
        fun onUndoTimeout() {
            val shown = undo.value
            undo.value = null
            if (shown?.to != null) return
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
                        ui = previewFor(shown.rawText, shown.title, shown.detail, quiet, clock.now()).copy(to = shown.to),
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
