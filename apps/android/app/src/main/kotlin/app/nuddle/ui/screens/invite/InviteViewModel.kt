package app.nuddle.ui.screens.invite

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.api.model.EdgeType
import app.nuddle.data.InviteLinks
import app.nuddle.data.InviteRepository
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.TurkishPhone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The person to invite: [phone] is E.164. */
@Immutable
data class Invitee(
    val name: String,
    val phone: String,
)

/**
 * 1e (nothing chosen yet) and 1f (a contact and a relationship). The number can also be typed by hand
 * ([typing], open-questions#32). The relationship is mandatory and nothing is preselected.
 */
@Immutable
data class InviteUiState(
    val contact: Invitee? = null,
    val typing: Boolean = false,
    val typedName: String = "",
    /** National digits, as on 0c. */
    val typedNumber: String = "",
    /** The picked contact's number is not a Turkish mobile. */
    val numberInvalid: Boolean = false,
    val relationship: EdgeType? = null,
    val sending: Boolean = false,
    val failed: Boolean = false,
) {
    val invitee: Invitee?
        get() =
            contact ?: TurkishPhone
                .toE164(typedNumber)
                ?.takeIf { typing && typedName.isNotBlank() }
                ?.let { Invitee(typedName.trim(), it) }

    val canSend: Boolean get() = invitee != null && relationship != null && !sending
}

@HiltViewModel
class InviteViewModel
    @Inject
    constructor(
        private val invites: InviteRepository,
        private val ids: IdGenerator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(InviteUiState())
        val state: StateFlow<InviteUiState> = mutableState.asStateFlow()

        private val shareChannel = Channel<String>(Channel.BUFFERED)

        /** The invite went out: the link to put into the system share sheet. */
        val share: Flow<String> = shareChannel.receiveAsFlow()

        /** Kept for a retry of the same invite, so a create the server got but did not answer is not made twice. */
        private var pendingKey: String? = null

        /** From the single contact picker; numbers that are not Turkish mobiles are refused. */
        fun onContactPicked(
            name: String,
            rawNumber: String,
        ) {
            val phone = TurkishPhone.fromContact(rawNumber)
            pendingKey = null
            mutableState.update {
                it.copy(
                    contact = phone?.let { e164 -> Invitee(name.ifBlank { TurkishPhone.display(e164) }, e164) },
                    numberInvalid = phone == null,
                    typing = false,
                    failed = false,
                )
            }
        }

        fun onTypeNumber() {
            pendingKey = null
            mutableState.update { it.copy(typing = true, contact = null, numberInvalid = false) }
        }

        fun onTypedName(name: String) {
            pendingKey = null
            mutableState.update { it.copy(typedName = name) }
        }

        fun onTypedNumber(input: String) {
            pendingKey = null
            mutableState.update { it.copy(typedNumber = TurkishPhone.sanitize(input)) }
        }

        fun onRelationship(relationship: EdgeType) {
            pendingKey = null
            mutableState.update { it.copy(relationship = relationship) }
        }

        /** Daveti gönder: create the invite, then hand the link to the share sheet. */
        fun onSend() {
            val current = mutableState.value
            if (!current.canSend) return
            val invitee = current.invitee ?: return
            val relationship = current.relationship ?: return
            val key = pendingKey ?: ids.next().also { pendingKey = it }
            mutableState.update { it.copy(sending = true, failed = false) }
            viewModelScope.launch {
                val code = invites.create(invitee.phone, invitee.name, relationship, key)
                mutableState.update { it.copy(sending = false, failed = code == null) }
                if (code != null) shareChannel.send(InviteLinks.of(code))
            }
        }
    }
