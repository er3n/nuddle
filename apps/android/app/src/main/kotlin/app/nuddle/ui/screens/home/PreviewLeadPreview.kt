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

/** "Kime?" asked for a 17:30 pickup with a 20 minute lead: the time row says when the pokes begin (NUD-82). */
@PreviewScreens
@Composable
private fun LeadPreviewSheetPreview() {
    val preview =
        PreviewUi(
            rawText = "17.30da çocukları okuldan al",
            title = "Çocukları okuldan al",
            detail = null,
            choices = emptyList(),
            selected = null,
            quietStart = "23:00",
            quietEnd = "07:00",
            recipients = listOf(RecipientChoice.Self, RecipientChoice.Person("eren", "Eren")),
            recipient = null,
            knownDeadline = DeadlineText.Today("17:30", moment = true),
            missAfterMinutes = 15,
            firstPokeAt = "17:10",
            reach = Reachability.SOUND,
        )
    DurtTheme {
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

/** "beni ara" read as a task to self, after a tap on Kime: the "Kime?" choices with Kendine selected (NUD-108). */
@PreviewScreens
@Composable
private fun ChangeRecipientPreviewSheetPreview() {
    val preview =
        PreviewUi(
            rawText = "beni ara",
            title = "Beni ara",
            detail = null,
            choices = listOf(DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-18T09:00:00+03:00"))),
            selected = null,
            quietStart = "23:00",
            quietEnd = "07:00",
            recipients = listOf(RecipientChoice.Self, RecipientChoice.Person("eren", "Eren")),
            recipientOptions = listOf(RecipientChoice.Self, RecipientChoice.Person("eren", "Eren")),
        )
    DurtTheme {
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
