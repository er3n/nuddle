package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.ui.screens.person.PersonCallbacks
import app.nuddle.ui.screens.person.PersonScreen
import app.nuddle.ui.screens.person.PersonViewModel

/** The `person/{id}` route (5b). Removing the connection goes back to profile. */
@Composable
fun PersonRoute(
    onBack: () -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) { viewModel.closed.collect { back() } }
    val current = state ?: return
    PersonScreen(
        state = current,
        callbacks =
            PersonCallbacks(
                onBack = onBack,
                onRelationship = viewModel::onRelationship,
                onNotifyMe = viewModel::onNotifyMe,
                onRemove = viewModel::onRemove,
                onSelect = viewModel::onSelect,
                onConfirm = viewModel::onConfirm,
                onDismiss = viewModel::onDismiss,
            ),
    )
}
