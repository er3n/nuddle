package app.nuddle.data.example

import app.nuddle.api.CommandsApi
import app.nuddle.api.PhotosApi
import app.nuddle.api.model.ExamplePhotoUploadUrlRequest
import app.nuddle.api.model.SetExamplePhotoCommand
import app.nuddle.data.api.ApiFailure
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.domain.OutboxPolicy
import app.nuddle.domain.SendOutcome
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

/**
 * One upload attempt for the example photo of a task (NUD-138), as for a photo (ADR 0002: the app talks only to our
 * API; the signed URL is its answer): a signed upload URL, the PUT to Storage, then `set_example_photo`. The copy
 * stays on the phone as the one for the server's path. A refusal on purpose (the task was undone or closed
 * meanwhile) drops it.
 */
class ExampleUploader
    @Inject
    constructor(
        private val photos: PhotosApi,
        private val commands: CommandsApi,
        @param:Named("storage") private val storage: OkHttpClient,
        private val files: ExampleFiles,
        private val json: Json,
    ) {
        suspend fun upload(taskId: String): SendOutcome {
            val key = files.pendingKey(taskId) ?: return SendOutcome.SENT
            val target =
                when (val r = apiCall(json) { photos.createExamplePhotoUploadUrl(ExamplePhotoUploadUrlRequest(taskId)) }) {
                    is ApiResult.Success -> r.value
                    is ApiResult.Failure -> return finish(taskId, r.failure)
                }
            val put =
                Request
                    .Builder()
                    .url(target.url)
                    .put(files.of(taskId).asRequestBody(JPEG))
                    .build()
            val stored =
                try {
                    storage.newCall(put).execute().use { it.isSuccessful }
                } catch (_: IOException) {
                    false
                }
            if (!stored) return SendOutcome.RETRY
            return when (val set = apiCall(json) { commands.setExamplePhoto(SetExamplePhotoCommand(key, taskId, target.path)) }) {
                is ApiResult.Success -> {
                    files.uploaded(taskId, target.path)
                    SendOutcome.SENT
                }

                is ApiResult.Failure -> {
                    finish(taskId, set.failure)
                }
            }
        }

        private fun finish(
            taskId: String,
            failure: ApiFailure,
        ): SendOutcome {
            val outcome = OutboxPolicy.outcome((failure as? ApiFailure.Http)?.status)
            if (outcome == SendOutcome.DROP) files.delete(taskId)
            return outcome
        }

        private companion object {
            val JPEG = "image/jpeg".toMediaType()
        }
    }
