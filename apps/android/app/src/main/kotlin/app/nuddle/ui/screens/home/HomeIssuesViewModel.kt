package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.session.DismissedRows
import app.nuddle.data.session.SessionStore
import app.nuddle.ui.screens.inbox.openUnreached
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * What on home waits for an answer (7e, 5d) and the ✕ of an "iptal etti" row (NUD-67). The answers themselves are in
 * the task detail.
 */
@HiltViewModel
class HomeIssuesViewModel
    @Inject
    constructor(
        sessions: SessionStore,
        taskDao: TaskDao,
        eventDao: TaskEventDao,
        connectionDao: ConnectionDao,
        private val dismissedRows: DismissedRows,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val me = sessions.session.map { it?.personId }.filterNotNull()
        private val events = eventDao.observeAll().map { all -> all.groupBy { it.taskId } }

        val issues: StateFlow<IssuesUi> =
            combine(
                me,
                taskDao.observeAll(),
                events,
                connectionDao.observeAll().map { all -> all.associateBy { it.personId } },
                dismissedRows.dismissed,
            ) { me, all, events, connections, dismissed ->
                val found = issues(all, events, me, connections, clock.now(), timeZone)
                found.copy(cancelled = found.cancelled.filter { it.taskId !in dismissed })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), IssuesUi())

        /** "Cevap vermiyor" entries in the bell that still matter (7b): part of the bell's badge. */
        val unreached: StateFlow<Int> =
            combine(me, taskDao.observeAll(), events) { me, all, events -> openUnreached(all, events, me) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

        /** The ✕ of an "iptal etti" row: off this phone's home; the task stays cancelled. */
        fun onDismissCancelled(taskId: String) {
            viewModelScope.launch { dismissedRows.dismiss(taskId) }
        }
    }
