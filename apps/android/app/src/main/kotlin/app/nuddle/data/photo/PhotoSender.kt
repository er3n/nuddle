package app.nuddle.data.photo

import app.nuddle.data.TaskCloser
import app.nuddle.domain.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Clock

/** The uploads of kept photos, run with WorkManager (docs/ux/platform.md: retried until the server has them). */
interface PhotoUploads {
    /** Starts the upload once there is a network; again now for "Şimdi tekrar dene" (6e). */
    fun uploadSoon(taskId: String)

    /** True while the upload of [taskId] has failed and waits for its next try (6e). */
    fun failing(taskId: String): Flow<Boolean>
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
        private val uploads: PhotoUploads,
        private val ids: IdGenerator,
        private val clock: Clock,
    ) {
        suspend fun send(
            taskId: String,
            jpeg: ByteArray,
        ) {
            val sending = PendingPhoto(ids.next(), clock.now())
            withContext(Dispatchers.IO) { files.save(taskId, jpeg, sending) }
            closer.photoTaken(taskId)
            uploads.uploadSoon(taskId)
        }
    }
