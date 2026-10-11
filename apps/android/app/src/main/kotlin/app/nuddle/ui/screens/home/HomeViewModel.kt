package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.TaskDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
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
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock

private const val STOP_TIMEOUT_MS = 5_000L

/** Home (1a, 2a, 2f): the person's name, the task due first, and writing a task to self. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        sessions: SessionStore,
        taskDao: TaskDao,
        private val tasks: TaskRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val compose = MutableStateFlow<ComposeState>(ComposeState.Idle)

        /** Kept while the same text is retried, so a write the server got but did not answer is not created twice. */
        private var pendingKey: String? = null

        private val nextUp =
            combine(sessions.session.map { it?.personId }.filterNotNull(), taskDao.observeAll()) { me, all ->
                nextUp(all, me, clock.now(), timeZone)
            }

        val state: StateFlow<HomeUiState?> =
            combine(sessions.displayName.filterNotNull(), compose, nextUp) { name, compose, nextUp ->
                HomeUiState(myName = name, compose = compose, nextUp = nextUp)
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
                when (tasks.write(text, key)) {
                    WriteOutcome.Created -> {
                        pendingKey = null
                        compose.value = ComposeState.Idle
                    }

                    // The preview (2b) that asks the one missing thing is the next step of slice 1. Until then the
                    // text stays so nothing typed is lost.
                    is WriteOutcome.Ask -> {
                        pendingKey = null
                        compose.value = ComposeState.Typing(text)
                    }

                    WriteOutcome.Failed -> {
                        compose.value = ComposeState.Failed(text)
                    }
                }
            }
        }

        fun onFinishNextUp() {
            val taskId = state.value?.nextUp?.taskId ?: return
            viewModelScope.launch { tasks.done(taskId) }
        }
    }
