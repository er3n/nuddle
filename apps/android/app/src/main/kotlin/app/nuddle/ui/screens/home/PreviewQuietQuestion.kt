package app.nuddle.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.theme.DurtTheme

/** A moment inside quiet hours (NUD-120): the end of quiet hours or the asked time, neither picked at first. */
@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
internal fun QuietQuestion(
    quiet: QuietUi,
    to: String?,
    callbacks: PreviewCallbacks,
) {
    val dimens = DurtTheme.dimens
    val question =
        if (to == null) {
            stringResource(R.string.preview_quiet_self, quiet.start, quiet.end)
        } else {
            stringResource(R.string.preview_quiet_other, to, quiet.start, quiet.end)
        }
    QuestionBox(question) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.space8), verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
            ChoiceChip(
                stringResource(R.string.preview_quiet_move, quiet.suggestedTime),
                selected = quiet.keep == false,
                onClick = { callbacks.onQuiet(false) },
                modifier = Modifier.testTag(TestTags.PREVIEW_QUIET_MOVE),
            )
            ChoiceChip(
                stringResource(R.string.preview_quiet_keep, quiet.askedTime),
                selected = quiet.keep == true,
                onClick = { callbacks.onQuiet(true) },
                modifier = Modifier.testTag(TestTags.PREVIEW_QUIET_KEEP),
            )
        }
        Text(stringResource(R.string.preview_quiet_keep_note), style = DurtTheme.type.body, color = DurtTheme.colors.textSecondary)
    }
}
