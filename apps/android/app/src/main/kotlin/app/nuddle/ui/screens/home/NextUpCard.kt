package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.StatusPill
import app.nuddle.ui.components.UndoToast
import app.nuddle.ui.theme.DurtTheme

// Parts of home once there is a task (2f). Kept next to HomeScreen.kt, used only there.

@Composable
private fun DeadlineText.text(): String =
    when (this) {
        is DeadlineText.Today -> stringResource(R.string.deadline_today, time)
        is DeadlineText.Tomorrow -> stringResource(R.string.deadline_tomorrow, time)
        is DeadlineText.OnDate -> stringResource(R.string.deadline_on_date, day, stringArrayResource(R.array.month_names)[month - 1], time)
    }

/** 2f: title, detail and who it is for, the deadline and how it ends, and "Şimdiden bitti". */
@Composable
internal fun NextUpCard(
    task: NextUpUi,
    onDone: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val toSelf = stringResource(R.string.home_to_self)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space16)
                .testTag(TestTags.HOME_NEXT_UP),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = DurtTheme.type.brand, color = colors.ink)
                Text(
                    text = task.detail?.let { stringResource(R.string.separator_dot, it, toSelf) } ?: toSelf,
                    style = DurtTheme.type.bodySmall,
                    color = colors.textSecondary,
                )
            }
            StatusPill(PillStatus.Scheduled)
        }
        // Both tiles as tall as the taller one when a value wraps at large font scales.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(dimens.space10)) {
            InfoTile(stringResource(R.string.home_deadline_label), task.deadline.text(), Modifier.weight(1f))
            InfoTile(
                label = stringResource(R.string.home_finish_label),
                value = stringResource(if (task.photo) R.string.home_finish_photo else R.string.home_finish_done),
                modifier = Modifier.weight(1f),
            )
        }
        DurtButton(
            text = stringResource(R.string.home_done_early),
            onClick = onDone,
            kind = DurtButtonKind.Success,
            size = DurtButtonSize.Small,
            modifier = Modifier.testTag(TestTags.HOME_DONE_EARLY),
        )
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(colors.background, RoundedCornerShape(dimens.radiusS))
                .padding(horizontal = dimens.space12, vertical = dimens.space10),
    ) {
        Text(label, style = DurtTheme.type.caption, color = colors.textTertiary)
        Text(value, style = DurtTheme.type.bodySmallStrong, color = colors.ink)
    }
}

/** The invite card once there is a task: one dashed row (2f). */
@Composable
internal fun CompactInviteCard(onInvite: () -> Unit) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .dashedBorder(colors.textMuted, dimens.borderStrong, dimens.radiusM)
                .clickable(onClick = onInvite)
                .padding(horizontal = dimens.space14, vertical = dimens.space12)
                .testTag(TestTags.HOME_INVITE),
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashedPlus(dimens.avatarList)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_invite_title), style = DurtTheme.type.bodySmallStrong, color = colors.ink)
            Text(stringResource(R.string.home_invite_compact_body), style = DurtTheme.type.caption, color = colors.textSecondary)
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    width: Dp,
    radius: Dp,
): Modifier =
    drawBehind {
        val stroke = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
        )
    }

/** The deadline inside a sentence: "yarın 09:00". */
@Composable
private fun DeadlineText.inlineText(): String =
    when (this) {
        is DeadlineText.Today -> stringResource(R.string.deadline_today_inline, time)
        is DeadlineText.Tomorrow -> stringResource(R.string.deadline_tomorrow_inline, time)
        is DeadlineText.OnDate -> stringResource(R.string.deadline_on_date, day, stringArrayResource(R.array.month_names)[month - 1], time)
    }

/** 2a: "Kuruldu · Para çek · yarın 09:00" with Geri al and Düzenle (UndoToast, components.md 6). */
@Composable
internal fun SelfUndoToast(
    task: UndoUi,
    callbacks: UndoCallbacks,
    modifier: Modifier = Modifier,
) {
    val parts = listOfNotNull(task.title, task.deadline.inlineText(), if (task.photo) stringResource(R.string.undo_finish_photo) else null)
    UndoToast(
        title = stringResource(R.string.undo_self_title),
        subtitle = parts.reduce { line, part -> stringResource(R.string.separator_dot, line, part) },
        onUndo = callbacks.onUndo,
        onEdit = callbacks.onEdit,
        onTimeout = callbacks.onTimeout,
        modifier = modifier.testTag(TestTags.HOME_UNDO),
    )
}
