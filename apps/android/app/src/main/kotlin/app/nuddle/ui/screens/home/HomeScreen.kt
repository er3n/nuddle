package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.nuddle.R
import app.nuddle.api.model.EdgeType
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.ComposeBar
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.StatusPill
import app.nuddle.ui.components.TopBar
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.uppercaseForUi

/**
 * 1a: home with nobody connected and no tasks; 2f and 4c: the task due first as the "Sıradaki" card. Lists and the other
 * home states arrive with the feature slices.
 */
@Immutable
data class HomeUiState(
    val myName: String,
    val unread: Int = 0,
    val compose: ComposeState = ComposeState.Idle,
    val nextUp: NextUpUi? = null,
    val preview: PreviewUi? = null,
    val undo: UndoUi? = null,
    val doneNote: DoneNoteUi? = null,
    /** 4i: my open tasks to other people. */
    val sent: List<SentUi> = emptyList(),
    /** Someone to write to: no invite card then (open-questions#34). */
    val connected: Boolean = false,
    /** 1g: shown instead of the invite card while my invite waits. */
    val pendingInvite: PendingInviteUi? = null,
)

@Immutable
data class PendingInviteCallbacks(
    val onReshare: (PendingInviteUi) -> Unit = {},
    val onRevoke: (PendingInviteUi) -> Unit = {},
)

@Immutable
data class UndoCallbacks(
    val onUndo: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onTimeout: () -> Unit = {},
)

internal const val DASH = 6f

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
    nextUpActions: NextUpCallbacks = NextUpCallbacks(),
    onOpenTask: (taskId: String) -> Unit = {},
    preview: PreviewCallbacks = PreviewCallbacks(),
    undo: UndoCallbacks = UndoCallbacks(),
    pendingInvite: PendingInviteCallbacks = PendingInviteCallbacks(),
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.HOME),
        bottom = {
            state.undo?.let { TaskUndoToast(it, undo, Modifier.padding(bottom = dimens.space10)) }
            state.doneNote?.let { TaskDoneNote(it, Modifier.padding(bottom = dimens.space10)) }
            ComposeBar(state.compose, onComposeTextChange, onComposeSend, onVoiceStart, onVoiceEnd)
        },
    ) {
        TopBar(myName = state.myName, unread = state.unread, onBell = onBell, onProfile = onProfile)
        Text(
            text = stringResource(R.string.home_greeting, state.myName),
            style = DurtTheme.type.h1,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        val nextUp = state.nextUp
        val waiting = state.pendingInvite
        if (waiting != null) {
            PendingInviteCard(state.myName, waiting, { pendingInvite.onReshare(waiting) }, { pendingInvite.onRevoke(waiting) })
        }
        if (nextUp == null && state.sent.isEmpty()) {
            if (waiting == null && !state.connected) InviteCard(state.myName, onInvite)
            EmptyTasks(Modifier.padding(top = dimens.space48))
        } else {
            if (nextUp != null) {
                Text(
                    text = stringResource(R.string.home_next_up).uppercaseForUi(),
                    style = DurtTheme.type.overline,
                    color = colors.textTertiary,
                    modifier = Modifier.semantics { heading() },
                )
                NextUpCard(nextUp, nextUpActions)
            }
            if (state.sent.isNotEmpty()) SentSection(state.sent, fresh = state.undo?.taskId, onOpen = onOpenTask)
            if (waiting == null && !state.connected) CompactInviteCard(onInvite)
        }
    }
    state.preview?.let { PreviewSheet(it, preview) }
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
            DashedPlus(dimens.avatarTopBar, Modifier.offset(x = -dimens.space4))
        }
        Text(stringResource(R.string.home_invite_title), style = DurtTheme.type.brand, color = colors.ink)
        Text(stringResource(R.string.home_invite_body), style = DurtTheme.type.body, color = colors.textSecondary)
        DurtButton(
            text = stringResource(R.string.home_invite_action),
            onClick = onInvite,
            kind = DurtButtonKind.Accent,
            modifier = Modifier.padding(top = dimens.space8).testTag(TestTags.HOME_INVITE),
        )
    }
}

/** A dashed circle with a plus: the person who is not there yet. */
@Composable
internal fun DashedPlus(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Box(
        modifier =
            modifier
                .size(size)
                .drawBehind {
                    drawCircle(
                        color = colors.textMuted,
                        radius = this.size.minDimension / 2 - dimens.borderStrong.toPx(),
                        center = Offset(this.size.width / 2, this.size.height / 2),
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

@PreviewScreens
@Composable
private fun HomeScreenTaskPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    unread = 1,
                    nextUp =
                        NextUpUi(
                            taskId = "task-1",
                            title = "Para çek",
                            detail = "1.000 TL",
                            deadline = DeadlineText.Tomorrow("09:00"),
                            photo = false,
                        ),
                ),
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
private fun HomeScreenUndoPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    nextUp = NextUpUi("task-1", "Para çek", "1.000 TL", DeadlineText.Tomorrow("09:00"), photo = false),
                    undo = UndoUi("task-1", "1000 tl çek yarın 9da", "Para çek", "1.000 TL", DeadlineText.Tomorrow("09:00"), photo = false),
                ),
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
private fun HomeScreenDoneNotePreview() {
    DurtTheme {
        HomeScreen(
            state = HomeUiState(myName = "Eren", doneNote = DoneNoteUi("Para çek", caughtAt = 2)),
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
private fun HomeScreenInviteSentPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    pendingInvite =
                        PendingInviteUi(
                            inviteId = "i1",
                            code = "abc",
                            name = "Selin",
                            relationship = EdgeType.SPOUSE,
                            sharedAt = DeadlineText.Today("14:32"),
                        ),
                ),
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
