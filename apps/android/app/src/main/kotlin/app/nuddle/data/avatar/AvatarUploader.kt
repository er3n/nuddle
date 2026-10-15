package app.nuddle.data.avatar

import app.nuddle.api.CommandsApi
import app.nuddle.api.PhotosApi
import app.nuddle.api.model.SetAvatarCommand
import app.nuddle.data.api.ApiFailure
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.session.SessionStore
import app.nuddle.data.toProfile
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.OutboxPolicy
import app.nuddle.domain.SendOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

/** Starts the upload of the picked picture once there is a network (WorkManager, like task photos). */
fun interface AvatarUploads {
    fun uploadSoon()
}

/**
 * One upload attempt for the picked picture (NUD-72): a signed upload URL, the PUT to Storage, then `set_avatar`
 * with the path. The picture is then kept as mine under the new path; the pending file goes once the server has
 * it, or when the server refuses on purpose.
 */
class AvatarUploader
    @Inject
    constructor(
        private val photos: PhotosApi,
        private val commands: CommandsApi,
        @param:Named("storage") private val storage: OkHttpClient,
        private val files: AvatarFiles,
        private val sessions: SessionStore,
        private val ids: IdGenerator,
        private val json: Json,
    ) {
        suspend fun upload(): SendOutcome {
            val file = files.pending() ?: return SendOutcome.SENT
            val target =
                when (val r = apiCall(json) { photos.createAvatarUploadUrl() }) {
                    is ApiResult.Success -> r.value
                    is ApiResult.Failure -> return finish(r.failure)
                }
            val stored =
                withContext(Dispatchers.IO) {
                    try {
                        storage
                            .newCall(
                                Request
                                    .Builder()
                                    .url(target.url)
                                    .put(file.asRequestBody(JPEG))
                                    .build(),
                            ).execute()
                            .use {
                                it.isSuccessful
                            }
                    } catch (_: IOException) {
                        false
                    }
                }
            if (!stored) return SendOutcome.RETRY
            return when (val set = apiCall(json) { commands.setAvatar(SetAvatarCommand(ids.next(), target.path)) }) {
                is ApiResult.Success -> {
                    files.save(ME, target.path, withContext(Dispatchers.IO) { file.readBytes() })
                    files.deletePending()
                    sessions.saveMe(set.value.toProfile())
                    SendOutcome.SENT
                }

                is ApiResult.Failure -> {
                    finish(set.failure)
                }
            }
        }

        private fun finish(failure: ApiFailure): SendOutcome {
            val outcome = OutboxPolicy.outcome((failure as? ApiFailure.Http)?.status)
            if (outcome == SendOutcome.DROP) files.deletePending()
            return outcome
        }

        private companion object {
            val JPEG = "image/jpeg".toMediaType()
        }
    }
