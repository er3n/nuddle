package app.nuddle.ui.screens.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.AuthRepository
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

@HiltViewModel
class PhoneViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PhoneUiState())
        val state: StateFlow<PhoneUiState> = mutableState.asStateFlow()

        private val sent = Channel<String>(Channel.BUFFERED)

        /** E.164 numbers a code was sent to; the screen navigates to the code screen (0d). */
        val codeSent: Flow<String> = sent.receiveAsFlow()

        fun onNumberChange(input: String) {
            mutableState.update { it.copy(national = TurkishPhone.sanitize(input), failed = false) }
        }

        fun onSend() {
            val current = mutableState.value
            if (!current.canSend) return
            val e164 = TurkishPhone.toE164(current.national) ?: return
            mutableState.update { it.copy(sending = true, failed = false) }
            viewModelScope.launch {
                val ok = auth.sendOtp(e164)
                mutableState.update { it.copy(sending = false, failed = !ok) }
                if (ok) sent.send(e164)
            }
        }
    }
