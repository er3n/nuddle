package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.LineRow
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

@Immutable
data class PreviewCallbacks(
    val onChoice: (DeadlineChoice) -> Unit = {},
    val onTimePicked: (LocalTime) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** 2b: a bottom sheet with what was understood and the one question, here the deadline. */
@Composable
internal fun PreviewSheet(
    preview: PreviewUi,
    callbacks: PreviewCallbacks,
) {
    DurtBottomSheet(
        title = preview.title,
        description = stringResource(R.string.preview_you_wrote, preview.rawText),
        onDismiss = callbacks.onDismiss,
        primary = preview.confirmAction(callbacks),
        modifier = Modifier.testTag(TestTags.PREVIEW),
    ) { PreviewBody(preview, callbacks) }
}

@Composable
private fun PreviewUi.confirmAction(callbacks: PreviewCallbacks) =
    SheetAction(
        text = stringResource(R.string.preview_confirm),
        onClick = callbacks.onConfirm,
        enabled = selected != null,
        loading = sending,
    )

@Composable
private fun ColumnScope.PreviewBody(
    preview: PreviewUi,
    callbacks: PreviewCallbacks,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    var pickingTime by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(dimens.radiusM))
                .border(dimens.borderCard, colors.border, RoundedCornerShape(dimens.radiusM))
                .padding(horizontal = dimens.space16),
    ) {
        LineRow(label = stringResource(R.string.preview_to), value = preview.to ?: stringResource(R.string.home_to_self))
        preview.detail?.let {
            GroupDivider()
            LineRow(label = stringResource(R.string.preview_detail), value = it)
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.highlightSoft, RoundedCornerShape(dimens.radiusM))
                .border(dimens.borderCard, colors.highlightBorder, RoundedCornerShape(dimens.radiusM))
                .padding(dimens.space16),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Text(stringResource(R.string.preview_question_deadline), style = DurtTheme.type.bodyStrong, color = colors.ink)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.space8),
            verticalArrangement = Arrangement.spacedBy(dimens.space8),
        ) {
            preview.choices.forEach { choice ->
                val (text, tag) =
                    when (choice) {
                        is DeadlineChoice.TonightEvening -> stringResource(R.string.preview_tonight) to TestTags.PREVIEW_EVENING
                        is DeadlineChoice.TomorrowMorning -> stringResource(R.string.preview_tomorrow_morning) to TestTags.PREVIEW_MORNING
                        is DeadlineChoice.Picked -> choice.time to TestTags.PREVIEW_PICK_TIME
                    }
                ChoiceChip(
                    text,
                    selected = preview.selected == choice,
                    onClick = { callbacks.onChoice(choice) },
                    modifier = Modifier.testTag(tag),
                )
            }
            val picked = preview.selected as? DeadlineChoice.Picked
            ChoiceChip(
                text = picked?.time ?: stringResource(R.string.preview_pick_time),
                selected = picked != null,
                onClick = { pickingTime = true },
                modifier = Modifier.testTag(TestTags.PREVIEW_PICK_TIME),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
        Icon(DurtIcons.Bell, contentDescription = null, tint = colors.poke, modifier = Modifier.size(dimens.icon))
        Text(
            text = stringResource(R.string.preview_quiet_hint, preview.quietStart, preview.quietEnd),
            style = DurtTheme.type.body,
            color = colors.textSecondary,
        )
    }
    if (pickingTime) {
        TimePickerDialog(
            onPick = {
                pickingTime = false
                callbacks.onTimePicked(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** "Saat seç": the system time picker in a dialog (24 hour clock). */
@OptIn(ExperimentalMaterial3Api::class) // Reason: TimePicker is still experimental in Material 3.
@Composable
private fun TimePickerDialog(
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DurtButton(
                text = stringResource(R.string.action_ok),
                onClick = { onPick(LocalTime(state.hour, state.minute)) },
                kind = DurtButtonKind.Ghost,
                size = DurtButtonSize.Small,
            )
        },
        dismissButton = {
            DurtButton(
                text = stringResource(R.string.action_dismiss),
                onClick = onDismiss,
                kind = DurtButtonKind.Ghost,
                size = DurtButtonSize.Small,
            )
        },
        text = { TimePicker(state = state) },
    )
}

@PreviewScreens
@Composable
private fun PreviewSheetPreview() {
    val preview =
        PreviewUi(
            rawText = "1000 tl çek",
            title = "Para çek",
            detail = "1.000 TL",
            choices =
                listOf(
                    DeadlineChoice.TonightEvening(Instant.parse("2026-10-11T19:00:00+03:00")),
                    DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00")),
                ),
            selected = null,
            quietStart = "23:00",
            quietEnd = "07:00",
        )
    DurtTheme {
        // The sheet itself is a ModalBottomSheet window; screenshots show its content over the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            DurtBottomSheetContent(
                title = preview.title,
                description = stringResource(R.string.preview_you_wrote, preview.rawText),
                onDismiss = {},
                primary = preview.confirmAction(PreviewCallbacks()),
            ) { PreviewBody(preview, PreviewCallbacks()) }
        }
    }
}
