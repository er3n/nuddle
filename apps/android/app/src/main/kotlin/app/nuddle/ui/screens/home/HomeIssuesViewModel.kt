package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.TaskRepository
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * Home's cards that wait for an answer (7e, 5d) and their answers: "Sonradan yaptım", "Ben hallederim", "İptal et",
 * and a new deadline for a dropped or missed task through the 2b picks.
 */
@HiltViewModel
class HomeIssuesViewModel
    @Inject
    constructor(
        sessions: SessionStore,
        taskDao: TaskDao,
        eventDao: TaskEventDao,
        connectionDao: ConnectionDao,
        private val tasks: TaskRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        val issues: StateFlow<IssuesUi> =
            combine(
                sessions.session.map { it?.personId }.filterNotNull(),
                taskDao.observeAll(),
                eventDao.observeAll().map { all -> all.groupBy { it.taskId } },
                connectionDao.observeAll().map { all -> all.associateBy { it.personId } },
            ) { me, all, events, connections ->
                issues(all, events, me, connections, clock.now(), timeZone)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), IssuesUi())

        private val sheet = MutableStateFlow<NewDeadlineUi?>(null)

        /** The new deadline sheet of 5d, while open. */
        val newDeadline: StateFlow<NewDeadlineUi?> = sheet.asStateFlow()

        /** Kept while the same new deadline is retried, so it is not applied twice. */
        private var saveKey: String? = null

        fun onDidItLater(taskId: String) {
            viewModelScope.launch { tasks.didItLater(taskId) }
        }

        fun onSenderHandles(taskId: String) {
            viewModelScope.launch { tasks.senderHandles(taskId) }
        }

        fun onCancel(taskId: String) {
            viewModelScope.launch { tasks.cancel(taskId) }
        }

        fun onNewDeadline(taskId: String) {
            val task = issues.value.sent.firstOrNull { it.taskId == taskId } ?: return
            saveKey = null
            sheet.value = NewDeadlineUi(taskId, task.toName, quickDeadlines(clock.now(), timeZone))
        }

        fun onDeadlineChoice(choice: DeadlineChoice) {
            saveKey = null
            sheet.update { it?.copy(picked = if (it.picked == choice) null else choice, failed = false) }
        }

        fun onTimePicked(time: LocalTime) {
            saveKey = null
            val picked = DeadlineChoice.Picked(nextOccurrence(time, clock.now(), timeZone), time.format(HOUR_MINUTE))
            sheet.update { it?.copy(picked = picked, failed = false) }
        }

        fun onDismiss() {
            sheet.value = null
        }

        fun onSave() {
            val current = sheet.value ?: return
            val deadline = current.picked?.at ?: return
            if (current.saving) return
            val key = saveKey ?: ids.next().also { saveKey = it }
            sheet.value = current.copy(saving = true, failed = false)
            viewModelScope.launch {
                val ok = tasks.newDeadline(current.taskId, deadline, key) is WriteOutcome.Created
                if (ok) saveKey = null
                sheet.update { if (ok) null else it?.copy(saving = false, failed = true) }
            }
        }
    }
