package app.nuddle.ui.screens.code

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nuddle.data.AuthRepository
import app.nuddle.data.SendOtpOutcome
import app.nuddle.data.SessionStarter
import app.nuddle.data.VerifyOutcome
import app.nuddle.data.sync.Syncer
import app.nuddle.domain.TurkishPhone
import app.nuddle.ui.components.CODE_LENGTH
import app.nuddle.ui.screens.hourMinute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Where sign-in continues: home for an existing person, the name screen (0e) for a new one (ADR 0014). */
enum class SignedInDestination { Home, Name }

@HiltViewModel
class CodeViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val auth: AuthRepository,
        private val starter: SessionStarter,
        private val syncer: Syncer,
        private val timeZone: TimeZone,
    ) : ViewModel() {
        private val phone: String = checkNotNull(savedState[ARG_PHONE])
        private val mutableState = MutableStateFlow(CodeUiState(phoneDisplay = TurkishPhone.display(phone)))
        val state: StateFlow<CodeUiState> = mutableState.asStateFlow()

        private val done = Channel<SignedInDestination>(Channel.BUFFERED)
        val signedIn: Flow<SignedInDestination> = done.receiveAsFlow()

        private val sends = MutableStateFlow(0)

        /** Counts the codes sent from this screen, so the SMS reader listens again after a resend. */
        val codesSent: StateFlow<Int> = sends.asStateFlow()

        private var countdown: Job? = null

        init {
            startCountdown()
        }

        fun onCodeChange(code: String) {
            mutableState.update { it.copy(code = code, wrongCode = false, unreachable = false) }
        }

        private fun locked(until: Instant) {
            countdown?.cancel()
            mutableState.update {
                it.copy(
                    code = "",
                    verifying = false,
                    wrongCode = true,
                    remainingAttempts = 0,
                    lockedUntil = until.hourMinute(timeZone),
                    resendInSeconds = 0,
                )
            }
        }

        fun onComplete(code: String) {
            if (code.length != CODE_LENGTH || mutableState.value.verifying) return
            mutableState.update { it.copy(code = code, verifying = true) }
            viewModelScope.launch {
                when (val outcome = auth.verify(phone, code)) {
                    is VerifyOutcome.SignedIn -> {
                        if (outcome.registered) {
                            starter.start()
                            // Home opens with the tasks, not the empty state (NUD-105); offline or slow, it opens anyway.
                            withTimeoutOrNull(FIRST_SYNC_WAIT) { syncer.sync() }
                        }
                        done.send(if (outcome.registered) SignedInDestination.Home else SignedInDestination.Name)
                    }

                    is VerifyOutcome.WrongCode -> {
                        mutableState.update { it.copy(verifying = false, wrongCode = true, remainingAttempts = outcome.remaining) }
                    }

                    is VerifyOutcome.Locked -> {
                        locked(outcome.until)
                    }

                    VerifyOutcome.Unreachable -> {
                        mutableState.update { it.copy(verifying = false, unreachable = true) }
                    }
                }
            }
        }

        fun onResend() {
            val current = mutableState.value
            if (current.resendInSeconds > 0 || current.lockedUntil != null || current.limitedUntil != null) return
            mutableState.update { it.copy(code = "", wrongCode = false, unreachable = false) }
            viewModelScope.launch {
                when (val outcome = auth.sendOtp(phone)) {
                    SendOtpOutcome.Sent -> {
                        sends.update { it + 1 }
                        startCountdown()
                    }

                    is SendOtpOutcome.Locked -> {
                        locked(outcome.until)
                    }

                    // The code already sent can still be typed; only a new one waits (ADR 0024).
                    is SendOtpOutcome.Limited -> {
                        mutableState.update { it.copy(limitedUntil = outcome.until.hourMinute(timeZone)) }
                    }

                    SendOtpOutcome.Failed -> {
                        mutableState.update { it.copy(unreachable = true) }
                    }
                }
            }
        }

        /** Counts in coroutine time (virtual in tests), not by reading the clock. */
        private fun startCountdown() {
            countdown?.cancel()
            countdown =
                viewModelScope.launch {
                    for (left in RESEND_AFTER_SECONDS downTo 0) {
                        mutableState.update { it.copy(resendInSeconds = left) }
                        if (left > 0) delay(1.seconds)
                    }
                }
        }

        companion object {
            const val ARG_PHONE = "phone"

            /** The design shows a one minute wait before a new code (0d). */
            const val RESEND_AFTER_SECONDS = 60

            /** How long the code screen keeps its spinner for the first sync before home opens anyway (NUD-105). */
            val FIRST_SYNC_WAIT = 5.seconds
        }
    }
