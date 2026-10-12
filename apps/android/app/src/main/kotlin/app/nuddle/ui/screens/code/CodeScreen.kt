package app.nuddle.ui.screens.code

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.CodeInput
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.NoteCard
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.SignInHeader
import app.nuddle.ui.theme.DurtTheme

private const val SECONDS_PER_MINUTE = 60

/**
 * 0d, and 6c after a wrong code. SMS only until WhatsApp exists (NUD-9 decision E). The server does not count
 * attempts yet, so 6c shows no remaining attempts (NUD-9 decision F).
 */
@Immutable
data class CodeUiState(
    val phoneDisplay: String,
    val code: String = "",
    val verifying: Boolean = false,
    val wrongCode: Boolean = false,
    /** Codes left before the wait, once the API said so (6c, open-questions#41). */
    val remainingAttempts: Int? = null,
    /** Too many wrong codes: when a new one can be asked for, "21:15". */
    val lockedUntil: String? = null,
    val unreachable: Boolean = false,
    val resendInSeconds: Int = 0,
)

private fun countdown(seconds: Int): String =
    "%d:%02d".format(
        java.util.Locale.ROOT,
        seconds / SECONDS_PER_MINUTE,
        seconds % SECONDS_PER_MINUTE,
    )

@Composable
fun CodeScreen(
    state: CodeUiState,
    onCodeChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ScreenLayout(
        background = colors.background,
        modifier = modifier,
        bottom = {
            if (state.wrongCode) {
                DurtButton(
                    text = stringResource(R.string.code_resend),
                    onClick = onResend,
                    kind = DurtButtonKind.Secondary,
                    enabled = state.resendInSeconds == 0 && state.lockedUntil == null,
                    modifier = Modifier.testTag(TestTags.CODE_RESEND),
                )
            }
        },
    ) {
        BackBar(title = null, onBack = onBack)
        if (state.wrongCode) {
            SignInHeader(
                stringResource(R.string.code_wrong_title),
                stringResource(R.string.code_wrong_body, state.phoneDisplay),
            )
        } else {
            SignInHeader(stringResource(R.string.code_title), stringResource(R.string.code_body, state.phoneDisplay)) {
                TextLink(
                    stringResource(R.string.code_change_number),
                    onClick = onChangeNumber,
                    style = DurtTheme.type.bodyLarge,
                    modifier = Modifier.testTag(TestTags.CODE_CHANGE_NUMBER),
                )
            }
        }
        CodeInput(
            value = state.code,
            error = state.wrongCode,
            onValueChange = onCodeChange,
            onComplete = onComplete,
            enabled = !state.verifying && state.lockedUntil == null,
            focusRequester = focus,
            modifier = Modifier.testTag(TestTags.CODE_INPUT),
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space12)) {
            when {
                state.wrongCode -> {
                    WrongCode(state)
                }

                state.resendInSeconds > 0 -> {
                    val time = countdown(state.resendInSeconds)
                    val sentence = stringResource(R.string.code_resend_wait, time)
                    val at = sentence.indexOf(time)
                    Text(
                        text =
                            buildAnnotatedString {
                                append(sentence.substring(0, at))
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.ink)) { append(time) }
                                append(sentence.substring(at + time.length))
                            },
                        style = DurtTheme.type.body,
                        color = colors.textSecondary,
                    )
                }

                else -> {
                    TextLink(stringResource(R.string.code_resend), onClick = onResend, modifier = Modifier.testTag(TestTags.CODE_RESEND))
                }
            }
            if (!state.wrongCode) {
                NoteCard {
                    Text(
                        stringResource(R.string.code_hint),
                        style = DurtTheme.type.bodySmall,
                        color = colors.textBody,
                    )
                }
            }
            if (state.unreachable) {
                InlineAlert(
                    stringResource(R.string.error_connection_title),
                    stringResource(R.string.error_connection_body),
                )
            }
        }
    }
}

/** "Kod yanlış." with the codes left and what happens when they run out, or the wait (6c, open-questions#41). */
@Composable
private fun ColumnScope.WrongCode(state: CodeUiState) {
    val colors = DurtTheme.colors
    val until = state.lockedUntil
    val remaining = state.remainingAttempts
    Text(
        text =
            when {
                until != null -> stringResource(R.string.code_locked_title)
                remaining != null -> pluralStringResource(R.plurals.code_wrong_remaining, remaining, remaining)
                else -> stringResource(R.string.code_wrong_error)
            },
        style = DurtTheme.type.bodyStrong,
        color = colors.warning,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    NoteCard {
        Text(
            text = if (until != null) stringResource(R.string.code_locked_body, until) else stringResource(R.string.code_wrong_limit_note),
            style = DurtTheme.type.bodySmall,
            color = colors.textBody,
        )
    }
}

@PreviewScreens
@Composable
private fun CodeScreenPreview() {
    DurtTheme {
        CodeScreen(CodeUiState("+90 532 123 45 67", code = "481", resendInSeconds = 42), {}, {}, {}, {}, {})
    }
}

@PreviewScreens
@Composable
private fun CodeScreenWrongPreview() {
    DurtTheme {
        CodeScreen(CodeUiState("+90 532 123 45 67", code = "481927", wrongCode = true, remainingAttempts = 2), {}, {}, {}, {}, {})
    }
}

@PreviewScreens
@Composable
private fun CodeScreenLockedPreview() {
    DurtTheme {
        CodeScreen(CodeUiState("+90 532 123 45 67", wrongCode = true, remainingAttempts = 0, lockedUntil = "21:15"), {}, {}, {}, {}, {})
    }
}

@PreviewScreens
@Composable
private fun CodeScreenResendPreview() {
    DurtTheme {
        CodeScreen(CodeUiState("+90 532 123 45 67", unreachable = true), {}, {}, {}, {}, {})
    }
}
