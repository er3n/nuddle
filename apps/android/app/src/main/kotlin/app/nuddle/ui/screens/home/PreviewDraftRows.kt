package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.LineRow
import app.nuddle.ui.theme.DurtTheme

/** The card of what was understood: Kime, the time, the repeat rule (ADR 0021) and the detail, with dividers between. */
@Composable
internal fun DraftRows(
    preview: PreviewUi,
    callbacks: PreviewCallbacks,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val rows: List<@Composable () -> Unit> =
        listOfNotNull(
            if (preview.recipients.isEmpty()) {
                { LineRow(label = stringResource(R.string.preview_to), value = preview.to ?: stringResource(R.string.home_to_self)) }
            } else {
                null
            },
            preview.knownDeadline?.let { known ->
                {
                    // A moment with a lead says when the pokes begin (NUD-82).
                    val value =
                        preview.firstPokeAt
                            ?.let { stringResource(R.string.separator_dot, known.text(), stringResource(R.string.preview_first_poke, it)) }
                            ?: known.text()
                    LineRow(label = known.timeLabel(), value = value, onClick = callbacks.onToggleMoment)
                }
            },
            preview.repeat?.let { rule -> { LineRow(label = stringResource(R.string.task_repeat_label), value = rule.text()) } },
            preview.detail?.let { detail -> { LineRow(label = stringResource(R.string.preview_detail), value = detail) } },
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(dimens.radiusM))
                .border(dimens.borderCard, colors.border, RoundedCornerShape(dimens.radiusM))
                .padding(horizontal = dimens.space16),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) GroupDivider()
            row()
        }
    }
}
