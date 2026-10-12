package app.nuddle.data.photo

import app.nuddle.api.CommandsApi
import app.nuddle.api.PhotosApi
import app.nuddle.api.model.PhotoSentCommand
import app.nuddle.api.model.PhotoUploadUrlRequest
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
 * One upload attempt for a kept photo: a signed upload URL, the PUT to Storage, then `photo_sent` (ADR 0002: the
 * app talks only to our API; the signed URL is the API's answer). The file is deleted once the server has it, or
 * when the server refuses on purpose (the task closed meanwhile).
 */
class PhotoUploader
    @Inject
    constructor(
        private val photos: PhotosApi,
        private val commands: CommandsApi,
        @param:Named("storage") private val storage: OkHttpClient,
        private val files: PhotoFiles,
        private val json: Json,
    ) {
        suspend fun upload(taskId: String): SendOutcome {
            val file = files.of(taskId)
            val sending = files.pending(taskId) ?: return SendOutcome.SENT
            val takenAt = sending.takenAt.toString()
            val target =
                when (val r = apiCall(json) { photos.createPhotoUploadUrl(PhotoUploadUrlRequest(taskId, takenAt)) }) {
                    is ApiResult.Success -> r.value
                    is ApiResult.Failure -> return finish(taskId, r.failure)
                }
            val put =
                Request
                    .Builder()
                    .url(target.url)
                    .put(file.asRequestBody(JPEG))
                    .build()
            val stored =
                try {
                    storage.newCall(put).execute().use { it.isSuccessful }
                } catch (_: IOException) {
                    false
                }
            if (!stored) return SendOutcome.RETRY
            val sent =
                apiCall(json) { commands.photoSent(PhotoSentCommand(sending.idempotencyKey, taskId, target.path, takenAt)) }
            return when (sent) {
                is ApiResult.Success -> {
                    files.delete(taskId)
                    SendOutcome.SENT
                }

                is ApiResult.Failure -> {
                    finish(taskId, sent.failure)
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
