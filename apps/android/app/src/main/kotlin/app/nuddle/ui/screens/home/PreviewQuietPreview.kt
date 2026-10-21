package app.nuddle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.api.model.Reachability
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme
import kotlin.time.Instant

private fun quiet(
    end: String,
    keep: Boolean?,
) = QuietUi(
    start = "23:00",
    end = end,
    asked = Instant.parse("2026-10-21T05:00:00+03:00"),
    suggested = Instant.parse("2026-10-21T$end:00+03:00"),
    askedTime = "05:00",
    suggestedTime = end,
    keep = keep,
)

@Composable
private fun QuietSheet(preview: PreviewUi) {
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

private val wakeUp =
    PreviewUi(
        rawText = "Yarın sabah beni 5.00'te uyandır",
        title = "Uyan",
        detail = null,
        choices = emptyList(),
        selected = null,
        quietStart = "23:00",
        quietEnd = "06:00",
        knownDeadline = DeadlineText.Tomorrow("05:00", moment = true),
        missAfterMinutes = 15,
        quiet = quiet("06:00", keep = null),
    )

/** A 05:00 wake-up inside quiet hours (NUD-120): nothing picked yet, so Kur waits. */
@PreviewScreens
@Composable
private fun QuietPreviewSheetPreview() {
    QuietSheet(wakeUp)
}

/** "06:00 olsun" picked: the time row follows. */
@PreviewScreens
@Composable
private fun QuietMovedPreviewSheetPreview() {
    QuietSheet(wakeUp.copy(knownDeadline = DeadlineText.Tomorrow("06:00", moment = true), quiet = quiet("06:00", keep = false)))
}

/** For someone else their quiet hours count, by name. */
@PreviewScreens
@Composable
private fun QuietOtherPreviewSheetPreview() {
    QuietSheet(
        wakeUp.copy(
            rawText = "sibel yarın sabah 5te kalksın",
            title = "Kalk",
            to = "Sibel",
            recipient = RecipientChoice.Person("sibel", "Sibel"),
            reach = Reachability.FULL_SCREEN,
            quiet = quiet("07:00", keep = true),
        ),
    )
}
