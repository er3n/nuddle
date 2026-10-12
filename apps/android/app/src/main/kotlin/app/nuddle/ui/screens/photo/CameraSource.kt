package app.nuddle.ui.screens.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.max

/** The in-app camera (docs/ux/platform.md: CameraX, no gallery): a live viewfinder and one shot as JPEG. */
interface CameraSource {
    @Composable
    fun Viewfinder(modifier: Modifier)

    /** Null when the shot failed; the person can try again. */
    suspend fun capture(): ByteArray?
}

/** Longest side of a sent photo: enough to read a receipt, small enough for a slow connection. */
private const val MAX_SIDE = 1600
private const val JPEG_QUALITY = 85

class CameraXSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CameraSource {
        private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

        @Composable
        override fun Viewfinder(modifier: Modifier) {
            val lifecycle = LocalLifecycleOwner.current
            var request by remember { mutableStateOf<SurfaceRequest?>(null) }
            LaunchedEffect(lifecycle) {
                val provider = ProcessCameraProvider.awaitInstance(context)
                val preview = Preview.Builder().build().also { it.setSurfaceProvider { r -> request = r } }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
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
                            val jpeg = image.use { it.toUprightJpeg() }
                            continuation.resume(jpeg)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resume(null)
                        }
                    },
                )
            }
    }

private fun ImageProxy.toUprightJpeg(): ByteArray {
    val source = toBitmap()
    val scale = minOf(1f, MAX_SIDE.toFloat() / max(source.width, source.height))
    val matrix =
        Matrix().apply {
            postScale(scale, scale)
            postRotate(imageInfo.rotationDegrees.toFloat())
        }
    val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    return ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}
