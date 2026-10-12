package app.nuddle.ui.screens.photo

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nuddle.ui.theme.DurtTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * The camera of the `e2e` flavor (docs/testing.md, Android `e2e` flavor; NUD-29): the shot is the JPEG the
 * orchestrator put at `files/e2e-photo.jpg`, so a flow can send a photo on an emulator without a scene to film.
 * Only bound when `BuildConfig.INJECTED_PHOTO` is set.
 */
class InjectedPhotoSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CameraSource {
        @Composable
        override fun Viewfinder(modifier: Modifier) {
            Box(modifier.background(DurtTheme.colors.darkBorder))
        }

        override suspend fun capture(): ByteArray? =
            withContext(Dispatchers.IO) { File(context.filesDir, FILE).takeIf { it.exists() }?.readBytes() }

        companion object {
            const val FILE = "e2e-photo.jpg"
        }
    }
