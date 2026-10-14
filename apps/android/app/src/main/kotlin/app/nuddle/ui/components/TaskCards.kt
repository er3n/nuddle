package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

/** Pill states from docs/ux/screens.md (Status to UI). Approval reads differently for the sender and the recipient. */
enum class PillStatus { Scheduled, AwaitingMyApproval, AwaitingApproval, Rejected, Done, Dropped, Missed, Cancelled, Queued, New }

@Immutable
data class TaskUi(
    val title: String,
    val subtitle: String,
    val status: PillStatus,
    val personName: String,
    val personColor: Color,
)

data class CardAction(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val testTag: String = "",
)

@Composable
private fun PillStatus.label(): String =
    stringResource(
        when (this) {
            PillStatus.Scheduled -> R.string.status_scheduled
            PillStatus.AwaitingMyApproval -> R.string.status_awaiting_my_approval
            PillStatus.AwaitingApproval -> R.string.status_awaiting_approval
            PillStatus.Rejected -> R.string.status_rejected
            PillStatus.Done -> R.string.status_done
            PillStatus.Dropped -> R.string.status_dropped
            PillStatus.Missed -> R.string.status_missed
            PillStatus.Cancelled -> R.string.status_cancelled
            PillStatus.Queued -> R.string.status_queued
            PillStatus.New -> R.string.status_new
        },
    )

/** 12 sp / 600 pill, 24 dp high (docs/ux/components.md, 4). Colors from the Status to UI table. */
@Composable
fun StatusPill(
    status: PillStatus,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val (background, content) =
        when (status) {
            PillStatus.Scheduled, PillStatus.Dropped, PillStatus.Cancelled, PillStatus.New -> colors.neutralPill to colors.onNeutralPill
            PillStatus.AwaitingMyApproval, PillStatus.AwaitingApproval -> colors.infoPill to colors.onInfoPill
            PillStatus.Rejected, PillStatus.Queued -> colors.warningPill to colors.onWarningPill
            PillStatus.Done -> colors.successSoft to colors.onSuccessSoft
            PillStatus.Missed -> colors.danger to colors.surface
        }
    Text(
        text = status.label(),
        style = DurtTheme.type.micro,
        color = content,
        maxLines = 1,
        modifier =
            modifier
                .heightIn(min = dimens.pillHeight)
                .background(background, RoundedCornerShape(dimens.radiusPill))
                .padding(horizontal = dimens.pillPaddingHorizontal, vertical = dimens.space4),
    )
}

/**
 * One task in a list: avatar, title, sub line, pill. TalkBack reads it as one element (platform.md). [highlighted]
 * draws a 2 dp `accent` border: the task that just went out (4i).
 */
@Composable
fun TaskRow(
    task: TaskUi,
    /** Null for a row with nothing to open (a text written offline, 6a). */
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    val pill = task.status.label()
    val description = listOf(task.title, task.subtitle, pill).joinToString(", ")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .then(
                    if (highlighted) {
                        Modifier.border(dimens.borderFocused, colors.accent, shape)
                    } else {
                        Modifier.border(dimens.borderCard, colors.border, shape)
                    },
                ).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .clearAndSetSemantics { contentDescription = description }
                .padding(horizontal = dimens.space14, vertical = dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Avatar(task.personName, task.personColor, dimens.avatarList)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = DurtTheme.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(task.subtitle, style = DurtTheme.type.caption, color = colors.textSecondary)
        }
        StatusPill(task.status)
    }
}

/** A task that needs a decision (missed, dropped, awaiting approval): message box and one or two actions. */
@Composable
fun TaskActionCard(
    task: TaskUi,
    message: String,
    primary: CardAction,
    modifier: Modifier = Modifier,
    secondary: CardAction? = null,
    /** A close button (×, "Kaldır") in the top right: the recipient's missed card (NUD-49). */
    onRemove: (() -> Unit)? = null,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space16),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
            Avatar(task.personName, task.personColor, dimens.avatarCard)
            Column(Modifier.weight(1f)) {
                Text(task.title, style = DurtTheme.type.bodyStrong, color = colors.ink)
                Text(task.subtitle, style = DurtTheme.type.caption, color = colors.textSecondary)
            }
            StatusPill(task.status)
            if (onRemove != null) RemoveButton(onRemove)
        }
        Text(
            text = message,
            style = DurtTheme.type.bodySmall,
            color = colors.textBody,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(dimens.radiusS))
                    .padding(dimens.space12),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
            DurtButton(
                text = primary.text,
                onClick = primary.onClick,
                size = DurtButtonSize.Compact,
                leadingIcon = primary.icon,
                modifier = Modifier.weight(1f).testTag(primary.testTag),
            )
            if (secondary != null) {
                DurtButton(
                    text = secondary.text,
                    onClick = secondary.onClick,
                    kind = DurtButtonKind.Secondary,
                    size = DurtButtonSize.Compact,
                    leadingIcon = secondary.icon,
                    modifier = Modifier.weight(1f).testTag(secondary.testTag),
                )
            }
        }
    }
}

/** The × of a card that can be taken off the list (NUD-49). */
@Composable
private fun RemoveButton(onRemove: () -> Unit) {
    val description = stringResource(R.string.issue_remove)
    Box(
        modifier =
            Modifier
                .size(DurtTheme.dimens.iconLarge)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = description }
                .testTag(TestTags.ISSUE_REMOVE),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DurtIcons.Close, contentDescription = null, tint = DurtTheme.colors.textSecondary)
    }
}

@PreviewComponent
@Composable
private fun TaskCardsPreview() {
    PreviewSurface {
        val sibel = DurtTheme.colors.avatarOthers.first()
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            TaskRow(TaskUi("Gelirken ekmek al", "Sibel · bu akşam", PillStatus.Scheduled, "Sibel", sibel), onClick = {})
            TaskActionCard(
                task = TaskUi("Para çek", "Sibel · bugün 09:00'a kadardı", PillStatus.Missed, "Sibel", sibel),
                message = "Son zaman geçti. Sibel'e haber verdim.",
                primary = CardAction("Sonradan yaptım", {}),
                secondary = CardAction("Ara", {}),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space8)) {
                PillStatus.entries.forEach { StatusPill(it, Modifier.padding(bottom = DurtTheme.dimens.space8)) }
            }
        }
    }
}
