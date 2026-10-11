package app.nuddle.data

import app.nuddle.api.AuthApi
import app.nuddle.api.CommandsApi
import app.nuddle.api.model.RegisterCommand
import app.nuddle.api.model.SendOtpRequest
import app.nuddle.api.model.VerifyOtpRequest
import app.nuddle.data.api.ApiFailure
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.api.toStored
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import javax.inject.Inject

sealed interface VerifyOutcome {
    /** `registered = false` means a new person: the name screen (0e) and `register` come next (ADR 0014). */
    data class SignedIn(
        val registered: Boolean,
    ) : VerifyOutcome

    data object WrongCode : VerifyOutcome

    data object Unreachable : VerifyOutcome
}

/** Sign-in and sign-up are one flow, all through our API (ADR 0014). */
interface AuthRepository {
    suspend fun sendOtp(e164: String): Boolean

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
    ) : AuthRepository {
        override suspend fun sendOtp(e164: String): Boolean = apiCall(json) { authApi.sendOtp(SendOtpRequest(e164)) } is ApiResult.Success

        override suspend fun verify(
            e164: String,
            code: String,
        ): VerifyOutcome =
            when (val result = apiCall(json) { authApi.verifyOtp(VerifyOtpRequest(e164, code)) }) {
                is ApiResult.Success -> {
                    sessions.save(result.value.toStored())
                    VerifyOutcome.SignedIn(result.value.registered)
                }

                is ApiResult.Failure -> {
                    val wrongCode = (result.failure as? ApiFailure.Http)?.name == INVALID_OTP
                    if (wrongCode) VerifyOutcome.WrongCode else VerifyOutcome.Unreachable
                }
            }

        override suspend fun register(displayName: String): Boolean {
            val command =
                RegisterCommand(idempotencyKey = ids.next(), displayName = displayName, timeZone = timeZone.id)
            val result = apiCall(json) { commandsApi.register(command) }
            if (result !is ApiResult.Success) return false
            val me = result.value
            sessions.markRegistered(me.displayName)
            sessions.saveMe(
                me.displayName,
                QuietHours.parse(me.quietHours.start, me.quietHours.end, me.quietHours.timeZone),
            )
            return true
        }

        private companion object {
            const val INVALID_OTP = "invalid_otp"
        }
    }
