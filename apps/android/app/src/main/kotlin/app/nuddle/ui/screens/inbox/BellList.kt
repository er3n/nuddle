package app.nuddle.ui.screens.inbox

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.InboxItem
import app.nuddle.ui.components.InboxTone
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.home.inlineText
import app.nuddle.ui.screens.home.startPhrase
import app.nuddle.ui.theme.DurtTheme

@Composable
private fun WrittenText.dayTitle(): String =
    when (this) {
        is WrittenText.Today -> stringResource(R.string.inbox_today)
        is WrittenText.Yesterday -> stringResource(R.string.inbox_yesterday)
        is WrittenText.OnDate -> stringResource(R.string.inbox_on_date, day, stringArrayResource(R.array.month_names)[month - 1])
    }

@Composable
private fun Finished.text(): String =
    stringResource(
        when (this) {
            Finished.SaidDone -> R.string.bell_said_done
            Finished.NoPhoto -> R.string.bell_no_photo
            Finished.Late -> R.string.bell_late
            Finished.Approved -> R.string.bell_approved
            Finished.AutoApproved -> R.string.bell_auto_approved
        },
    )

/** 7b: my tasks to others as text, one group per day, then the note that finished tasks only sit here. */
@Composable
internal fun ColumnScope.BellList(
    days: List<BellDay>,
    onOpenTask: (taskId: String) -> Unit,
) {
    days.forEach { day ->
        SettingsGroup(day.day.dayTitle()) {
            day.entries.forEachIndexed { index, entry ->
                if (index > 0) GroupDivider()
                BellRow(entry, onOpenTask)
            }
        }
    }
    if (days.isNotEmpty()) {
        Text(
            stringResource(R.string.bell_footer),
            style = DurtTheme.type.caption,
            color = DurtTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = DurtTheme.dimens.space4),
        )
    }
}

@Composable
private fun BellRow(
    entry: BellEntry,
    onOpenTask: (taskId: String) -> Unit,
) {
    val modifier = Modifier.testTag(TestTags.bellEntry(entry.taskId))
    when (entry) {
        is BellEntry.Unreached -> {
            InboxItem(
                icon = DurtIcons.Warning,
                title = stringResource(R.string.bell_unreached_title, entry.name, entry.title),
                subtitle = stringResource(R.string.bell_unreached_body, entry.deadline.startPhrase()),
                time = entry.time,
                unread = entry.open,
                onClick = if (entry.open) ({ onOpenTask(entry.taskId) }) else null,
                modifier = modifier,
            )
        }

        is BellEntry.Done -> {
            InboxItem(
                icon = DurtIcons.Check,
                title = stringResource(R.string.bell_done_title, entry.name, entry.title),
                subtitle = entry.how.text(),
                time = entry.time,
                unread = false,
                onClick = null,
                modifier = modifier,
                tone = InboxTone.Success,
            )
        }

        is BellEntry.Dropped -> {
            InboxItem(
                icon = DurtIcons.Close,
                title = stringResource(R.string.bell_dropped_title, entry.name, entry.title),
                subtitle = entry.note?.let { stringResource(R.string.issue_note, it) } ?: stringResource(R.string.status_dropped),
                time = entry.time,
                unread = false,
                onClick = null,
                modifier = modifier,
                tone = InboxTone.Neutral,
            )
        }
    }
}

/** 7b as Sibel sees it (SibelBell.png). */
@PreviewScreens
@Composable
private fun InboxScreenBellPreview() {
    val today = WrittenText.Today("08:20")
    val yesterday = WrittenText.Yesterday("19:02")
    val days =
        listOf(
            BellDay(
                today,
                listOf(
                    BellEntry.Unreached("t1", "Eren", "Temizlikçi için para çek", "08:20", DeadlineText.Today("09:00"), open = true),
                    BellEntry.Done("t2", "Eren", "Çöpü at", "07:58", Finished.SaidDone),
                ),
            ),
            BellDay(
                yesterday,
                listOf(
                    BellEntry.Done("t3", "Eren", "Gelirken ekmek al", "19:02", Finished.Approved),
                    BellEntry.Dropped("t4", "Eren", "Kuru temizleme", "17:40", "Yarın hallederim"),
                ),
            ),
        )
    DurtTheme { InboxScreen(InboxUiState(days = days), onBack = {}, onOpenInvite = {}) }
}
