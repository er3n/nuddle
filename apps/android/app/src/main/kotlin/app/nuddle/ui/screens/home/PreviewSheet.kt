package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.nuddle.api.model.Reachability
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.GroupDivider
import app.nuddle.ui.components.InlineAlert
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
    val onRecipient: (RecipientChoice) -> Unit = {},
    /** A tap on the Kime row when the text gave the recipient (NUD-108). */
    val onChangeRecipient: () -> Unit = {},
    val onPhoto: (Boolean) -> Unit = {},
    val onTimePicked: (LocalTime) -> Unit = {},
    /** A tap on the known time: a moment ("19:48") or a deadline ("Son zaman 19:48"), open-questions#43. */
    val onToggleMoment: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * 2b and 4a: a bottom sheet with what was understood and the question: the deadline, or "Kime?". For someone else it
 * also offers how the task ends and says how far pokes reach them.
 */
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
internal fun PreviewUi.confirmAction(callbacks: PreviewCallbacks) =
    SheetAction(
        text = stringResource(if (toOther) R.string.preview_send else R.string.preview_confirm),
        onClick = callbacks.onConfirm,
        enabled = ready,
        loading = sending,
    )

@Composable
internal fun ColumnScope.PreviewBody(
    preview: PreviewUi,
    callbacks: PreviewCallbacks,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    DraftRows(preview, callbacks)
    if (preview.recipients.isNotEmpty()) RecipientQuestion(preview, callbacks)
    if (preview.knownDeadline == null) DeadlineQuestion(preview, callbacks)
    if (preview.toOther) {
        if (!preview.notice) FinishChoice(preview, callbacks)
        preview.reach?.let { ReachNote(preview.to.orEmpty(), it) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
            Icon(DurtIcons.Bell, contentDescription = null, tint = colors.poke, modifier = Modifier.size(dimens.icon))
            Text(
                text = stringResource(R.string.preview_quiet_hint, preview.quietStart, preview.quietEnd),
                style = DurtTheme.type.body,
                color = colors.textSecondary,
            )
        }
    }
}

/** The highlighted box around the one question (2b). */
@Composable
private fun QuestionBox(
    question: String,
    content: @Composable () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.highlightSoft, RoundedCornerShape(dimens.radiusM))
                .border(dimens.borderCard, colors.highlightBorder, RoundedCornerShape(dimens.radiusM))
                .padding(dimens.space16),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Text(question, style = DurtTheme.type.bodyStrong, color = colors.ink)
        content()
    }
}

/** "Kime?": Kendine and each connection (4a). */
@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun RecipientQuestion(
    preview: PreviewUi,
    callbacks: PreviewCallbacks,
) {
    val dimens = DurtTheme.dimens
    QuestionBox(stringResource(R.string.preview_question_recipient)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.space8), verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
            preview.recipients.forEach { choice ->
                val (text, tag) =
                    when (choice) {
                        RecipientChoice.Self -> stringResource(R.string.home_to_self) to TestTags.previewRecipient(null)
                        is RecipientChoice.Person -> choice.name to TestTags.previewRecipient(choice.personId)
                    }
                ChoiceChip(
                    text,
                    selected = preview.recipient == choice,
                    onClick = { callbacks.onRecipient(choice) },
                    modifier = Modifier.testTag(tag),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun DeadlineQuestion(
    preview: PreviewUi,
    callbacks: PreviewCallbacks,
) {
    val dimens = DurtTheme.dimens
    var pickingTime by remember { mutableStateOf(false) }
    QuestionBox(stringResource(R.string.preview_question_deadline)) {
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

/** "Nasıl bitsin?" with "Para işlerinde fotoğraf öneririm" when the extraction chose a photo (4a). */
@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping; no stable alternative.
@Composable
private fun ColumnScope.FinishChoice(
    preview: PreviewUi,
    callbacks: PreviewCallbacks,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    // Wraps at large font scales instead of squeezing the question to nothing.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.preview_finish_question), style = DurtTheme.type.body, color = colors.textSecondary)
        if (preview.photoSuggested) {
            Text(stringResource(R.string.preview_photo_suggested), style = DurtTheme.type.captionStrong, color = colors.warning)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
        ChoiceChip(
            stringResource(R.string.task_finish_done),
            selected = !preview.photo,
            onClick = { callbacks.onPhoto(false) },
            modifier = Modifier.weight(1f).testTag(TestTags.PREVIEW_FINISH_DONE),
        )
        ChoiceChip(
            stringResource(R.string.task_finish_photo),
            selected = preview.photo,
            onClick = { callbacks.onPhoto(true) },
            modifier = Modifier.weight(1f).testTag(TestTags.PREVIEW_FINISH_PHOTO),
        )
    }
}

/**
 * "Şu an Eren için en güçlü dürtme: sesli bildirim." and, below full screen, that I will ask them for more (4a,
 * open-questions#34). Nothing is said when full screen already reaches them.
 */
@Composable
private fun ReachNote(
    name: String,
    reach: Reachability,
) {
    val level =
        when (reach) {
            Reachability.FULL_SCREEN -> return
            Reachability.SOUND -> stringResource(R.string.reach_sound)
            Reachability.SILENT -> stringResource(R.string.reach_silent)
            Reachability.NONE -> null
        }
    InlineAlert(
        title = level?.let { stringResource(R.string.preview_reach, name, it) } ?: stringResource(R.string.preview_reach_none, name),
        body = stringResource(R.string.preview_reach_ask),
        icon = DurtIcons.Bell,
    )
}

/** "Saat seç": the system time picker in a dialog (24 hour clock). */
@OptIn(ExperimentalMaterial3Api::class) // Reason: TimePicker is still experimental in Material 3.
@Composable
internal fun TimePickerDialog(
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

@PreviewScreens
@Composable
private fun OtherPreviewSheetPreview() {
    val preview =
        PreviewUi(
            rawText = "eren yarın 9dan önce temizlikçi için 1000 çeksin",
            title = "Temizlikçi için para çek",
            detail = "1.000 TL",
            choices = emptyList(),
            selected = null,
            quietStart = "23:00",
            quietEnd = "07:00",
            to = "Eren",
            recipient = RecipientChoice.Person("eren", "Eren"),
            knownDeadline = DeadlineText.Tomorrow("09:00"),
            photo = true,
            photoSuggested = true,
            reach = Reachability.SOUND,
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
