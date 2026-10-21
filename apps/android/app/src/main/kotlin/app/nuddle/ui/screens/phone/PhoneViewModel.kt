package app.nuddle.ui.screens.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.AuthRepository
import app.nuddle.data.SendOtpOutcome
import app.nuddle.domain.TurkishPhone
import app.nuddle.ui.screens.hourMinute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import javax.inject.Inject

@HiltViewModel
class PhoneViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PhoneUiState())
        val state: StateFlow<PhoneUiState> = mutableState.asStateFlow()

        private val sent = Channel<String>(Channel.BUFFERED)

        /** E.164 numbers a code was sent to; the screen navigates to the code screen (0d). */
        val codeSent: Flow<String> = sent.receiveAsFlow()

        fun onNumberChange(input: String) {
            mutableState.update {
                it.copy(
                    national = TurkishPhone.sanitize(input),
                    failed = false,
                    lockedUntil = null,
                    limitedUntil = null,
                )
            }
        }

        fun onSend() {
            val current = mutableState.value
            if (!current.canSend) return
            val e164 = TurkishPhone.toE164(current.national) ?: return
            mutableState.update { it.copy(sending = true, failed = false, lockedUntil = null, limitedUntil = null) }
            viewModelScope.launch {
                when (val outcome = auth.sendOtp(e164)) {
                    SendOtpOutcome.Sent -> {
                        mutableState.update { it.copy(sending = false) }
                        sent.send(e164)
                    }

                    is SendOtpOutcome.Locked -> {
                        mutableState.update { it.copy(sending = false, lockedUntil = outcome.until.hourMinute(timeZone)) }
                    }

                    is SendOtpOutcome.Limited -> {
                        mutableState.update { it.copy(sending = false, limitedUntil = outcome.until.hourMinute(timeZone)) }
                    }

                    SendOtpOutcome.Failed -> {
                        mutableState.update { it.copy(sending = false, failed = true) }
                    }
                }
            }
        }
    }
