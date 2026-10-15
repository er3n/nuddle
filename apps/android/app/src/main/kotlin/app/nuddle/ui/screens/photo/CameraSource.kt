package app.nuddle.ui.screens.photo

import android.content.Context
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.nuddle.data.photo.toJpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/** Which camera the viewfinder shows (4d, "Kamerayı çevir"). */
enum class Lens { BACK, FRONT }

/** The in-app camera (docs/ux/platform.md): a live viewfinder, one shot as JPEG, and the lens to flip (NUD-73). */
interface CameraSource {
    @Composable
    fun Viewfinder(modifier: Modifier)

    /** Null when the shot failed; the person can try again. */
    suspend fun capture(): ByteArray?

    val lens: StateFlow<Lens>

    /** The other camera, when the phone has one. */
    fun flip()
}

class CameraXSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CameraSource {
        private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        private val current = MutableStateFlow(Lens.BACK)
        override val lens: StateFlow<Lens> = current.asStateFlow()

        override fun flip() {
            current.update { if (it == Lens.BACK) Lens.FRONT else Lens.BACK }
        }

        @Composable
        override fun Viewfinder(modifier: Modifier) {
            val lifecycle = LocalLifecycleOwner.current
            val lens by current.collectAsState()
            var request by remember { mutableStateOf<SurfaceRequest?>(null) }
            // Re-bound on every flip; a lens the phone lacks falls back to the other one.
            LaunchedEffect(lifecycle, lens) {
                val provider = ProcessCameraProvider.awaitInstance(context)
                val preview = Preview.Builder().build().also { it.setSurfaceProvider { r -> request = r } }
                val wanted = if (lens == Lens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val selector = if (provider.hasCamera(wanted)) wanted else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, selector, preview, imageCapture)
                try {
                    awaitCancellation()
                } finally {
                    provider.unbindAll()
                }
            }
            request?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
        }

        override suspend fun capture(): ByteArray? =
            suspendCancellableCoroutine { continuation ->
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val jpeg = image.use { it.toBitmap().toJpeg(rotationDegrees = it.imageInfo.rotationDegrees) }
                            continuation.resume(jpeg)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resume(null)
                        }
                    },
                )
            }
    }
