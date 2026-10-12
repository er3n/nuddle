package app.nuddle.ui.screens.consent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.EdgeType
import app.nuddle.api.model.SyncReceivedInvite
import app.nuddle.data.InviteRepository
import app.nuddle.data.SetupGate
import app.nuddle.data.db.InviteDao
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.Ago
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * 0f (right after sign-in) and 1d (from the bell, [fromBell]): "Selin seni dürtebilsin mi?". [addedAs] is the
 * inviter's pick (what I am to them), [myPick] mine (what they are to me), which "Değiştir" changes.
 */
@Immutable
data class ConsentUiState(
    val inviterName: String,
    val addedAs: EdgeType,
    val myPick: EdgeType,
    val quietStart: String,
    val quietEnd: String,
    val fromBell: Boolean = false,
    val ago: Ago = Ago.Now,
    val picking: Boolean = false,
    val sending: Boolean = false,
    val failed: Boolean = false,
    /** Revoked, declined or already accepted: nothing to answer any more. */
    val gone: Boolean = false,
)

sealed interface ConsentEffect {
    /** İzin ver went through: 0g next, then setup when [setupNeeded]. */
    data class Accepted(
        val inviterName: String,
        val setupNeeded: Boolean,
    ) : ConsentEffect

    /** Şimdi değil, or Reddet went through. */
    data object Closed : ConsentEffect
}

/** My side of the relationship the inviter chose: if I am their parent, they are my child. */
internal fun EdgeType.inverse(): EdgeType =
    when (this) {
        EdgeType.PARENT -> EdgeType.CHILD
        EdgeType.CHILD -> EdgeType.PARENT
        else -> this
    }

@HiltViewModel
class ConsentViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        inviteDao: InviteDao,
        private val sessions: SessionStore,
        private val invites: InviteRepository,
        private val setupGate: SetupGate,
        ids: IdGenerator,
        clock: Clock,
        timeZone: TimeZone,
    ) : ViewModel() {
        private val inviteId: String = checkNotNull(savedState[ARG_INVITE])
        private val fromBell: Boolean = savedState[ARG_FROM_BELL] ?: false

        /** What the person changed on the screen: their pick, the sheet, a send in flight. */
        private val local = MutableStateFlow(Local())

        private data class Local(
            val myPick: EdgeType? = null,
            val picking: Boolean = false,
            val sending: Boolean = false,
            val failed: Boolean = false,
        )

        private val effectChannel = Channel<ConsentEffect>(Channel.BUFFERED)
        val effects: Flow<ConsentEffect> = effectChannel.receiveAsFlow()

        /** Kept for retries of the same answer (ADR 0006). */
        private val acceptKey = ids.next()
        private val declineKey = ids.next()

        private var code: String? = null

        val state: StateFlow<ConsentUiState?> =
            combine(inviteDao.observe(inviteId), local) { invite, local ->
                invite ?: return@combine null
                code = invite.code
                val addedAs = EdgeType.decode(invite.relationship) ?: EdgeType.OTHER
                val quiet = sessions.quietHours() ?: QuietHours.default(timeZone)
                ConsentUiState(
                    inviterName = invite.name,
                    addedAs = addedAs,
                    myPick = local.myPick ?: addedAs.inverse(),
                    quietStart = quiet.start.toString(),
                    quietEnd = quiet.end.toString(),
                    fromBell = fromBell,
                    ago = Ago.between(Instant.fromEpochMilliseconds(invite.createdAt), clock.now()),
                    picking = local.picking,
                    sending = local.sending,
                    failed = local.failed,
                    gone = invite.status != SyncReceivedInvite.Status.OPEN.value,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun onChangeRelationship() {
            local.update { it.copy(picking = true) }
        }

        fun onPick(relationship: EdgeType) {
            local.update { it.copy(myPick = relationship, picking = false) }
        }

        fun onPickDismiss() {
            local.update { it.copy(picking = false) }
        }

        fun onAllow() {
            val current = state.value ?: return
            val inviteCode = code ?: return
            if (current.sending || current.gone) return
            local.update { it.copy(sending = true, failed = false) }
            viewModelScope.launch {
                val ok = invites.accept(inviteCode, current.myPick, acceptKey)
                local.update { it.copy(sending = false, failed = !ok) }
                if (ok) effectChannel.send(ConsentEffect.Accepted(current.inviterName, setupGate.needed()))
            }
        }

        /** Şimdi değil (0f): nothing is sent; the invite stays in the bell. */
        fun onLater() {
            effectChannel.trySend(ConsentEffect.Closed)
        }

        /** Reddet (1d): stored, never shown to the inviter. */
        fun onDecline() {
            val inviteCode = code ?: return
            if (local.value.sending) return
            local.update { it.copy(sending = true, failed = false) }
            viewModelScope.launch {
                val ok = invites.decline(inviteCode, declineKey)
                local.update { it.copy(sending = false, failed = !ok) }
                if (ok) effectChannel.send(ConsentEffect.Closed)
            }
        }

        companion object {
            const val ARG_INVITE = "inviteId"
            const val ARG_FROM_BELL = "fromBell"
        }
    }
