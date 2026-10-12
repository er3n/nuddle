package app.nuddle.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.EdgeType
import app.nuddle.data.AccountRepository
import app.nuddle.data.AuthRepository
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.session.Profile
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.PermissionReader
import app.nuddle.domain.TurkishPhone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** A switch on its way (shown as switched), and whether the last change failed. */
private data class Switches(
    val useConnections: Boolean? = null,
    val notifyMe: Boolean? = null,
    val failed: Boolean = false,
)

/** 5a and its sheets (7g, 7h, 7i, rename). Changes go straight to the API (open-questions#37). */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        sessions: SessionStore,
        connections: ConnectionDao,
        invites: InviteDao,
        private val permissions: PermissionReader,
        private val account: AccountRepository,
        private val auth: AuthRepository,
        ids: IdGenerator,
    ) : ViewModel() {
        private val granted = MutableStateFlow(permissions.read())
        private val switches = MutableStateFlow(Switches())

        /** The settings sheets; the route wires its callbacks straight to it. */
        internal val sheets = ProfileSheetHolder(viewModelScope, account, auth, ids)

        /** After Çıkış yap or Hesabımı sil: this phone holds nothing of the person any more. */
        val signedOut: Flow<Unit> = sheets.signedOut

        val state: StateFlow<ProfileUiState?> =
            combine(sessions.profile, connections.observeAll(), invites.observeAll(), granted, combine(switches, sheets.open, ::Pair)) {
                me,
                people,
                sent,
                perms,
                (sw, open),
                ->
                me?.let { uiState(it, people, sent, perms, sw).copy(sheet = open?.sheet) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Back from system settings: the permission rows show what is true now. */
        fun onResume() {
            granted.value = permissions.read()
        }

        fun onUseConnections(on: Boolean) =
            switch(set = { it.copy(useConnections = on) }, clear = { it.copy(useConnections = null) }) {
                account.setUseConnectionsForMyTasks(on)
            }

        fun onNotifyMe(on: Boolean) =
            switch(set = { it.copy(notifyMe = on) }, clear = { it.copy(notifyMe = null) }) {
                account.setNotifyMeForOthersTasks(on)
            }

        /** Shown as switched at once; back as it was, with the alert, when it did not reach the server. */
        private fun switch(
            set: (Switches) -> Switches,
            clear: (Switches) -> Switches,
            send: suspend () -> Boolean,
        ) {
            switches.update { set(it).copy(failed = false) }
            viewModelScope.launch {
                val ok = send()
                switches.update { clear(it).copy(failed = !ok) }
            }
        }

        fun onEditName() = sheets.show(ProfileSheet.Rename(state.value?.name.orEmpty()))

        fun onQuietHours() {
            val current = state.value ?: return
            sheets.show(ProfileSheet.Quiet(current.quietStart, current.quietEnd))
        }

        fun onLogOut() = sheets.show(ProfileSheet.LogOut())

        fun onDelete() = sheets.show(ProfileSheet.Delete())
    }

private fun uiState(
    me: Profile,
    people: List<ConnectionEntity>,
    invites: List<InviteEntity>,
    permissions: GrantedPermissions,
    switches: Switches,
) = ProfileUiState(
    name = me.displayName,
    phone = TurkishPhone.display(me.phone),
    people =
        people
            .filter { it.status == "active" }
            .sortedBy { it.connectedAt ?: 0 }
            .map { PersonRowUi(it.personId, it.displayName, EdgeType.decode(it.relationship) ?: EdgeType.OTHER) },
    pending = invites.filter { it.sent && it.status == "open" }.map { it.name },
    useConnectionsForMyTasks = switches.useConnections ?: me.useConnectionsForMyTasks,
    notifyMeForOthersTasks = switches.notifyMe ?: me.notifyMeForOthersTasks,
    quietStart = me.quietHours.start,
    quietEnd = me.quietHours.end,
    permissions = permissions,
    failed = switches.failed,
)
