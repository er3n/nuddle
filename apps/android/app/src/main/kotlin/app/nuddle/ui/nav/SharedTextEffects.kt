package app.nuddle.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import app.nuddle.data.SharedImage
import app.nuddle.ui.screens.home.afterTyped

/**
 * Text shared while the app was open on another screen goes back to home's box first (NUD-127). Signed out, home is
 * not on the back stack yet; the text waits until sign-in reaches it.
 */
@Composable
internal fun BackToHomeOnShare(
    nav: NavHostController,
    sharedText: String?,
    sharedImage: SharedImage? = null,
) {
    LaunchedEffect(sharedText, sharedImage) {
        if (sharedText != null || sharedImage != null) nav.popBackStack(Routes.HOME, inclusive = false)
    }
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

/**
 * An image shared from another app (NUD-138, docs/ux/platform.md, Other): attached in home's compose bar as the
 * example photo, its text (if any) in the box after what is already typed. Never sent from here.
 */
@Composable
fun SharedImageEffect(
    sharedImage: SharedImage?,
    typed: String,
    onTake: () -> Unit,
    onText: (String) -> Unit,
    onImage: (Uri) -> Unit,
) {
    val currentTyped by rememberUpdatedState(typed)
    val take by rememberUpdatedState(onTake)
    val text by rememberUpdatedState(onText)
    val image by rememberUpdatedState(onImage)
    LaunchedEffect(sharedImage) {
        val shared = sharedImage ?: return@LaunchedEffect
        take()
        image(shared.uri)
        shared.text?.let { text(afterTyped(currentTyped, it)) }
    }
}
