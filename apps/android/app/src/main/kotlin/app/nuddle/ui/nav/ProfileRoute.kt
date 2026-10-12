package app.nuddle.ui.nav

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.profile.ProfileCallbacks
import app.nuddle.ui.screens.profile.ProfileScreen
import app.nuddle.ui.screens.profile.ProfileSheetCallbacks
import app.nuddle.ui.screens.profile.ProfileViewModel

/**
 * The `profile` route (5a) with its sheets (7g, 7h, 7i, rename). A permission row opens the same system page as setup;
 * coming back re-reads the permissions.
 */
@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onInvite: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val signedOut by rememberUpdatedState(onSignedOut)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) { viewModel.signedOut.collect { signedOut() } }
    val current = state ?: return
    ProfileScreen(
        state = current,
        callbacks =
            ProfileCallbacks(
                onBack = onBack,
                onEditName = viewModel::onEditName,
                onOpenPerson = onOpenPerson,
                onInvite = onInvite,
                onUseConnections = viewModel::onUseConnections,
                onNotifyMe = viewModel::onNotifyMe,
                onQuietHours = viewModel::onQuietHours,
                onFixPermission = { step -> activity?.let { it.startActivity(settingsIntent(it, step)) } },
                onLogOut = viewModel::onLogOut,
                onDelete = viewModel::onDelete,
            ),
        sheetCallbacks =
            ProfileSheetCallbacks(
                onNameChange = viewModel.sheets::onNameChange,
                onQuietEdge = viewModel.sheets::onQuietEdge,
                onQuietTime = viewModel.sheets::onQuietTime,
                onConfirm = viewModel.sheets::onConfirm,
                onDismiss = viewModel.sheets::onDismiss,
            ),
    )
}
