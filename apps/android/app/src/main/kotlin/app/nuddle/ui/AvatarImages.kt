package app.nuddle.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.ui.components.LocalAvatars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The cached profile pictures as bitmaps for [LocalAvatars], reloaded whenever a file changes (a sync fetched
 * one, a picture was picked or removed). Decoded off the main thread; a file that cannot be read is skipped.
 */
@Composable
fun rememberAvatarImages(files: AvatarFiles): State<Map<String, ImageBitmap>> {
    val version by files.version.collectAsStateWithLifecycle()
    return produceState(emptyMap(), version) {
        value = withContext(Dispatchers.IO) { files.loadAll() }
    }
}

private fun AvatarFiles.loadAll(): Map<String, ImageBitmap> {
    val dir = of("x").parentFile ?: return emptyMap()
    val pictures = dir.listFiles { file -> file.extension == "jpg" && file.name != "pending.jpg" }.orEmpty()
    return pictures.mapNotNull { file -> decode(file)?.let { file.nameWithoutExtension to it } }.toMap()
}

private fun decode(file: File): ImageBitmap? = BitmapFactory.decodeFile(file.path)?.asImageBitmap()
