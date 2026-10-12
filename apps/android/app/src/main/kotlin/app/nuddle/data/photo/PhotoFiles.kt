package app.nuddle.data.photo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

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
    ) {
        dir.mkdirs()
        of(taskId).writeBytes(jpeg)
    }

    fun delete(taskId: String) {
        of(taskId).delete()
    }
}
