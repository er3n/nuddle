package app.nuddle.data.avatar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The key under which my own picture is kept; connections are kept under their person id. */
const val ME = "me"

/**
 * Profile pictures cached on the phone (NUD-72): one JPEG per person next to the server path it was fetched for,
 * so a sync only downloads a picture whose path changed. My own picture is kept under [ME], and a picked one
 * waits in [pending] until the upload has made it mine.
 */
@Singleton
class AvatarFiles(
    private val dir: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.filesDir, "avatars"))

    private val changes = MutableStateFlow(0L)

    /** Bumped on every change, so screens reload the pictures they show. */
    val version: StateFlow<Long> = changes

    fun of(key: String): File = File(dir, "$key.jpg")

    /** The server path the kept picture of [key] is for, or null without one. */
    fun pathOf(key: String): String? = metaOf(key).takeIf { it.exists() && of(key).exists() }?.readText()

    fun save(
        key: String,
        path: String,
        jpeg: ByteArray,
    ) {
        dir.mkdirs()
        of(key).writeBytes(jpeg)
        metaOf(key).writeText(path)
        changes.update { it + 1 }
    }

    fun delete(key: String) {
        val had = of(key).exists()
        of(key).delete()
        metaOf(key).delete()
        if (had) changes.update { it + 1 }
    }

    /** The picked picture waiting to be uploaded, or null. */
    fun pending(): File? = File(dir, PENDING).takeIf { it.exists() }

    fun savePending(jpeg: ByteArray) {
        dir.mkdirs()
        File(dir, PENDING).writeBytes(jpeg)
    }

    fun deletePending() {
        File(dir, PENDING).delete()
    }

    /** Signing out: no picture of this person or their people stays on the phone. */
    fun deleteAll() {
        dir.deleteRecursively()
        changes.update { it + 1 }
    }

    private fun metaOf(key: String): File = File(dir, "$key.path")

    private companion object {
        const val PENDING = "pending.jpg"
    }
}
