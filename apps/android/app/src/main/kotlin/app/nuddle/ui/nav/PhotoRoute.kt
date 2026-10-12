package app.nuddle.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.photo.PhotoCallbacks
import app.nuddle.ui.screens.photo.PhotoScreen
import app.nuddle.ui.screens.photo.PhotoViewModel

/**
 * The `photo/{taskId}` route (4d). Asks for the camera the first time (docs/ux/platform.md, Permissions); a refusal
 * or a phone without a camera turns the screen into the plain Bitti fallback.
 */
@Composable
fun PhotoRoute(
    onClose: () -> Unit,
    viewModel: PhotoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) { viewModel.close.collect { close() } }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            if (!ok) viewModel.onCameraDenied()
        }
    LaunchedEffect(Unit) {
        when {
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> viewModel.onCameraDenied()
            !granted -> ask.launch(Manifest.permission.CAMERA)
        }
    }
    val current = state ?: return
    PhotoScreen(
        state = current,
        callbacks =
            PhotoCallbacks(
                onClose = onClose,
                onShoot = viewModel::onShoot,
                onRetake = viewModel::onRetake,
                onSend = viewModel::onSend,
                onDoneWithoutPhoto = viewModel::onDoneWithoutPhoto,
            ),
        viewfinder = { modifier -> if (granted) viewModel.camera.Viewfinder(modifier) },
    )
}
