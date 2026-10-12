package app.nuddle.data.photo

import app.nuddle.data.TaskCloser
import app.nuddle.domain.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Starts the upload of a kept photo once there is a network (implemented with WorkManager). */
fun interface PhotoUploadScheduler {
    /** [idempotencyKey] is the one `photo_sent` carries for this photo, kept across retries (ADR 0006). */
    fun uploadSoon(
        taskId: String,
        idempotencyKey: String,
    )
}

/**
 * Gönder (4d): the photo is kept on the phone, pokes stop at once (docs/product.md, Photo and approval) and the
 * upload goes out when there is a network.
 */
class PhotoSender
    @Inject
    constructor(
        private val files: PhotoFiles,
        private val closer: TaskCloser,
        private val scheduler: PhotoUploadScheduler,
        private val ids: IdGenerator,
    ) {
        suspend fun send(
            taskId: String,
            jpeg: ByteArray,
        ) {
            withContext(Dispatchers.IO) { files.save(taskId, jpeg) }
            closer.photoTaken(taskId)
            scheduler.uploadSoon(taskId, ids.next())
        }
    }
