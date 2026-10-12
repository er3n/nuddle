package app.nuddle.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.SyncReceivedInvite
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.Ago
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/** Open invites to me, newest first: what the bell shows and what its badge counts. */
internal fun openReceived(invites: List<InviteEntity>): List<InviteEntity> =
    invites.filter { !it.sent && it.status == SyncReceivedInvite.Status.OPEN.value }.sortedByDescending { it.createdAt }

@HiltViewModel
class InboxViewModel
    @Inject
    constructor(
        invites: InviteDao,
        sessions: SessionStore,
        tasks: TaskDao,
        events: TaskEventDao,
        connections: ConnectionDao,
        clock: Clock,
        timeZone: TimeZone,
    ) : ViewModel() {
        private val days =
            combine(
                sessions.session.map { it?.personId }.filterNotNull(),
                tasks.observeAll(),
                events.observeAll(),
                connections.observeAll().map { all -> all.associateBy { it.personId } },
            ) { me, allTasks, allEvents, people -> bellDays(allTasks, allEvents, me, people, clock.now(), timeZone) }

        val state: StateFlow<InboxUiState> =
            combine(invites.observeAll(), days.onStart { emit(emptyList()) }) { all, days ->
                InboxUiState(
                    openReceived(all).map {
                        InboxInviteUi(it.id, it.name, Ago.between(Instant.fromEpochMilliseconds(it.createdAt), clock.now()))
                    },
                    days,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InboxUiState())
    }
