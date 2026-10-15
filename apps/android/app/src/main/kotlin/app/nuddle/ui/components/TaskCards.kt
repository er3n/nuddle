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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

/** The text color of a status written at the start of a sub line: its pill's, readable on `surface`. */
@Composable
private fun PillStatus.lineColor(): Color {
    val colors = DurtTheme.colors
    return when (this) {
        PillStatus.Scheduled, PillStatus.Dropped, PillStatus.Cancelled -> colors.textSecondary
        PillStatus.New -> colors.ink
        PillStatus.AwaitingMyApproval, PillStatus.AwaitingApproval -> colors.onInfoPill
        PillStatus.Rejected, PillStatus.Queued -> colors.onWarningPill
        PillStatus.Done -> colors.success
        PillStatus.Missed -> colors.danger
    }
}

/**
 * One task in a list: avatar, title, sub line, chevron (docs/ux/components.md 4). Title and sub line are one line each
 * with an ellipsis, so nothing moves when a title is long (NUD-67). The sub line starts with the status unless it is
 * Kurulu. TalkBack reads it as one element (platform.md). [highlighted] draws a 2 dp `accent` border: the task that just
 * went out (4i). [trailing] replaces the chevron.
 */
@Composable
fun TaskRow(
    task: TaskUi,
    /** Null for a row with nothing to open (a text written offline, 6a). */
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    val pill = task.status.label()
    val description = listOf(task.title, task.subtitle, pill).joinToString(", ")
    val statusColor = task.status.lineColor()
    val line =
        buildAnnotatedString {
            if (task.status != PillStatus.Scheduled) {
                withStyle(SpanStyle(color = statusColor, fontWeight = FontWeight.SemiBold)) { append(pill) }
                if (task.subtitle.isNotEmpty()) append(stringResource(R.string.separator_dot, "", task.subtitle))
            } else {
                append(task.subtitle)
            }
        }
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
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(start = dimens.space14, top = dimens.space12, bottom = dimens.space12, end = dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Box(Modifier.clearAndSetSemantics { }) { Avatar(task.personName, task.personColor, dimens.avatarList) }
        Column(Modifier.weight(1f).clearAndSetSemantics { }) {
            Text(task.title, style = DurtTheme.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = DurtTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            trailing != null -> {
                trailing()
            }

            onClick != null -> {
                Icon(
                    DurtIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(dimens.icon),
                )
            }
        }
    }
}

/** The × that takes a row off the list: "Kaldır" for TalkBack. */
@Composable
fun RemoveButton(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.issue_remove)
    Box(
        modifier =
            modifier
                .size(DurtTheme.dimens.minTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            DurtIcons.Close,
            contentDescription = null,
            tint = DurtTheme.colors.textSecondary,
            modifier = Modifier.size(DurtTheme.dimens.icon),
        )
    }
}

@PreviewComponent
@Composable
private fun TaskCardsPreview() {
    PreviewSurface {
        val sibel = DurtTheme.colors.avatarOthers.first()
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            TaskRow(TaskUi("Gelirken ekmek al", "Sibel · bu akşam", PillStatus.Scheduled, "Sibel", sibel), onClick = {})
            TaskRow(
                TaskUi("Kuru temizlemeyi Moda'daki dükkandan al", "Not: yarın hallederim", PillStatus.Dropped, "Sibel", sibel),
                onClick = {},
            )
            TaskRow(TaskUi("Para çek", "bugün 09:00'a kadardı", PillStatus.Missed, "Sibel", sibel), onClick = {})
            TaskRow(
                TaskUi("Ütüyü kaldır", "Dürtmeler durdu", PillStatus.Cancelled, "Sibel", sibel),
                onClick = null,
                trailing = { RemoveButton(onRemove = {}) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space8)) {
                PillStatus.entries.forEach { StatusPill(it, Modifier.padding(bottom = DurtTheme.dimens.space8)) }
            }
        }
    }
}
