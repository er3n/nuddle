package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.model.CreateInviteCommand
import app.nuddle.api.model.EdgeType
import app.nuddle.api.model.RevokeInviteCommand
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.sync.SyncRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** The link that goes into the share sheet. TODO(open-questions#13): `https://nuddle.app/i/{code}` as App Links host. */
object InviteLinks {
    private const val BASE = "https://nuddle.app/i/"

    fun of(code: String): String = BASE + code
}

/**
 * Inviting someone (1e, 1f) and taking a pending invite back (1g). Both go straight to the API, not through the
 * outbox: the share sheet needs the code the server makes, and a revoke only makes sense while online.
 */
interface InviteRepository {
    /** The invite's code, or null when it did not reach the server. Reuse [idempotencyKey] when retrying. */
    suspend fun create(
        phone: String,
        name: String,
        relationship: EdgeType,
        idempotencyKey: String,
    ): String?

    suspend fun revoke(
        inviteId: String,
        idempotencyKey: String,
    ): Boolean
}

class ApiInviteRepository
    @Inject
    constructor(
        private val commandsApi: CommandsApi,
        private val sync: SyncRepository,
        private val json: Json,
    ) : InviteRepository {
        override suspend fun create(
            phone: String,
            name: String,
            relationship: EdgeType,
            idempotencyKey: String,
        ): String? {
            val command =
                CreateInviteCommand(
                    idempotencyKey = idempotencyKey,
                    phone = phone,
                    relationship = relationship,
                    prefilledName = name,
                )
            val result = apiCall(json) { commandsApi.createInvite(command) }
            if (result !is ApiResult.Success) return null
            // Home shows the pending invite (1g) from the synced list.
            sync.sync()
            return result.value.code
        }

        override suspend fun revoke(
            inviteId: String,
            idempotencyKey: String,
        ): Boolean {
            val result = apiCall(json) { commandsApi.revokeInvite(RevokeInviteCommand(idempotencyKey, inviteId)) }
            if (result !is ApiResult.Success) return false
            sync.sync()
            return true
        }
    }
