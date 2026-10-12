package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.InviteRepository
import app.nuddle.data.db.InviteDao
import app.nuddle.data.session.ConsentPrompts
import app.nuddle.domain.IdGenerator
import app.nuddle.ui.screens.inbox.openReceived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Invites as home sees them: the bell badge (open invites to me), the consent screen (0f) that opens by itself once
 * per invite right after sign-in, and "İptal et" on my pending invite (1g).
 */
@HiltViewModel
class HomeInvitesViewModel
    @Inject
    constructor(
        inviteDao: InviteDao,
        private val prompts: ConsentPrompts,
        private val invites: InviteRepository,
        private val ids: IdGenerator,
    ) : ViewModel() {
        private val received = inviteDao.observeAll().map(::openReceived)

        val unread: StateFlow<Int> =
            received.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

        /** The invite whose consent screen should open now; each one at most once (TODO(open-questions#33)). */
        val openConsent: Flow<String> =
            received
                .mapNotNull { open -> open.lastOrNull { !prompts.shown(it.id) }?.id }
                .take(1)
                .onEach { prompts.markShown(it) }

        fun onRevoke(invite: PendingInviteUi) {
            viewModelScope.launch { invites.revoke(invite.inviteId, ids.next()) }
        }
    }
