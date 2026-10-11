package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.WriteTaskResult
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.ui.components.ComposeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import javax.inject.Inject
import kotlin.time.Clock

private const val STOP_TIMEOUT_MS = 5_000L

/** Home (1a, 2a, 2f): the person's name, the task due first, and writing a task to self. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val sessions: SessionStore,
        taskDao: TaskDao,
        private val tasks: TaskRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val compose = MutableStateFlow<ComposeState>(ComposeState.Idle)
        private val preview = MutableStateFlow<OpenPreview?>(null)

        /** The preview on screen plus what is needed to create the task from it. */
        private data class OpenPreview(
            val ui: PreviewUi,
            val finishMode: FinishMode,
            val key: String,
        )

        /** Kept while the same text is retried, so a write the server got but did not answer is not created twice. */
        private var pendingKey: String? = null

        private val nextUp =
            combine(sessions.session.map { it?.personId }.filterNotNull(), taskDao.observeAll()) { me, all ->
                nextUp(all, me, clock.now(), timeZone)
            }

        val state: StateFlow<HomeUiState?> =
            combine(sessions.displayName.filterNotNull(), compose, nextUp, preview) { name, compose, nextUp, preview ->
                HomeUiState(myName = name, compose = compose, nextUp = nextUp, preview = preview?.ui)
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
                    WriteOutcome.Created -> {
                        pendingKey = null
                        compose.value = ComposeState.Idle
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
                    ui =
                        PreviewUi(
                            rawText = text,
                            title = title,
                            detail = ask.draft.detail,
                            choices = quickDeadlines(clock.now(), timeZone),
                            selected = null,
                            quietStart = quiet.start.format(HOUR_MINUTE),
                            quietEnd = quiet.end.format(HOUR_MINUTE),
                        ),
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

        /** Kur: creates the task. A failure closes the sheet and leaves the text with "Tekrar dene" (6b). */
        fun onPreviewConfirm() {
            val open = preview.value ?: return
            val deadline = open.ui.selected ?: return
            if (open.ui.sending) return
            preview.value = open.copy(ui = open.ui.copy(sending = true))
            viewModelScope.launch {
                val outcome =
                    tasks.createForSelf(
                        title = open.ui.title,
                        detail = open.ui.detail,
                        deadline = deadline.at,
                        finishMode = open.finishMode,
                        rawText = open.ui.rawText,
                        idempotencyKey = open.key,
                    )
                preview.value = null
                compose.value = if (outcome == WriteOutcome.Created) ComposeState.Idle else ComposeState.Failed(open.ui.rawText)
            }
        }

        fun onFinishNextUp() {
            val taskId = state.value?.nextUp?.taskId ?: return
            viewModelScope.launch { tasks.done(taskId) }
        }
    }
