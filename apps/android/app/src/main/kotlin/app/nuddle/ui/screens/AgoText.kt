package app.nuddle.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.domain.Ago

/** "şimdi", "5 dk önce", "2 sa önce", "3 gün önce" (1c, 1d). */
@Composable
fun agoText(ago: Ago): String =
    when (ago) {
        Ago.Now -> stringResource(R.string.ago_now)
        is Ago.Minutes -> pluralStringResource(R.plurals.ago_minutes, ago.value, ago.value)
        is Ago.Hours -> pluralStringResource(R.plurals.ago_hours, ago.value, ago.value)
        is Ago.Days -> pluralStringResource(R.plurals.ago_days, ago.value, ago.value)
    }
