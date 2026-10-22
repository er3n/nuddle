package app.nuddle.data.example

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The kept example photo of a task as JPEG bytes (NUD-138); null while there is none. */
interface ExamplePictures {
    /** Again on every change. */
    fun picture(taskId: String): Flow<ByteArray?>

    /** What the phone has right now: for a poke, which cannot wait for a fetch. */
    fun current(taskId: String): ByteArray?

    /** For previews and tests without pictures. */
    object None : ExamplePictures {
        override fun picture(taskId: String): Flow<ByteArray?> = flowOf(null)

        override fun current(taskId: String): ByteArray? = null
    }
}

/**
 * The sender's example photos on this phone (NUD-138, docs/domain.md, Example photo): one JPEG per task next to the
 * server path it is for, so a sync only fetches a picture whose path changed. On the sender's phone a picked picture
 * waits here with the idempotency key of its `set_example_photo` until the upload is done; a picture attached to a
 * text written offline waits under the text's key until the text becomes a task.
 *
 * Kept inside the photo folder (`files/photos/examples`), so signing out, account deletion and the e2e reset, which
 * delete that folder, take these too.
 */
@Singleton
class ExampleFiles(
    private val dir: File,
) : ExamplePictures {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(File(context.filesDir, "photos"), "examples"))

    private val changes = MutableStateFlow(0L)

    /** Bumped on every change, so screens reload the picture they show. */
    val version: StateFlow<Long> = changes

    fun of(taskId: String): File = File(dir, "$taskId.jpg")

    override fun picture(taskId: String): Flow<ByteArray?> =
        version.map { withContext(Dispatchers.IO) { current(taskId) } }.distinctUntilChanged { a, b -> a.contentEquals(b) }

    /** The kept picture of [taskId], or null without one. */
    override fun current(taskId: String): ByteArray? = of(taskId).takeIf { it.exists() }?.readBytes()

    /** The server path the kept picture of [taskId] is for ([PENDING_PATH] while it uploads), or null. */
    fun pathOf(taskId: String): String? = metaOf(taskId).takeIf { it.exists() && of(taskId).exists() }?.readText()

    fun save(
        taskId: String,
        path: String,
        jpeg: ByteArray,
    ) {
        dir.mkdirs()
        of(taskId).writeBytes(jpeg)
        metaOf(taskId).writeText(path)
        changes.update { it + 1 }
    }

    /** The sender's picture for [taskId], shown at once and uploaded with [idempotencyKey] when there is a network. */
    fun savePending(
        taskId: String,
        jpeg: ByteArray,
        idempotencyKey: String,
    ) {
        save(taskId, PENDING_PATH, jpeg)
        pendingOf(taskId).writeText(idempotencyKey)
    }

    /** The key of the waiting upload of [taskId], or null when none waits. */
    fun pendingKey(taskId: String): String? = pendingOf(taskId).takeIf { it.exists() && of(taskId).exists() }?.readText()

    /** The server has it under [path]: the copy stays as the one for that path. */
    fun uploaded(
        taskId: String,
        path: String,
    ) {
        metaOf(taskId).writeText(path)
        pendingOf(taskId).delete()
    }

    fun delete(taskId: String) {
        val had = of(taskId).exists()
        of(taskId).delete()
        metaOf(taskId).delete()
        pendingOf(taskId).delete()
        if (had) changes.update { it + 1 }
    }

    private fun metaOf(taskId: String): File = File(dir, "$taskId.path")

    private fun pendingOf(taskId: String): File = File(dir, "$taskId.pending")

    companion object {
        /** The path a picked picture is kept under until the upload gives it a real one. */
        const val PENDING_PATH = "pending"
    }
}

/** The picture attached to a text that waits offline (6a, NUD-138), under the text's key, next to [ExampleFiles]. */
class ExampleDrafts(
    private val dir: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(File(context.filesDir, "photos"), "examples"))

    fun save(
        writeKey: String,
        jpeg: ByteArray,
    ) {
        dir.mkdirs()
        of(writeKey).writeBytes(jpeg)
    }

    /** The picture of the queued text [writeKey], removed from the drafts; null without one. */
    fun take(writeKey: String): ByteArray? {
        val file = of(writeKey).takeIf { it.exists() } ?: return null
        return file.readBytes().also { file.delete() }
    }

    private fun of(writeKey: String): File = File(dir, "draft-$writeKey.jpg")
}
