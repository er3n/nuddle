package app.nuddle.ui.screens.errors

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

@Immutable
data class GenericErrorCallbacks(
    val onRetry: () -> Unit = {},
    /** "Ana ekrana dön" and the system back. */
    val onHome: () -> Unit = {},
)

/** 6f: the server broke on our side; [code] is the `DRT-xxxx` it answered with (open-questions#39). */
@Composable
fun GenericErrorScreen(
    code: String,
    callbacks: GenericErrorCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.GENERIC_ERROR),
        horizontalAlignment = Alignment.CenterHorizontally,
        bottom = {
            DurtButton(
                text = stringResource(R.string.generic_error_retry),
                onClick = callbacks.onRetry,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.GENERIC_ERROR_RETRY),
            )
            DurtButton(
                text = stringResource(R.string.generic_error_home),
                onClick = callbacks.onHome,
                kind = DurtButtonKind.Ghost,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.GENERIC_ERROR_HOME),
            )
            Text(
                stringResource(R.string.generic_error_code, code),
                style = DurtTheme.type.caption,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        // Nothing at the top: the content is centered like 6d's.
        top = {},
    ) {
        CenteredError(
            icon = DurtIcons.Warning,
            circle = colors.warningPill,
            tint = colors.onWarningPill,
            title = stringResource(R.string.generic_error_title),
            body = stringResource(R.string.generic_error_body),
        )
    }
}

/** ErrGeneric.png */
@PreviewScreens
@Composable
private fun GenericErrorScreenPreview() {
    DurtTheme { GenericErrorScreen("DRT-5001", GenericErrorCallbacks()) }
}
