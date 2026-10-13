package app.nuddle.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.nuddle.ui.components.ComposeState

/** The mic's two callbacks for ComposeBar. */
class VoiceCallbacks(
    val onStart: () -> Unit,
    val onEnd: (cancelled: Boolean) -> Unit,
)

/**
 * 4h on home: the first tap asks for the microphone (the person taps again once it is granted), then each tap
 * listens; every transcript goes to [onSpeak] to go into the compose box (open-questions#17, #40).
 */
@Composable
fun rememberVoiceInput(
    voice: VoiceViewModel,
    onSpeak: (String) -> Unit,
): VoiceCallbacks {
    val context = LocalContext.current
    val speak by rememberUpdatedState(onSpeak)
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) voice.onPermissionDenied()
        }
    LaunchedEffect(voice) { voice.transcripts.collect { speak(it) } }
    return remember(voice, ask) {
        VoiceCallbacks(
            onStart = {
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) voice.onStart() else ask.launch(Manifest.permission.RECORD_AUDIO)
            },
            onEnd = voice::onEnd,
        )
    }
}

/** What is already in the compose box; dictation goes after it (NUD-48). */
fun ComposeState?.typedText(): String =
    when (this) {
        is ComposeState.Typing -> text
        is ComposeState.Failed -> text
        else -> ""
    }

/** The box text with [heard] after what was typed. */
fun afterTyped(
    typed: String,
    heard: String,
): String = listOf(typed, heard).filter { it.isNotBlank() }.joinToString(" ")
