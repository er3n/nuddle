package app.nuddle.data.example

import app.nuddle.api.PhotosApi
import app.nuddle.api.model.ExamplePhotoViewUrlRequest
import app.nuddle.api.model.TaskStatus
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.db.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

/**
 * After a sync (NUD-138): fetches the example photo of every synced task whose path changed through a short-lived
 * signed URL, and drops the copy of a task that closed or lost its picture. Best effort, like profile pictures: a
 * picture that could not be fetched is tried again on the next sync that brings the task.
 */
fun interface ExamplePhotoRefresher {
    suspend fun refresh(tasks: List<TaskEntity>)
}

class ExampleSync
    @Inject
    constructor(
        private val photos: PhotosApi,
        @param:Named("storage") private val storage: OkHttpClient,
        private val files: ExampleFiles,
        private val json: Json,
    ) : ExamplePhotoRefresher {
        override suspend fun refresh(tasks: List<TaskEntity>) {
            for (task in tasks) {
                // My picked picture waits for its upload; the server does not have it yet.
                if (files.pendingKey(task.id) != null) continue
                val kept = files.pathOf(task.id)
                val path = task.examplePhotoPath?.takeIf { task.status in SHOWN }
                when {
                    path == null -> if (kept != null) files.delete(task.id)
                    kept != path -> fetch(task.id)
                }
            }
        }

        private suspend fun fetch(taskId: String) {
            val view = apiCall(json) { photos.createExamplePhotoViewUrl(ExamplePhotoViewUrlRequest(taskId)) }
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
            withContext(Dispatchers.IO) { files.save(taskId, view.value.examplePhotoPath, bytes) }
        }

        private companion object {
            /** The statuses in which a task has its picture (docs/domain.md, Example photo). */
            val SHOWN = setOf(TaskStatus.SCHEDULED.value, TaskStatus.REJECTED.value, TaskStatus.AWAITING_APPROVAL.value)
        }
    }
