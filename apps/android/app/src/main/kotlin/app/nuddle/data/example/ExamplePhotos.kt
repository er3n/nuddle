package app.nuddle.data.example

import android.net.Uri
import app.nuddle.data.photo.GalleryImages
import app.nuddle.domain.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Starts the upload of the example photo of a task once there is a network (WorkManager, with retries). */
fun interface ExampleUploads {
    fun uploadSoon(taskId: String)
}

/**
 * The sender's example photo from the compose bar (NUD-138, docs/ux/screens.md 2a): picked or shared, it goes out
 * once the text became a task, on the direct path and after the preview alike, and waits with a text written offline.
 */
interface ExamplePhotos {
    /** The picture as the JPEG that is sent, the same as a gallery pick for a photo (4d); null when unreadable. */
    suspend fun read(uri: Uri): ByteArray?

    /** The task exists: the picture is kept here, shown at once and uploaded when there is a network. */
    suspend fun attach(
        taskId: String,
        jpeg: ByteArray,
    )

    /** The text [writeKey] waits offline: its picture waits with it. */
    suspend fun keepWithQueued(
        writeKey: String,
        jpeg: ByteArray,
    )

    /** The picture that waited with the queued text [writeKey], taken out; null without one. */
    suspend fun takeQueued(writeKey: String): ByteArray?

    /** For previews and tests that never attach a picture. */
    object None : ExamplePhotos {
        override suspend fun read(uri: Uri): ByteArray? = null

        override suspend fun attach(
            taskId: String,
            jpeg: ByteArray,
        ) = Unit

        override suspend fun keepWithQueued(
            writeKey: String,
            jpeg: ByteArray,
        ) = Unit

        override suspend fun takeQueued(writeKey: String): ByteArray? = null
    }
}

class FileExamplePhotos
    @Inject
    constructor(
        private val gallery: GalleryImages,
        private val files: ExampleFiles,
        private val drafts: ExampleDrafts,
        private val uploads: ExampleUploads,
        private val ids: IdGenerator,
    ) : ExamplePhotos {
        override suspend fun read(uri: Uri): ByteArray? = gallery.read(uri)

        override suspend fun attach(
            taskId: String,
            jpeg: ByteArray,
        ) {
            withContext(Dispatchers.IO) { files.savePending(taskId, jpeg, ids.next()) }
            uploads.uploadSoon(taskId)
        }

        override suspend fun keepWithQueued(
            writeKey: String,
            jpeg: ByteArray,
        ) = withContext(Dispatchers.IO) { drafts.save(writeKey, jpeg) }

        override suspend fun takeQueued(writeKey: String): ByteArray? = withContext(Dispatchers.IO) { drafts.take(writeKey) }
    }
