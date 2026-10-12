package app.nuddle.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.domain.Ago
import app.nuddle.domain.SetupStep
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.agoText
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.uppercaseForUi

/** An open invite to me, in the bell. */
@Immutable
data class InboxInviteUi(
    val inviteId: String,
    val inviterName: String,
    val ago: Ago,
)

/** 2g, 1c and 7b: warnings and invites under "Yeni", then my tasks to others by day. */
@Immutable
data class InboxUiState(
    val invites: List<InboxInviteUi> = emptyList(),
    /** A poke that did not ring and permissions that are off (2g). */
    val warnings: List<BellWarning> = emptyList(),
    /** My tasks to others from the last week, as text (7b). */
    val days: List<BellDay> = emptyList(),
)

@Composable
fun InboxScreen(
    state: InboxUiState,
    onBack: () -> Unit,
    onOpenInvite: (InboxInviteUi) -> Unit,
    modifier: Modifier = Modifier,
    onOpenTask: (taskId: String) -> Unit = {},
    onFix: (BellWarning) -> Unit = {},
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(background = colors.background, modifier = modifier.testTag(TestTags.INBOX)) {
        BackBar(title = stringResource(R.string.inbox_title), onBack = onBack)
        if (state.invites.isNotEmpty() || state.warnings.isNotEmpty()) {
            Text(
                stringResource(R.string.inbox_new).uppercaseForUi(),
                style = DurtTheme.type.overline,
                color = colors.textTertiary,
                modifier = Modifier.semantics { heading() },
            )
            BellWarningCards(state.warnings, onFix)
            state.invites.forEach { InviteCard(it, onOpen = { onOpenInvite(it) }) }
        }
        BellList(state.days, onOpenTask)
        Text(
            stringResource(R.string.inbox_no_older),
            style = DurtTheme.type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = dimens.space48 * 2),
        )
    }
}

@Composable
private fun InviteCard(
    invite: InboxInviteUi,
    onOpen: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val title = stringResource(R.string.inbox_invite_title, invite.inviterName)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space16),
        verticalArrangement = Arrangement.spacedBy(dimens.space14),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
            Box(Modifier.clearAndSetSemantics { }) {
                Avatar(invite.inviterName, colors.avatarOthers.first(), dimens.avatarInbox)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(dimens.badgeSize)
                        .background(colors.accent, CircleShape)
                        .border(dimens.borderStrong, colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DurtIcons.Plus, contentDescription = null, tint = colors.ink, modifier = Modifier.size(dimens.iconSmall))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append(title)
                        val start = title.indexOf(invite.inviterName)
                        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + invite.inviterName.length)
                    },
                    style = DurtTheme.type.body,
                    color = colors.ink,
                )
                Text(
                    stringResource(R.string.separator_dot, stringResource(R.string.inbox_invite_body), agoText(invite.ago)),
                    style = DurtTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
            Box(Modifier.padding(top = dimens.space6).size(dimens.unreadDot).background(colors.poke, CircleShape))
        }
        DurtButton(
            text = stringResource(R.string.inbox_invite_open),
            onClick = onOpen,
            kind = DurtButtonKind.Accent,
            size = DurtButtonSize.Medium,
            modifier = Modifier.testTag(TestTags.INBOX_OPEN_INVITE),
        )
    }
}

@PreviewScreens
@Composable
private fun InboxScreenPreview() {
    DurtTheme {
        InboxScreen(InboxUiState(listOf(InboxInviteUi("i1", "Selin", Ago.Hours(2)))), onBack = {}, onOpenInvite = {})
    }
}

/** 2g (NotificationsPerm.png). */
@PreviewScreens
@Composable
private fun InboxScreenWarningsPreview() {
    DurtTheme {
        InboxScreen(
            InboxUiState(
                warnings =
                    listOf(
                        BellWarning.MissedRing,
                        BellWarning.PermissionOff(SetupStep.FULL_SCREEN, NextPokeUi("Para çek", PokeDay.Tomorrow)),
                    ),
            ),
            onBack = {},
            onOpenInvite = {},
        )
    }
}

@PreviewScreens
@Composable
private fun InboxScreenEmptyPreview() {
    DurtTheme { InboxScreen(InboxUiState(), onBack = {}, onOpenInvite = {}) }
}
