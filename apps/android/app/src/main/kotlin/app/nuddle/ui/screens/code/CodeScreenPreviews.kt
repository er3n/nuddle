package app.nuddle.ui.screens.code

import androidx.compose.runtime.Composable
import app.nuddle.data.CodeChannel
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

// The WhatsApp states of 0d (ADR 0029), next to CodeScreen.kt so that file keeps to detekt's function limit.

@PreviewScreens
@Composable
private fun CodeScreenWhatsAppPreview() {
    DurtTheme {
        CodeScreen(CodeUiState("+90 532 123 45 67", resendInSeconds = 58, channel = CodeChannel.WhatsApp), {}, {}, {}, {}, {})
    }
}

@PreviewScreens
@Composable
private fun CodeScreenWhatsAppSmsOfferedPreview() {
    DurtTheme {
        CodeScreen(
            CodeUiState("+90 532 123 45 67", resendInSeconds = 54, channel = CodeChannel.WhatsApp, smsOffered = true),
            {},
            {},
            {},
            {},
            {},
        )
    }
}

@PreviewScreens
@Composable
private fun CodeScreenAlsoSentBySmsPreview() {
    DurtTheme {
        CodeScreen(CodeUiState("+90 532 123 45 67", resendInSeconds = 47, alsoSentBySms = true), {}, {}, {}, {}, {})
    }
}
