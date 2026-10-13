package app.nuddle.ui.screens.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

// The parts of the next-up card for a task from someone else (4c). Used only by NextUpCard.

@Immutable
data class NextUpCallbacks(
    val onDone: () -> Unit = {},
    /** "Bitti, çek" on a photo task: the camera (4d) for that task. */
    val onTakePhoto: (taskId: String) -> Unit = {},
    /** The photo whose upload failed: the kept photo and Şimdi tekrar dene (6e). */
    val onOpenUnsentPhoto: (taskId: String) -> Unit = {},
    /** The phone number of the person who wrote the task. */
    val onCall: (String) -> Unit = {},
    val onMessage: (String) -> Unit = {},
)

@Composable
private fun WrittenText.text(): String =
    when (this) {
        is WrittenText.Today -> time
        is WrittenText.Yesterday -> stringResource(R.string.written_yesterday, time)
        is WrittenText.OnDate -> stringResource(R.string.deadline_on_date, day, stringArrayResource(R.array.month_names)[month - 1], time)
    }

/** "**Sibel** yazdı · 14:32" with Sibel's avatar. */
@Composable
internal fun FromLine(
    from: FromUi,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val line = stringResource(R.string.home_from_wrote, from.name, from.writtenAt.text())
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.space10)) {
        Avatar(from.name, colors.avatarOthers.first(), dimens.avatarCard)
        Text(
            buildAnnotatedString {
                append(line)
                if (line.startsWith(from.name)) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.ink), 0, from.name.length)
            },
            style = DurtTheme.type.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/** Call and Message (docs/product.md: no in-app chat; docs/ux/platform.md: ACTION_DIAL and smsto:). */
@Composable
internal fun ReachRow(
    phone: String,
    callbacks: NextUpCallbacks,
    note: String? = null,
) {
    val dimens = DurtTheme.dimens
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TODO(NUD-16): "Bugün yapamayacağım" (7f) goes on the left when there is no note.
        if (note != null) {
            Text(note, style = DurtTheme.type.caption, color = DurtTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        RoundIconButton(
            DurtIcons.Phone,
            stringResource(R.string.home_call),
            { callbacks.onCall(phone) },
            Modifier.testTag(TestTags.HOME_CALL),
        )
        RoundIconButton(
            DurtIcons.Message,
            stringResource(R.string.home_message),
            { callbacks.onMessage(phone) },
            Modifier.testTag(TestTags.HOME_MESSAGE),
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Box(
        modifier =
            modifier
                .size(dimens.minTouchTarget)
                .clip(CircleShape)
                .border(dimens.borderCard, colors.border, CircleShape)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = colors.ink, modifier = Modifier.size(dimens.icon))
    }
}

@PreviewScreens
@Composable
private fun HomeScreenTaskFromSibelPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    unread = 1,
                    connected = true,
                    nextUp =
                        NextUpUi(
                            taskId = "task-1",
                            title = "Temizlikçi için para çek",
                            detail = "1.000 TL",
                            deadline = DeadlineText.Tomorrow("09:00"),
                            photo = true,
                            from = FromUi("Sibel", WrittenText.Today("14:32"), "+905320000001"),
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
private fun HomeScreenDoneTaskFromSibelPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    connected = true,
                    nextUp =
                        NextUpUi(
                            taskId = "task-1",
                            title = "Gelirken ekmek al",
                            detail = null,
                            deadline = DeadlineText.Today("19:00"),
                            photo = false,
                            from = FromUi("Sibel", WrittenText.Yesterday("21:10"), "+905320000001"),
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
