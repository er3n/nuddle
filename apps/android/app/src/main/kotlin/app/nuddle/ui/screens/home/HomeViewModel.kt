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
import app.nuddle.data.DoneNotes
import app.nuddle.data.FinishSwitcher
import app.nuddle.data.MinuteTicks
import app.nuddle.data.SetupGate
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.QueuedWriteDao
import app.nuddle.data.db.QueuedWriteEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.delivery
import app.nuddle.data.example.ExamplePhotos
import app.nuddle.data.network.Connectivity
import app.nuddle.data.network.ErrorReporter
import app.nuddle.data.photo.PhotoLoader
import app.nuddle.data.photo.PhotoUploads
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.TaskAcks
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.screens.hourMinute
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
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
        private val uploads: PhotoUploads,
        private val connectivity: Connectivity,
        private val queuedWrites: QueuedWriteDao,
        private val errors: ErrorReporter,
        private val clock: Clock,
        private val timeZone: TimeZone,
        eventDao: TaskEventDao,
        ticks: MinuteTicks,
        private val acks: TaskAcks,
        finishSwitcher: FinishSwitcher,
        examples: ExamplePhotos = ExamplePhotos.None,
    ) : ViewModel() {
        private val compose = MutableStateFlow<ComposeState>(ComposeState.Idle)

        /** The example photo in the bar (NUD-138); the route binds the gallery button and the ✕ to it. */
        internal val composePhoto = ComposePhotoHolder(examples, viewModelScope)

        /** The code 6f shows while it is open. */
        private val unrecoverable = MutableStateFlow<String?>(null)

        /** The preview sheet's choices; the route binds its callbacks to this. */
        internal val previewSheet = PreviewHolder(clock, timeZone)
        private val preview = previewSheet.open
        private val undo = MutableStateFlow<UndoUi?>(null)

        /** The undo toast's "Bitti" | "Fotoğrafla" switch (NUD-137); the route binds its callback to this. */
        internal val undoFinish = UndoFinishSwitch(undo, finishSwitcher, viewModelScope)

        /** The one-line answer to a text that is not a task, above the bar (NUD-52). */
        private val reply = MutableStateFlow<String?>(null)

        /** Bana or Gönderdiklerim (NUD-67); a task just written shows the tab it went to. */
        private val tab = MutableStateFlow(HomeTab.Mine)
        private val setup = Channel<Unit>(Channel.CONFLATED)

        /**
         * Opens setup (2c to 2e) once the first task to self stands: the undo window is over, or Kur in the preview
         * set it (docs/product.md, Permissions and setup). Also as soon as home knows someone connected, for an install
         * that never ran setup: a reinstall of someone who mostly receives tasks would otherwise never see it (NUD-103).
         * Checked again when home takes it: a request that waited while consent ran setup is stale by then.
         */
        val openSetup: Flow<Unit> = setup.receiveAsFlow().filter { setupGate.needed() }

        init {
            viewModelScope.launch {
                connectionDao.observeAll().first { all -> all.any { it.status == ACTIVE } }
                if (setupGate.needed()) setup.send(Unit)
            }
        }

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
                eventDao.observeAll().map { all -> all.groupBy { it.taskId }.mapValues { (_, events) -> events.delivery() } },
                // Read again every minute, so the last hour counts down (NUD-68).
                ticks.ticks(),
            ) { me, all, connections, deliveries, _ ->
                val now = clock.now()
                val next = nextUp(all, me, connections, now, timeZone)
                TasksPart(
                    nextUp = next,
                    later = laterTasks(all, me, connections, next?.taskId, now, timeZone),
                    sent = sentTasks(all, me, connections, now, timeZone, deliveries),
                    connected = connections.isNotEmpty(),
                )
            }

        private data class TasksPart(
            val nextUp: NextUpUi?,
            val later: List<LaterUi>,
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

        /** Whether the upload of a next-up photo has failed and waits for its next try (6e). */
        @OptIn(ExperimentalCoroutinesApi::class) // Reason: flatMapLatest follows the task that is next up now.
        private val nextUpUploadFailing: Flow<Boolean> =
            tasksPart
                .map { part -> part.nextUp?.takeIf { it.status == NextUpStatus.AwaitingApproval && it.photoSentAt == null }?.taskId }
                .distinctUntilChanged()
                .flatMapLatest { taskId -> taskId?.let { uploads.failing(it) } ?: flowOf(false) }

        val state: StateFlow<HomeUiState?> =
            combine(
                combine(sessions.displayName.filterNotNull(), compose, tasksPart, nextUpPhoto, nextUpUploadFailing) {
                    name,
                    compose,
                    part,
                    photo,
                    failing,
                    ->
                    Triple(name, compose, part.copy(nextUp = part.nextUp?.copy(uploadFailed = failing), photo = photo))
                },
                preview,
                undo,
                doneNote,
                combine(
                    inviteDao.observeAll().map { pendingInvite(it, clock.now(), timeZone) },
                    connectivity.online,
                    queuedWrites.observeAll().map { all -> all.map { QueuedUi(it.idempotencyKey, it.text) } },
                    combine(unrecoverable, reply, tab, composePhoto.image, ::Notes),
                    ::Extras,
                ),
            ) { (name, compose, tasksPart), preview, undo, doneNote, extras ->
                HomeUiState(
                    myName = name,
                    compose = compose,
                    nextUp = tasksPart.nextUp,
                    nextUpPhoto = tasksPart.photo,
                    later = tasksPart.later,
                    sent = tasksPart.sent,
                    connected = tasksPart.connected,
                    preview = preview?.ui,
                    undo = undo,
                    doneNote = doneNote,
                    pendingInvite = extras.pendingInvite,
                    offline = !extras.online,
                    queued = extras.queued,
                    unrecoverable = extras.notes.unrecoverable,
                    reply = extras.notes.reply,
                    tab = extras.notes.tab,
                    composePhoto = extras.notes.photo,
                )
            }.onEach { home ->
                // Tasks from others on Bana while home is on screen: their senders see "gördü" (NUD-68).
                seenOnBana(home).takeIf { it.isNotEmpty() }?.let { viewModelScope.launch { acks.seen(it) } }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private data class Extras(
            val pendingInvite: PendingInviteUi?,
            val online: Boolean,
            val queued: List<QueuedUi>,
            val notes: Notes,
        )

        private data class Notes(
            val unrecoverable: String?,
            val reply: String?,
            val tab: HomeTab,
            val photo: ImageBitmap?,
        )

        init {
            // Texts written offline go out oldest first once the phone is online, the bar is empty and no preview is
            // open: one that needs an answer takes the bar and the preview (open-questions#39).
            viewModelScope.launch {
                combine(connectivity.online, queuedWrites.observeAll(), compose, preview) { online, queued, bar, open ->
                    queued.firstOrNull()?.takeIf { online && bar == ComposeState.Idle && open == null }
                }.distinctUntilChanged()
                    .collect { next -> next?.let { sendQueued(it) } }
            }
        }

        fun onComposeTextChange(text: String) {
            pendingKey = null
            reply.value = null
            compose.update { if (text.isEmpty()) ComposeState.Idle else ComposeState.Typing(text) }
        }

        /** The text the bar would send: typed, or kept after a failure; null while there is none to send. */
        private val sendable: String?
            get() =
                when (val current = compose.value) {
                    is ComposeState.Typing -> current.text
                    is ComposeState.Failed -> current.text
                    ComposeState.Idle, is ComposeState.Sending, is ComposeState.Listening -> null
                }?.trim()?.takeIf { it.isNotEmpty() }

        fun onComposeSend() {
            val text = sendable ?: return
            reply.value = null
            val key = pendingKey ?: ids.next().also { pendingKey = it }
            val photo = composePhoto.current
            compose.value = ComposeState.Sending(text)
            viewModelScope.launch {
                when (val outcome = tasks.write(text, key)) {
                    is WriteOutcome.Created -> {
                        pendingKey = null
                        compose.value = ComposeState.Idle
                        composePhoto.sent(photo, outcome.taskId)
                        // 5 s to take it back or change it (domain.md, Undo toast). Synced right after the write;
                        // without the task on the device there is nothing to describe, and it stays as created.
                        undo.value =
                            taskDao.get(outcome.taskId)?.let { task ->
                                // Null for a task to self: "Kuruldu" instead of "Gönderildi" (4i).
                                val to = if (task.recipientId == task.senderId) null else connectionDao.get(task.recipientId)?.displayName
                                undoFor(task, text, to, clock.now(), timeZone)
                            }
                        undo.value?.let { tab.value = if (it.to == null) HomeTab.Mine else HomeTab.Sent }
                    }

                    // Not a task: the answer replaces the text, nothing was created (NUD-52).
                    is WriteOutcome.Chat -> {
                        pendingKey = null
                        compose.value = ComposeState.Idle
                        reply.value = outcome.reply
                    }

                    // The text stays under the preview, so Vazgeç goes back to it.
                    is WriteOutcome.Ask -> {
                        pendingKey = null
                        compose.value = ComposeState.Typing(text)
                        openPreview(text, outcome)
                    }

                    // 6a without a network: only the server can understand the text, so it waits on the phone with its
                    // key. With one, the text stays for Tekrar dene (6b); over the daily cap 6b says when (ADR 0024).
                    WriteOutcome.Failed, is WriteOutcome.Limited -> {
                        if (outcome.staysInBar(connectivity.online.value)) {
                            compose.value = ComposeState.Failed(text, outcome.limitUntil(timeZone))
                        } else {
                            pendingKey = null
                            compose.value = ComposeState.Idle
                            composePhoto.queued(photo, key)
                            queuedWrites.add(QueuedWriteEntity(key, text, clock.now().toEpochMilliseconds()))
                        }
                    }

                    // 6f over the text, which stays in the bar for when the person goes back home.
                    is WriteOutcome.Broken -> {
                        compose.value = ComposeState.Failed(text)
                        unrecoverable.value = outcome.code
                        errors.report(outcome.code)
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
            val photo = composePhoto.current.takeIf { open.editing == null }
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
                            moment = open.ui.knownDeadline?.moment == true,
                            missAfterMinutes = open.ui.missAfterMinutes,
                            repeat = open.ui.repeat?.toRule(),
                            leadMinutes = open.ui.leadMinutes,
                        )
                    }
                when {
                    outcome is WriteOutcome.Created -> {
                        preview.value = null
                        compose.value = ComposeState.Idle
                        composePhoto.sent(photo, outcome.taskId)
                        tab.value = if (open.ui.toOther) HomeTab.Sent else HomeTab.Mine
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
                        if (outcome is WriteOutcome.Broken) {
                            unrecoverable.value = outcome.code
                            errors.report(outcome.code)
                        }
                    }
                }
            }
        }

        /** 6f: Tekrar dene sends the text in the bar again with its key; Ana ekrana dön leaves it there (6b). */
        fun onUnrecoverableClose(retry: Boolean) {
            unrecoverable.value = null
            if (retry) onComposeSend()
        }

        /** A text written offline, sent with the key it got then. Not understood or failed: it stays queued. */
        private suspend fun sendQueued(entry: QueuedWriteEntity) {
            when (val outcome = tasks.write(entry.text, entry.idempotencyKey)) {
                is WriteOutcome.Created -> {
                    queuedWrites.remove(entry.idempotencyKey)
                    composePhoto.queuedSent(entry.idempotencyKey, outcome.taskId)
                }

                // Not a task: the picture comes back to the bar for the next try.
                is WriteOutcome.Chat -> {
                    queuedWrites.remove(entry.idempotencyKey)
                    reply.value = outcome.reply
                    composePhoto.queuedBack(entry.idempotencyKey)
                }

                is WriteOutcome.Ask -> {
                    queuedWrites.remove(entry.idempotencyKey)
                    composePhoto.queuedBack(entry.idempotencyKey)
                    compose.value = ComposeState.Typing(entry.text)
                    openPreview(entry.text, outcome)
                }

                // Not understood or the server failed: it waits for the next connection.
                WriteOutcome.Failed, is WriteOutcome.Broken, is WriteOutcome.Limited -> {}
            }
        }

        /** The reply note's time is up. */
        fun onReplyTimeout() {
            reply.value = null
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
                        ui =
                            previewFor(shown.rawText, shown.title, shown.detail, quiet, clock.now())
                                .copy(to = shown.to, notice = shown.notice),
                        finishMode =
                            when {
                                shown.notice -> FinishMode.NOTICE
                                shown.photo -> FinishMode.PHOTO
                                else -> FinishMode.DONE
                            },
                        key = ids.next(),
                        editing = shown.taskId,
                    )
            }
        }

        /** A tap on a tab or a swipe (NUD-67). */
        fun onTab(tab: HomeTab) {
            this.tab.value = tab
        }
    }

/**
 * The tasks from others that the Bana tab shows (NUD-68). Home's state is only collected while home is started, so a task
 * is never told as seen from the background.
 */
private fun seenOnBana(state: HomeUiState): List<String> =
    if (state.tab != HomeTab.Mine) {
        emptyList()
    } else {
        listOfNotNull(state.nextUp?.takeIf { it.from != null }?.taskId) + state.later.filter { it.fromId != null }.map { it.taskId }
    }

/** "21:15" for a write refused over the daily cap (6b, open-questions#54), else null. */
private fun WriteOutcome.limitUntil(timeZone: TimeZone): String? = (this as? WriteOutcome.Limited)?.until?.hourMinute(timeZone)

/** A failed write waits on the phone only without a network; a refusal over the cap came from the server. */
private fun WriteOutcome.staysInBar(online: Boolean): Boolean = online || this is WriteOutcome.Limited
