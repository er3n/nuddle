package app.nuddle.data

import app.nuddle.api.InvitesApi
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Who sent the invite link and the name they typed for me, before sign-in (0b, 0e; ADR 0016). */
data class InvitePreview(
    val inviterName: String,
    val prefilledName: String,
)

/**
 * The preview of the invite link that opened the app while signed out. Kept in memory only: after sign-in the invite
 * reaches the person by their number anyway (ADR 0011).
 */
interface InvitePreviews {
    /** Null until a link was loaded, or when its invite is not open (or the phone was offline). */
    val current: StateFlow<InvitePreview?>

    suspend fun load(code: String)
}

@Singleton
class ApiInvitePreviews
    @Inject
    constructor(
        private val api: InvitesApi,
        private val json: Json,
    ) : InvitePreviews {
        private val preview = MutableStateFlow<InvitePreview?>(null)
        override val current: StateFlow<InvitePreview?> = preview.asStateFlow()

        override suspend fun load(code: String) {
            val result = apiCall(json) { api.invitePreview(code) }
            preview.value = (result as? ApiResult.Success)?.value?.let { InvitePreview(it.inviterName, it.prefilledName) }
        }
    }
