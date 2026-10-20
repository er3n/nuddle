package app.nuddle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.SyncReceivedInvite
import app.nuddle.data.InviteRepository
import app.nuddle.data.db.AnnouncementDao
import app.nuddle.data.db.InviteDao
import app.nuddle.data.session.ConsentPrompts
import app.nuddle.data.sync.Syncer
import app.nuddle.domain.IdGenerator
import app.nuddle.ui.screens.inbox.BellWarningSource
import app.nuddle.ui.screens.inbox.openReceived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L
private const val LINK_WAIT_MS = 5_000L

/**
 * Invites as home sees them: the bell badge (open invites to me, and the warnings of 2g), the consent screen (0f) that opens by itself once
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
        private val syncer: Syncer,
        private val warnings: BellWarningSource,
        announcements: AnnouncementDao,
    ) : ViewModel() {
        private val all = inviteDao.observeAll()
        private val received = all.map(::openReceived)

        /** Open invites to me, the bell's warnings (2g) and unread messages from Nuddle (ADR 0025). */
        val unread: StateFlow<Int> =
            combine(received, warnings.warnings, announcements.observeAll()) { open, warned, news ->
                open.size + warned.size + news.count { it.openedAt == null }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

        /** Back on home, maybe from system settings: the permissions may have changed. */
        fun onResume() = warnings.refresh()

        /** The invite whose consent screen should open now; each one at most once (open-questions#33). */
        val openConsent: Flow<String> =
            received
                .mapNotNull { open -> open.lastOrNull { !prompts.shown(it.id) }?.id }
                .take(1)
                .onEach { prompts.markShown(it) }

        /**
         * An invite link was opened (`nuddle.app/i/{code}`): the open invite to me with that code, after a sync since
         * the link may come before the invite reached this phone. Null when it is used, revoked, not mine or not there,
         * and 6d opens instead (open-questions#39).
         */
        suspend fun inviteForLink(code: String): String? {
            syncer.sync()
            val invite =
                withTimeoutOrNull(LINK_WAIT_MS) {
                    all
                        .map { invites -> invites.firstOrNull { !it.sent && it.code == code } }
                        .filterNotNull()
                        .first()
                }
            return invite?.takeIf { it.status == SyncReceivedInvite.Status.OPEN.value }?.id
        }

        fun onRevoke(invite: PendingInviteUi) {
            viewModelScope.launch { invites.revoke(invite.inviteId, ids.next()) }
        }
    }
