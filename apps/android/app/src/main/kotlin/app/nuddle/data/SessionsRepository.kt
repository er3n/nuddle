package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.DevicesApi
import app.nuddle.api.model.SignOutOthersCommand
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Instant

/** One phone my account is open on (ADR 0027). [model] is null until that phone reports one. */
data class SignedInPhone(
    val model: String?,
    val appVersion: String?,
    val lastSeenAt: Instant,
    /** The phone in my hand. */
    val current: Boolean,
)

/**
 * Cihazlarım (5e): like the profile settings these go straight to the API, not through the outbox, and the screen says
 * when one did not go (open-questions#37).
 */
interface SessionsRepository {
    /** Newest first; null when the list could not be loaded. */
    suspend fun phones(): List<SignedInPhone>?

    /** "Diğer cihazlardan çıkış yap". Reuse [idempotencyKey] when retrying. */
    suspend fun signOutOthers(idempotencyKey: String): Boolean
}

class ApiSessionsRepository
    @Inject
    constructor(
        private val devicesApi: DevicesApi,
        private val commandsApi: CommandsApi,
        private val json: Json,
    ) : SessionsRepository {
        override suspend fun phones(): List<SignedInPhone>? {
            val result = apiCall(json) { devicesApi.listSessions() }
            if (result !is ApiResult.Success) return null
            return result.value.sessions.map {
                SignedInPhone(it.deviceModel, it.appVersion, Instant.parse(it.lastSeenAt), it.current)
            }
        }

        override suspend fun signOutOthers(idempotencyKey: String): Boolean =
            apiCall(json) { commandsApi.signOutOthers(SignOutOthersCommand(idempotencyKey)) } is ApiResult.Success
    }
