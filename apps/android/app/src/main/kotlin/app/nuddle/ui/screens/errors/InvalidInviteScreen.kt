package app.nuddle.ui.screens.errors

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

@Immutable
data class InvalidInviteCallbacks(
    /** The back arrow and "Ana ekrana dön". */
    val onHome: () -> Unit = {},
    /** "Birini ben davet edeyim": the invite screen (1b). */
    val onInvite: () -> Unit = {},
)

/** 6d: an invite link whose invite is used, revoked or not mine (open-questions#39). */
@Composable
fun InvalidInviteScreen(
    callbacks: InvalidInviteCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.INVALID_INVITE),
        horizontalAlignment = Alignment.CenterHorizontally,
        bottom = {
            DurtButton(
                text = stringResource(R.string.invalid_invite_home),
                onClick = callbacks.onHome,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.INVALID_INVITE_HOME),
            )
            DurtButton(
                text = stringResource(R.string.invalid_invite_invite),
                onClick = callbacks.onInvite,
                kind = DurtButtonKind.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        top = { BackBar(title = null, onBack = callbacks.onHome) },
    ) {
        CenteredError(
            icon = DurtIcons.LinkOff,
            circle = colors.neutralPill,
            tint = colors.textSecondary,
            title = stringResource(R.string.invalid_invite_title),
            body = stringResource(R.string.invalid_invite_body),
        )
    }
}

/** ErrInvite.png */
@PreviewScreens
@Composable
private fun InvalidInviteScreenPreview() {
    DurtTheme { InvalidInviteScreen(InvalidInviteCallbacks()) }
}
