package app.nuddle.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

// The parts of the next-up card once a photo is involved (4e, 4g). Used only by NextUpCard. Copy without a suffix
// after a name or a time: open-questions#35.

/** 4e: the photo that went out, who looks at it, when it went and that the pokes stopped. */
@Composable
internal fun PhotoWaiting(
    task: NextUpUi,
    photo: ImageBitmap?,
    onOpenUnsent: (String) -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val thumbShape = RoundedCornerShape(dimens.radiusS)
    // A failed upload opens 6e: the kept photo and Şimdi tekrar dene.
    val open =
        if (task.uploadFailed) {
            Modifier.clickable(role = Role.Button) { onOpenUnsent(task.taskId) }.testTag(TestTags.HOME_UNSENT_PHOTO)
        } else {
            Modifier
        }
    Row(
        modifier = open,
        horizontalArrangement = Arrangement.spacedBy(dimens.space14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.photoThumb)
                .clip(thumbShape)
                .background(colors.surfaceMuted)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(dimens.photoThumb))
            } else {
                Icon(DurtIcons.Camera, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(dimens.iconLarge))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.photo_waiting, task.from?.name.orEmpty()),
                style = DurtTheme.type.body,
                color = colors.ink,
            )
            Text(
                when {
                    task.photoSentAt != null -> stringResource(R.string.photo_waiting_sent, task.photoSentAt)
                    task.uploadFailed -> stringResource(R.string.photo_waiting_failed)
                    else -> stringResource(R.string.photo_waiting_sending)
                },
                style = if (task.uploadFailed) DurtTheme.type.captionStrong else DurtTheme.type.caption,
                color = if (task.uploadFailed) colors.warning else colors.textSecondary,
            )
        }
    }
}

/** 4g: the sender's note with Tekrar iste, and that the pokes go on (the next time stays hidden, open-questions#3). */
@Composable
internal fun ColumnScope.ReRequested(task: NextUpUi) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val from = task.from
    if (task.note != null && from != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space10), verticalAlignment = Alignment.Top) {
            Box(Modifier.clearAndSetSemantics { }) { Avatar(from.name, colors.avatarOthers.first(), dimens.avatarCard) }
            Text(
                task.note,
                style = DurtTheme.type.body,
                color = colors.ink,
                modifier =
                    Modifier
                        .weight(1f)
                        .background(colors.background, RoundedCornerShape(dimens.radiusM))
                        .padding(horizontal = dimens.space14, vertical = dimens.space10),
            )
        }
    }
    Text(stringResource(R.string.rerequest_resumes), style = DurtTheme.type.captionStrong, color = colors.warning)
}

private val selinsTask =
    NextUpUi(
        taskId = "task-1",
        title = "Temizlikçi için para çek",
        detail = "1.000 TL",
        deadline = DeadlineText.Today("09:00"),
        photo = true,
        from = FromUi("Selin", WrittenText.Yesterday("14:32"), "+905320000001"),
    )

@Composable
private fun HomeWith(task: NextUpUi) {
    DurtTheme {
        HomeScreen(
            state = HomeUiState(myName = "Eren", connected = true, nextUp = task),
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
private fun HomeScreenAwaitingApprovalPreview() {
    HomeWith(selinsTask.copy(status = NextUpStatus.AwaitingApproval, photoSentAt = "08:24"))
}

@PreviewScreens
@Composable
private fun HomeScreenReRequestedPreview() {
    HomeWith(selinsTask.copy(status = NextUpStatus.ReRequested, note = "1.500 lazımdı, 500 daha çekebilir misin?"))
}
