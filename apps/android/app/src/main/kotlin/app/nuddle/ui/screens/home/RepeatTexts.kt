package app.nuddle.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.api.model.RepeatRule
import app.nuddle.data.db.TaskEntity

/** A repeating task's rule as the screens show it (ADR 0021): "Her gün 09:00", "Her salı 19:00", "Hafta içi 07:30". */
@Immutable
data class RepeatUi(
    /** "day", "week" or "weekdays". */
    val every: String,
    /** ISO weekdays 1 to 7, only for "week". */
    val days: List<Int>,
    /** "HH:MM". */
    val time: String,
)

/** The rule of [TaskEntity], or null for a one-off. */
val TaskEntity.repeat: RepeatUi?
    get() {
        val every = repeatEvery ?: return null
        val time = repeatTime ?: return null
        val days = repeatDays.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
        return RepeatUi(every, days, time)
    }

/** Back to the API's shape for `create_task` (the preview's Kur). */
fun RepeatUi.toRule(): RepeatRule =
    RepeatRule(
        every = RepeatRule.Every.entries.first { it.value == every },
        time = time,
        days = days.takeIf { every == "week" },
    )

@Composable
fun RepeatUi.text(): String =
    when (every) {
        "weekdays" -> {
            stringResource(R.string.repeat_weekdays, time)
        }

        "week" -> {
            val names = stringArrayResource(R.array.weekday_names)
            val dayNames = days.filter { it in 1..names.size }.joinToString(", ") { names[it - 1] }
            stringResource(R.string.repeat_weekly, dayNames, time)
        }

        else -> {
            stringResource(R.string.repeat_daily, time)
        }
    }
