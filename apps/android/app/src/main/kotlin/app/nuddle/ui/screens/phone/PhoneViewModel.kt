package app.nuddle.ui.screens.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.AuthRepository
import app.nuddle.data.CodeChannel
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

/** A code went to [e164] over [channel]; the code screen (0d) opens in that variant (ADR 0029). */
data class CodeSent(
    val e164: String,
    val channel: CodeChannel,
)

@HiltViewModel
class PhoneViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PhoneUiState())
        val state: StateFlow<PhoneUiState> = mutableState.asStateFlow()

        private val sent = Channel<CodeSent>(Channel.BUFFERED)

        /** Codes sent, with the number and the channel; the screen navigates to the code screen (0d). */
        val codeSent: Flow<CodeSent> = sent.receiveAsFlow()

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
                    is SendOtpOutcome.Sent -> {
                        mutableState.update { it.copy(sending = false) }
                        sent.send(CodeSent(e164, outcome.channel))
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
