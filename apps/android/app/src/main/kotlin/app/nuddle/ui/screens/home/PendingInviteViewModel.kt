package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.InviteRepository
import app.nuddle.domain.IdGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "İptal et" on the pending invite (1g). The card goes once the revoke is synced; when it fails the card simply
 * stays and can be tried again.
 */
@HiltViewModel
class PendingInviteViewModel
    @Inject
    constructor(
        private val invites: InviteRepository,
        private val ids: IdGenerator,
    ) : ViewModel() {
        fun onRevoke(invite: PendingInviteUi) {
            viewModelScope.launch { invites.revoke(invite.inviteId, ids.next()) }
        }
    }
