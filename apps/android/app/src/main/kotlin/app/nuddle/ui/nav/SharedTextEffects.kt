package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import app.nuddle.ui.screens.home.afterTyped

/**
 * Text shared while the app was open on another screen goes back to home's box first (NUD-127). Signed out, home is
 * not on the back stack yet; the text waits until sign-in reaches it.
 */
@Composable
internal fun BackToHomeOnShare(
    nav: NavHostController,
    sharedText: String?,
) {
    LaunchedEffect(sharedText) { if (sharedText != null) nav.popBackStack(Routes.HOME, inclusive = false) }
}

/**
 * Text shared from another app (NUD-127, docs/ux/platform.md, Other): into home's compose box after what is already
 * typed, as dictation does. Never sent from here; the person edits it and sends it, and the usual confident or
 * preview path follows.
 */
@Composable
fun SharedTextEffect(
    sharedText: String?,
    typed: String,
    onTake: () -> Unit,
    onText: (String) -> Unit,
) {
    val currentTyped by rememberUpdatedState(typed)
    val take by rememberUpdatedState(onTake)
    val text by rememberUpdatedState(onText)
    LaunchedEffect(sharedText) {
        val shared = sharedText ?: return@LaunchedEffect
        take()
        text(afterTyped(currentTyped, shared))
    }
}
