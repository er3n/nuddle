package app.nuddle.ui.screens.home

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.nuddle.data.example.ExamplePhotos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The example photo in the bar: the JPEG that goes out and the picture the bar shows. */
internal class AttachedPhoto(
    val jpeg: ByteArray,
    val image: ImageBitmap,
)

/**
 * The sender's example photo in home's compose bar (NUD-138, docs/ux/screens.md 2a): picked or shared, it goes out with
 * the next task the bar or the preview creates, and waits with a text written offline. The route binds the gallery
 * button and the ✕ to it.
 */
internal class ComposePhotoHolder(
    private val examples: ExamplePhotos,
    private val scope: CoroutineScope,
) {
    private val attached = MutableStateFlow<AttachedPhoto?>(null)

    /** What the bar shows. */
    val image: Flow<ImageBitmap?> = attached.map { it?.image }

    /** What a send now would take along. */
    val current: AttachedPhoto? get() = attached.value

    /** A picture picked in the gallery or shared to dürt: attached, replacing one before; nothing for an unreadable one. */
    fun onAttach(uri: Uri) {
        scope.launch { examples.read(uri)?.toAttached()?.let { attached.value = it } }
    }

    /** The ✕ of the attached picture. */
    fun onRemove() {
        attached.value = null
    }

    /** [photo] goes with the task just created; one attached meanwhile stays for the next task. */
    suspend fun sent(
        photo: AttachedPhoto?,
        taskId: String,
    ) {
        photo ?: return
        attached.compareAndSet(photo, null)
        examples.attach(taskId, photo.jpeg)
    }

    /** The text [writeKey] waits offline (6a): [photo] waits with it, the bar is empty again. */
    suspend fun queued(
        photo: AttachedPhoto?,
        writeKey: String,
    ) {
        photo ?: return
        attached.compareAndSet(photo, null)
        examples.keepWithQueued(writeKey, photo.jpeg)
    }

    /** The queued text [writeKey] became the task [taskId]: its picture goes with it. */
    suspend fun queuedSent(
        writeKey: String,
        taskId: String,
    ) {
        examples.takeQueued(writeKey)?.let { examples.attach(taskId, it) }
    }

    /** The queued text [writeKey] is back in the bar (a question, or not a task): so is its picture. */
    suspend fun queuedBack(writeKey: String) {
        examples.takeQueued(writeKey)?.toAttached()?.let { attached.value = it }
    }
}

/** Null when the bytes are no picture. */
private fun ByteArray.toAttached(): AttachedPhoto? =
    BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()?.let { AttachedPhoto(this, it) }
