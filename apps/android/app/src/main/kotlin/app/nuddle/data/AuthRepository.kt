package app.nuddle.data

import app.nuddle.BuildConfig
import app.nuddle.api.AuthApi
import app.nuddle.api.CommandsApi
import app.nuddle.api.model.OtpChannel
import app.nuddle.api.model.OtpSent
import app.nuddle.api.model.RegisterCommand
import app.nuddle.api.model.ResendOtpBySmsRequest
import app.nuddle.api.model.SendOtpRequest
import app.nuddle.api.model.VerifyOtpRequest
import app.nuddle.data.api.ApiFailure
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.api.toStored
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Instant

sealed interface VerifyOutcome {
    /** `registered = false` means a new person: the name screen (0e) and `register` come next (ADR 0014). */
    data class SignedIn(
        val registered: Boolean,
    ) : VerifyOutcome

    /** [remaining] codes left before the wait (6c, open-questions#41). */
    data class WrongCode(
        val remaining: Int? = null,
    ) : VerifyOutcome

    /** Too many wrong codes: nothing is checked or sent until [until]. */
    data class Locked(
        val until: Instant,
    ) : VerifyOutcome

    data object Unreachable : VerifyOutcome
}

/** How the code went out (ADR 0029): the code screen (0d) shows the WhatsApp or the SMS variant. */
enum class CodeChannel { WhatsApp, Sms }

sealed interface SendOtpOutcome {
    data class Sent(
        val channel: CodeChannel,
    ) : SendOtpOutcome

    /** Too many wrong codes for this phone: a new one can be asked for at [until] (open-questions#41). */
    data class Locked(
        val until: Instant,
    ) : SendOtpOutcome

    /** Too many codes to this phone or to everyone in the last 24 hours (ADR 0024): none is sent until [until]. */
    data class Limited(
        val until: Instant,
    ) : SendOtpOutcome

    data object Failed : SendOtpOutcome
}

/** "SMS ile gönder" (ADR 0029): the code already sent, again by SMS. */
sealed interface SmsResendOutcome {
    data object Sent : SmsResendOutcome

    /** No code is held for the phone, it expired or it already went by SMS (`otp_resend_unavailable`). */
    data object Unavailable : SmsResendOutcome

    data object Failed : SmsResendOutcome
}

private const val OTP_LOCKED = "otp_locked"
private const val OTP_RESEND_UNAVAILABLE = "otp_resend_unavailable"
private const val OTP_LIMIT = "otp_limit"

private fun ApiFailure.retryAtFor(name: String): Instant? = (this as? ApiFailure.Http)?.takeIf { it.name == name }?.retryAt

private fun ApiFailure.lockedUntil(): Instant? = retryAtFor(OTP_LOCKED)

private fun OtpSent.codeChannel(): CodeChannel =
    when (channel) {
        OtpChannel.WHATSAPP -> CodeChannel.WhatsApp
        OtpChannel.SMS -> CodeChannel.Sms
    }

/** Sign-in and sign-up are one flow, all through our API (ADR 0014). */
interface AuthRepository {
    /** Sends the WhatsApp handshake first, so a WhatsApp code can fill itself in (ADR 0029). */
    suspend fun sendOtp(e164: String): SendOtpOutcome

    suspend fun resendOtpBySms(e164: String): SmsResendOutcome

    suspend fun verify(
        e164: String,
        code: String,
    ): VerifyOutcome

    suspend fun register(displayName: String): Boolean
}

class ApiAuthRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val commandsApi: CommandsApi,
        private val sessions: SessionStore,
        private val ids: IdGenerator,
        private val timeZone: TimeZone,
        private val json: Json,
        private val whatsApp: WhatsAppOtp,
    ) : AuthRepository {
        override suspend fun sendOtp(e164: String): SendOtpOutcome {
            whatsApp.handshake()
            return when (val result = apiCall(json) { authApi.sendOtp(SendOtpRequest(e164)) }) {
                is ApiResult.Success -> {
                    SendOtpOutcome.Sent(result.value.codeChannel())
                }

                is ApiResult.Failure -> {
                    result.failure.lockedUntil()?.let(SendOtpOutcome::Locked)
                        ?: result.failure.retryAtFor(OTP_LIMIT)?.let(SendOtpOutcome::Limited)
                        ?: SendOtpOutcome.Failed
                }
            }
        }

        override suspend fun resendOtpBySms(e164: String): SmsResendOutcome =
            when (val result = apiCall(json) { authApi.resendOtpBySms(ResendOtpBySmsRequest(e164)) }) {
                is ApiResult.Success -> {
                    SmsResendOutcome.Sent
                }

                is ApiResult.Failure -> {
                    val unavailable = (result.failure as? ApiFailure.Http)?.name == OTP_RESEND_UNAVAILABLE
                    if (unavailable) SmsResendOutcome.Unavailable else SmsResendOutcome.Failed
                }
            }

        override suspend fun verify(
            e164: String,
            code: String,
        ): VerifyOutcome =
            when (
                val result =
                    apiCall(
                        json,
                    ) { authApi.verifyOtp(VerifyOtpRequest(e164, code, BuildConfig.VERSION_NAME, sessions.deviceId())) }
            ) {
                is ApiResult.Success -> {
                    sessions.save(result.value.toStored())
                    VerifyOutcome.SignedIn(result.value.registered)
                }

                is ApiResult.Failure -> {
                    val http = result.failure as? ApiFailure.Http
                    val locked = result.failure.lockedUntil()
                    when {
                        locked != null -> VerifyOutcome.Locked(locked)
                        http?.name == INVALID_OTP -> VerifyOutcome.WrongCode(http.remainingAttempts)
                        else -> VerifyOutcome.Unreachable
                    }
                }
            }

        override suspend fun register(displayName: String): Boolean {
            val command =
                RegisterCommand(idempotencyKey = ids.next(), displayName = displayName, timeZone = timeZone.id)
            val result = apiCall(json) { commandsApi.register(command) }
            if (result !is ApiResult.Success) return false
            val me = result.value
            sessions.markRegistered(me.displayName)
            sessions.saveMe(me.toProfile())
            return true
        }

        private companion object {
            const val INVALID_OTP = "invalid_otp"
        }
    }
