package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.ComposeBar
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.TopBar
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/** 1a: home with nobody connected and no tasks. Lists and the other home states arrive with the feature slices. */
@Immutable
data class HomeUiState(
    val myName: String,
    val unread: Int = 0,
    val compose: ComposeState = ComposeState.Idle,
)

private const val DASH = 6f

@Composable
fun HomeScreen(
    state: HomeUiState,
    onBell: () -> Unit,
    onProfile: () -> Unit,
    onInvite: () -> Unit,
    onComposeTextChange: (String) -> Unit,
    onComposeSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier,
        bottom = { ComposeBar(state.compose, onComposeTextChange, onComposeSend, onVoiceStart, onVoiceEnd) },
    ) {
        TopBar(myName = state.myName, unread = state.unread, onBell = onBell, onProfile = onProfile)
        Text(
            text = stringResource(R.string.home_greeting, state.myName),
            style = DurtTheme.type.h1,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        InviteCard(state.myName, onInvite)
        EmptyTasks(Modifier.padding(top = dimens.space48))
    }
}

@Composable
private fun InviteCard(
    myName: String,
    onInvite: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderFocused, colors.ink, shape)
                .padding(dimens.space20),
        verticalArrangement = Arrangement.spacedBy(dimens.space8),
    ) {
        Row(Modifier.clearAndSetSemantics { }) {
            Avatar(myName, colors.avatarSelf, dimens.avatarTopBar)
            Box(
                modifier =
                    Modifier
                        .offset(x = -dimens.space4)
                        .size(dimens.avatarTopBar)
                        .drawBehind {
                            drawCircle(
                                color = colors.textMuted,
                                radius = size.minDimension / 2 - dimens.borderStrong.toPx(),
                                center = Offset(size.width / 2, size.height / 2),
                                style =
                                    Stroke(
                                        width = dimens.borderStrong.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
                                    ),
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    DurtIcons.Plus,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(dimens.icon),
                )
            }
        }
        Text(stringResource(R.string.home_invite_title), style = DurtTheme.type.brand, color = colors.ink)
        Text(stringResource(R.string.home_invite_body), style = DurtTheme.type.body, color = colors.textSecondary)
        DurtButton(
            text = stringResource(R.string.home_invite_action),
            onClick = onInvite,
            kind = DurtButtonKind.Accent,
            modifier = Modifier.padding(top = dimens.space8),
        )
    }
}

@Composable
private fun EmptyTasks(modifier: Modifier = Modifier) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space8),
    ) {
        Icon(
            DurtIcons.Bell,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(dimens.iconHero),
        )
        Text(
            stringResource(R.string.home_empty_title),
            style = DurtTheme.type.bodyLarge.copy(fontWeight = DurtTheme.type.bodyStrong.fontWeight),
            color = colors.ink,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = DurtTheme.type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewScreens
@Composable
private fun HomeScreenPreview() {
    DurtTheme {
        HomeScreen(
            state = HomeUiState(myName = "Eren"),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}

@PreviewScreens
@Composable
private fun HomeScreenPendingPreview() {
    DurtTheme {
        HomeScreen(
            state = HomeUiState(myName = "Eren", unread = 1),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}
