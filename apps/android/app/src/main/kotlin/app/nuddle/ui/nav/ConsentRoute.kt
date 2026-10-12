package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.data.session.SessionStore
import app.nuddle.ui.screens.LightSystemBarIcons
import app.nuddle.ui.screens.consent.ConsentCallbacks
import app.nuddle.ui.screens.consent.ConsentEffect
import app.nuddle.ui.screens.consent.ConsentScreen
import app.nuddle.ui.screens.consent.ConsentViewModel
import app.nuddle.ui.screens.consent.MatchedScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** The `consent/{inviteId}` route (0f, 1d). */
@Composable
fun ConsentRoute(
    onAccept: (inviterName: String, setupNeeded: Boolean) -> Unit,
    onClose: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accept by rememberUpdatedState(onAccept)
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ConsentEffect.Accepted -> accept(effect.inviterName, effect.setupNeeded)
                ConsentEffect.Closed -> close()
            }
        }
    }
    val current = state ?: return
    ConsentScreen(
        state = current,
        callbacks =
            ConsentCallbacks(
                onAllow = viewModel::onAllow,
                onLater = viewModel::onLater,
                onDecline = viewModel::onDecline,
                onChangeRelationship = viewModel::onChangeRelationship,
                onPick = viewModel::onPick,
                onPickDismiss = viewModel::onPickDismiss,
                onBack = onClose,
            ),
    )
}

/** My own name for the two avatars on 0g. */
@HiltViewModel
class MatchedViewModel
    @Inject
    constructor(
        sessions: SessionStore,
    ) : ViewModel() {
        val myName = sessions.displayName
    }

/** The `matched/{name}` route (0g). */
@Composable
fun MatchedRoute(
    inviterName: String,
    onStart: () -> Unit,
    viewModel: MatchedViewModel = hiltViewModel(),
) {
    val myName by viewModel.myName.collectAsStateWithLifecycle(initialValue = null)
    LightSystemBarIcons()
    MatchedScreen(inviterName = inviterName, myName = myName.orEmpty(), onStart = onStart)
}
