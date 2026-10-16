package app.nuddle.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Goes back once, on the first [close], as soon as this route is the resumed entry. A closed task's view model says
 * close again on every later write (the done command's sync) while the popped entry still runs its exit animation;
 * going back for each of those popped home too and left an empty, white window (NUD-96).
 */
@Composable
fun BackOnClose(
    close: Flow<Unit>,
    onBack: () -> Unit,
) {
    val back by rememberUpdatedState(onBack)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(close, lifecycle) {
        close.first()
        lifecycle.withResumed { back() }
    }
}
