package app.nuddle.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.SyncReceivedInvite
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.domain.Ago
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        clock: Clock,
    ) : ViewModel() {
        val state: StateFlow<InboxUiState> =
            invites
                .observeAll()
                .map { all ->
                    InboxUiState(
                        openReceived(all).map {
                            InboxInviteUi(it.id, it.name, Ago.between(Instant.fromEpochMilliseconds(it.createdAt), clock.now()))
                        },
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InboxUiState())
    }
