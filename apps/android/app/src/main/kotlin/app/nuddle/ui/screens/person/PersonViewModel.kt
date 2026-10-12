package app.nuddle.ui.screens.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.EdgeType
import app.nuddle.data.AccountRepository
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.domain.ConnectedFor
import app.nuddle.domain.IdGenerator
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

/** What only this screen knows: the open sheet and its key, the switch on its way, and a failed switch. */
private data class Local(
    val sheet: PersonSheet? = null,
    val key: String = "",
    val notifyMe: Boolean? = null,
    val failed: Boolean = false,
)

/** 5b: one connection. Changes go straight to the API (open-questions#37). */
@HiltViewModel
class PersonViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        connections: ConnectionDao,
        tasks: TaskDao,
        private val account: AccountRepository,
        private val ids: IdGenerator,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val personId: String = checkNotNull(savedState[ARG_PERSON])
        private val local = MutableStateFlow(Local())
        private val closedChannel = Channel<Unit>(Channel.CONFLATED)

        /** The connection is gone (removed here): back to profile. */
        val closed: Flow<Unit> = closedChannel.receiveAsFlow()

        /** Null until the connection is known, and once it is no longer active. */
        val state: StateFlow<PersonUiState?> =
            combine(connections.observeAll(), tasks.observeAll(), local) { all, allTasks, l ->
                val connection = all.firstOrNull { it.personId == personId && it.status == "active" } ?: return@combine null
                PersonUiState(
                    personId = personId,
                    name = connection.displayName,
                    relationship = EdgeType.decode(connection.relationship) ?: EdgeType.OTHER,
                    since = connection.connectedAt?.let { ConnectedFor.between(Instant.fromEpochMilliseconds(it), clock.now(), timeZone) },
                    notifyMe = l.notifyMe ?: connection.notifyMe,
                    tasks = allTasks.count { it.senderId == personId || it.recipientId == personId },
                    failed = l.failed,
                    sheet = l.sheet,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Shown as switched at once; back as it was, with the alert, when it did not reach the server. */
        fun onNotifyMe(on: Boolean) {
            local.update { it.copy(notifyMe = on, failed = false) }
            viewModelScope.launch {
                val ok = account.setNotifyMe(personId, on)
                local.update { it.copy(notifyMe = null, failed = !ok) }
            }
        }

        fun onRelationship() {
            val current = state.value ?: return
            open(PersonSheet.Relationship(current.relationship))
        }

        fun onRemove() = open(PersonSheet.Remove())

        private fun open(sheet: PersonSheet) = local.update { it.copy(sheet = sheet, key = ids.next(), failed = false) }

        fun onSelect(type: EdgeType) =
            local.update { l ->
                (l.sheet as? PersonSheet.Relationship)?.let { l.copy(sheet = it.copy(selected = type, failed = false)) }
                    ?: l
            }

        /** Swipe, scrim or Vazgeç; not while the sheet's command is on its way. */
        fun onDismiss() = local.update { if (it.sheet?.busy == true) it else it.copy(sheet = null) }

        fun onConfirm() {
            val l = local.value
            val sheet = l.sheet ?: return
            if (sheet.busy) return
            local.update { it.copy(sheet = sheet.with(busy = true, failed = false)) }
            viewModelScope.launch {
                val ok =
                    when (sheet) {
                        is PersonSheet.Relationship -> account.setRelationship(personId, sheet.selected)
                        is PersonSheet.Remove -> account.removeConnection(personId, l.key)
                    }
                local.update { it.copy(sheet = if (ok) null else sheet.with(busy = false, failed = true)) }
                if (ok && sheet is PersonSheet.Remove) closedChannel.send(Unit)
            }
        }

        companion object {
            const val ARG_PERSON = "personId"
        }
    }

private fun PersonSheet.with(
    busy: Boolean,
    failed: Boolean,
): PersonSheet =
    when (this) {
        is PersonSheet.Relationship -> copy(busy = busy, failed = failed)
        is PersonSheet.Remove -> copy(busy = busy, failed = failed)
    }
