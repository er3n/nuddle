package app.nuddle.data.photo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Instant

/**
 * What goes with a kept photo: the idempotency key of its `photo_sent`, the same on every retry (ADR 0006), and when
 * Gönder was tapped, which the deadline counts from (docs/domain.md, Task states).
 */
data class PendingPhoto(
    val idempotencyKey: String,
    val takenAt: Instant,
)

/** Where a taken photo waits until the server has it (6e: kept on the phone, not lost). One file per task. */
class PhotoFiles(
    private val dir: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.filesDir, "photos"))

    fun of(taskId: String): File = File(dir, "$taskId.jpg")

    fun save(
        taskId: String,
        jpeg: ByteArray,
        sending: PendingPhoto,
    ) {
        dir.mkdirs()
        of(taskId).writeBytes(jpeg)
        metaOf(taskId).writeText("${sending.idempotencyKey}\n${sending.takenAt.toEpochMilliseconds()}")
    }

    /** How the kept photo of [taskId] is sent, or null when there is none. */
    fun pending(taskId: String): PendingPhoto? {
        if (!of(taskId).exists()) return null
        val lines = metaOf(taskId).takeIf { it.exists() }?.readLines() ?: return null
        val key = lines.getOrNull(0) ?: return null
        val taken = lines.getOrNull(1)?.toLongOrNull() ?: return null
        return PendingPhoto(key, Instant.fromEpochMilliseconds(taken))
    }

    fun delete(taskId: String) {
        of(taskId).delete()
        metaOf(taskId).delete()
    }

    private fun metaOf(taskId: String): File = File(dir, "$taskId.meta")
}
