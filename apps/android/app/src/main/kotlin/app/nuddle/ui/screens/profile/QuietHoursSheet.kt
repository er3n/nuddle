package app.nuddle.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.BigTextField
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime

/** 7g: Başlangıç and Bitiş; the selected box is set with the time input under them (open-questions#37). */
@OptIn(ExperimentalMaterial3Api::class) // Reason: the Material 3 time input is still experimental.
@Composable
internal fun ColumnScope.QuietPicker(
    sheet: ProfileSheet.Quiet,
    callbacks: ProfileSheetCallbacks,
) {
    val dimens = DurtTheme.dimens
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(dimens.space10)) {
        QuietBox(
            stringResource(R.string.quiet_start),
            sheet.start,
            sheet.editing == QuietEdge.START,
            TestTags.QUIET_START,
            onSelect = { callbacks.onQuietEdge(QuietEdge.START) },
            modifier = Modifier.weight(1f),
        )
        QuietBox(
            stringResource(R.string.quiet_end),
            sheet.end,
            sheet.editing == QuietEdge.END,
            TestTags.QUIET_END,
            onSelect = { callbacks.onQuietEdge(QuietEdge.END) },
            modifier = Modifier.weight(1f),
        )
    }
    val time = if (sheet.editing == QuietEdge.START) sheet.start else sheet.end
    // A new input for each box, starting at its time; what is typed goes back to the box.
    key(sheet.editing) {
        val input = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        LaunchedEffect(input.hour, input.minute) {
            val picked = LocalTime(input.hour, input.minute)
            if (picked != time) callbacks.onQuietTime(picked)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val colors = DurtTheme.colors
            TimeInput(
                state = input,
                colors =
                    TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor = colors.surfaceMuted,
                        timeSelectorUnselectedContainerColor = colors.surfaceMuted,
                        timeSelectorSelectedContentColor = colors.ink,
                        timeSelectorUnselectedContentColor = colors.ink,
                    ),
            )
        }
    }
    Text(stringResource(R.string.quiet_hint), style = DurtTheme.type.caption, color = DurtTheme.colors.textSecondary)
    Failed(sheet)
}

@Composable
private fun QuietBox(
    label: String,
    time: LocalTime,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .border(if (selected) dimens.borderFocused else dimens.borderCard, if (selected) colors.ink else colors.border, shape)
                .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
                .padding(vertical = dimens.space14)
                .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = DurtTheme.type.caption, color = colors.textSecondary)
        Text(time.hhmm(), style = DurtTheme.type.codeDigit, color = colors.ink, textAlign = TextAlign.Center)
    }
}

@PreviewScreens
@Composable
private fun QuietHoursSheetPreview() {
    ProfileSheetPreviewOf(ProfileSheet.Quiet(LocalTime(23, 0), LocalTime(7, 0)))
}

@PreviewScreens
@Composable
private fun QuietHoursSheetEndFailedPreview() {
    ProfileSheetPreviewOf(ProfileSheet.Quiet(LocalTime(22, 30), LocalTime(6, 30), editing = QuietEdge.END, failed = true))
}
