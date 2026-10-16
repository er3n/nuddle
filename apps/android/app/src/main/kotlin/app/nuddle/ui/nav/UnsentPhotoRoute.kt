package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.photo.UnsentPhotoScreen
import app.nuddle.ui.screens.photo.UnsentPhotoViewModel

/** The `unsent/{taskId}` route (6e). */
@Composable
fun UnsentPhotoRoute(
    onBack: () -> Unit,
    viewModel: UnsentPhotoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackOnClose(viewModel.close, onBack)
    val current = state ?: return
    UnsentPhotoScreen(current, onBack = onBack, onRetry = viewModel::onRetry)
}
