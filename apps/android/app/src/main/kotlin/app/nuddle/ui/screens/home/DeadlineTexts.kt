package app.nuddle.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

// How a task's time reads on screen: as a deadline ("Son zaman yarın 09:00") or, for an `at` task, as its moment
// ("Zaman", "yarın 09:00"); ADR 0017, open-questions#43.

@Composable
internal fun DeadlineText.text(): String =
    when (this) {
        is DeadlineText.Today -> stringResource(R.string.deadline_today, time)
        is DeadlineText.Tomorrow -> stringResource(R.string.deadline_tomorrow, time)
        is DeadlineText.OnDate -> stringResource(R.string.deadline_on_date, day, stringArrayResource(R.array.month_names)[month - 1], time)
    }

/** "son zaman yarın 09:00", or just "yarın 09:00" for the moment of an `at` task (open-questions#43). */
@Composable
internal fun DeadlineText.phrase(): String = if (moment) inlineText() else stringResource(R.string.home_deadline_inline, inlineText())

/** The same at the start of a line: "Son zaman yarın 09:00", or "Yarın 09:00" for an `at` task. */
@Composable
internal fun DeadlineText.startPhrase(): String = if (moment) text() else stringResource(R.string.issue_sent_deadline, inlineText())

/** The label of a time row: "Son zaman", or "Zaman" for an `at` task. */
@Composable
internal fun DeadlineText.timeLabel(): String = stringResource(if (moment) R.string.home_time_label else R.string.home_deadline_label)

/** The deadline inside a sentence: "yarın 09:00". */
@Composable
internal fun DeadlineText.inlineText(): String =
    when (this) {
        is DeadlineText.Today -> stringResource(R.string.deadline_today_inline, time)
        is DeadlineText.Tomorrow -> stringResource(R.string.deadline_tomorrow_inline, time)
        is DeadlineText.OnDate -> stringResource(R.string.deadline_on_date, day, stringArrayResource(R.array.month_names)[month - 1], time)
    }

/** 2f for an `at` task: "Zaman · Bugün 19:48" (open-questions#43). */
@PreviewScreens
@Composable
private fun HomeScreenMomentPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Sibel",
                    nextUp = NextUpUi("task-1", "Su iç", null, DeadlineText.Today("19:48", moment = true), photo = false),
                ),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}
