package app.nuddle.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * The 2b deadline picks (open-questions#28): "Bugün 19:00" while it is ahead, "Yarın 09:00", and "Saat seç" with
 * the time picker. Shared by the 7c row edit and the new deadline of 5d.
 */
@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
fun DeadlineChips(
    choices: List<DeadlineChoice>,
    picked: DeadlineChoice?,
    onChoice: (DeadlineChoice) -> Unit,
    onPickTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = DurtTheme.dimens
    var pickingTime by remember { mutableStateOf(false) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.space8),
        verticalArrangement = Arrangement.spacedBy(dimens.space8),
    ) {
        choices.forEach { choice ->
            val (text, tag) =
                when (choice) {
                    is DeadlineChoice.TonightEvening -> stringResource(R.string.preview_tonight) to TestTags.PREVIEW_EVENING
                    is DeadlineChoice.TomorrowMorning -> stringResource(R.string.preview_tomorrow_morning) to TestTags.PREVIEW_MORNING
                    is DeadlineChoice.Picked -> choice.time to TestTags.PREVIEW_PICK_TIME
                }
            ChoiceChip(text, selected = picked == choice, onClick = { onChoice(choice) }, modifier = Modifier.testTag(tag))
        }
        val pickedTime = picked as? DeadlineChoice.Picked
        ChoiceChip(
            text = pickedTime?.time ?: stringResource(R.string.preview_pick_time),
            selected = pickedTime != null,
            onClick = { pickingTime = true },
            modifier = Modifier.testTag(TestTags.PREVIEW_PICK_TIME),
        )
    }
    if (pickingTime) {
        TimePickerDialog(
            onPick = {
                pickingTime = false
                onPickTime(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@PreviewComponent
@Composable
private fun DeadlineChipsPreview() {
    val morning = DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00"))
    PreviewSurface { DeadlineChips(listOf(morning), picked = morning, onChoice = {}, onPickTime = {}) }
}
