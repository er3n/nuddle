package app.nuddle.data.photo

import app.nuddle.api.PhotosApi
import app.nuddle.api.model.PhotoViewUrlRequest
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

/**
 * The photo of a task awaiting approval, as JPEG bytes: the copy still kept on this phone (not uploaded yet), or the
 * one on the server through a short-lived signed URL (4e for the recipient, 4f for the sender). Null when there is
 * none to show or it could not be fetched; screens simply show no picture then.
 */
interface PhotoLoader {
    suspend fun load(taskId: String): ByteArray?
}

class ApiPhotoLoader
    @Inject
    constructor(
        private val photos: PhotosApi,
        @param:Named("storage") private val storage: OkHttpClient,
        private val files: PhotoFiles,
        private val json: Json,
    ) : PhotoLoader {
        override suspend fun load(taskId: String): ByteArray? {
            val kept = files.of(taskId)
            if (kept.exists()) return withContext(Dispatchers.IO) { kept.readBytes() }
            val view = apiCall(json) { photos.createPhotoViewUrl(PhotoViewUrlRequest(taskId)) }
            if (view !is ApiResult.Success) return null
            // Blocking OkHttp call: screens call this from the main thread.
            return withContext(Dispatchers.IO) {
                try {
                    storage.newCall(Request.Builder().url(view.value.url).build()).execute().use { response ->
                        if (response.isSuccessful) response.body.bytes() else null
                    }
                } catch (_: IOException) {
                    null
                }
            }
        }
    }
