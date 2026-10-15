package app.nuddle.data.avatar

import app.nuddle.api.PhotosApi
import app.nuddle.api.model.AvatarViewUrlRequest
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

/** A person whose picture the phone should hold: who, and the server path of it (null: the initial). */
data class AvatarRef(
    val key: String,
    val personId: String,
    val path: String?,
)

/**
 * After a sync (NUD-72): fetches every picture whose server path changed through a short-lived signed URL
 * (ADR 0002: the API hands out the URL, the phone never talks to Storage on its own), and drops the ones that
 * were removed. Best effort: a picture that could not be fetched is tried again on the next sync.
 */
fun interface AvatarRefresher {
    suspend fun refresh(refs: List<AvatarRef>)
}

class AvatarSync
    @Inject
    constructor(
        private val photos: PhotosApi,
        @param:Named("storage") private val storage: OkHttpClient,
        private val files: AvatarFiles,
        private val json: Json,
    ) : AvatarRefresher {
        override suspend fun refresh(refs: List<AvatarRef>) {
            for (ref in refs) {
                // A picked picture waits for its upload: the server's path is older than what the person sees.
                if (ref.key == ME && files.pending() != null) continue
                val kept = files.pathOf(ref.key)
                when {
                    ref.path == null -> if (kept != null) files.delete(ref.key)
                    kept != ref.path -> fetch(ref)
                }
            }
        }

        private suspend fun fetch(ref: AvatarRef) {
            val view = apiCall(json) { photos.createAvatarViewUrl(AvatarViewUrlRequest(ref.personId)) }
            if (view !is ApiResult.Success) return
            val bytes =
                withContext(Dispatchers.IO) {
                    try {
                        storage.newCall(Request.Builder().url(view.value.url).build()).execute().use { response ->
                            if (response.isSuccessful) response.body.bytes() else null
                        }
                    } catch (_: IOException) {
                        null
                    }
                } ?: return
            files.save(ref.key, view.value.avatarPath, bytes)
        }
    }
