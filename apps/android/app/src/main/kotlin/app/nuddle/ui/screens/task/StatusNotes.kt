package app.nuddle.ui.screens.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.nuddle.R
import app.nuddle.api.model.Reachability
import app.nuddle.data.Delivery
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.LineRow
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.components.StatusPill
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.FromLine
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.MissedUi
import app.nuddle.ui.screens.home.NewDeadlineCallbacks
import app.nuddle.ui.screens.home.NewDeadlineSheet
import app.nuddle.ui.screens.home.SentIssue
import app.nuddle.ui.screens.home.SentIssueUi
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.home.message
import app.nuddle.ui.screens.home.text
import app.nuddle.ui.screens.home.timeLabel
import app.nuddle.ui.screens.poke.DropCallbacks
import app.nuddle.ui.screens.poke.DropSheet
import app.nuddle.ui.theme.DurtColors
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime

/** The small lines under the status sentence: delivery, the pokes so far (NUD-81), a snooze. One Column, one emitter. */
@Composable
internal fun StatusNotes(state: TaskDetailUiState) {
    val colors = DurtTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space12)) {
        // Nothing while only sent: the phone may be fine and the server just has not said so yet.
        state.delivery
            ?.takeIf { it != Delivery.Sent && state.status.open }
            ?.let {
                Text(
                    stringResource(it.sentence()),
                    style = DurtTheme.type.bodySmallStrong,
                    color = if (it == Delivery.Seen) colors.success else colors.textSecondary,
                    modifier = Modifier.testTag(TestTags.TASK_DELIVERY),
                )
            }
        state.pokesSoFar?.let {
            Text(
                pluralStringResource(R.plurals.task_pokes_so_far, it.count, it.count, it.lastAt),
                style = DurtTheme.type.bodySmallStrong,
                color = colors.textSecondary,
                modifier = Modifier.testTag(TestTags.TASK_POKES),
            )
        }
        state.snoozedUntil?.takeIf { state.status == DetailStatus.Scheduled }?.let {
            Text(stringResource(R.string.next_up_snoozed, it), style = DurtTheme.type.bodySmallStrong, color = colors.warning)
        }
    }
}
