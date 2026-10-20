package app.nuddle.ui.screens.announcement

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
import app.nuddle.domain.Ago
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.MarkdownBody
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.agoText
import app.nuddle.ui.theme.DurtTheme

/** An announcement's call to action: an app route (`invite`, `profile`, `inbox`) or an https URL. */
@Immutable
data class AnnouncementButtonUi(
    val label: String,
    val target: String,
)

@Immutable
sealed interface AnnouncementUiState {
    data object Loading : AnnouncementUiState

    /** Not on this phone (yet): a link or notification ahead of its sync. */
    data object Missing : AnnouncementUiState

    data class Ready(
        val title: String,
        /** CommonMark. */
        val body: String,
        val ago: Ago,
        val button: AnnouncementButtonUi?,
        /** Root-relative pictures load from here (the API host). */
        val imageBase: String,
    ) : AnnouncementUiState
}

/** `announcement/{id}` (screens.md, ADR 0025): a message from Nuddle with its markdown body and an optional button. */
@Composable
fun AnnouncementScreen(
    state: AnnouncementUiState,
    onBack: () -> Unit,
    onButton: (target: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val ready = state as? AnnouncementUiState.Ready
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.ANNOUNCEMENT),
        bottom = {
            ready?.button?.let { button ->
                DurtButton(
                    text = button.label,
                    onClick = { onButton(button.target) },
                    modifier = Modifier.testTag(TestTags.ANNOUNCEMENT_BUTTON),
                )
            }
        },
    ) {
        BackBar(title = stringResource(R.string.announcement_from), onBack = onBack)
        when (state) {
            // Room answers within a frame: nothing to show meanwhile.
            AnnouncementUiState.Loading -> {}

            AnnouncementUiState.Missing -> {
                Text(stringResource(R.string.announcement_missing), style = DurtTheme.type.body, color = colors.textSecondary)
            }

            is AnnouncementUiState.Ready -> {
                Text(state.title, style = DurtTheme.type.h1, color = colors.ink, modifier = Modifier.semantics { heading() })
                Text(agoText(state.ago), style = DurtTheme.type.caption, color = colors.textMuted)
                MarkdownBody(state.body, state.imageBase)
            }
        }
    }
}

@PreviewScreens
@Composable
private fun AnnouncementScreenPreview() {
    DurtTheme {
        AnnouncementScreen(
            AnnouncementUiState.Ready(
                title = "Premium geldi",
                body =
                    """
                    Artık dürtme **SMS** ve *sesli arama* ile de gelebilir.

                    ## Neler var?

                    - Telefon kapalıyken SMS
                    - Hiç cevap yoksa arama

                    ![Premium](/announcements/premium/hero.png)

                    Ayrıntılar [nuddle.app](https://nuddle.app) adresinde.
                    """.trimIndent(),
                ago = Ago.Hours(2),
                button = AnnouncementButtonUi("Premium'a bak", "https://nuddle.app/premium"),
                imageBase = "https://nuddle.app/",
            ),
            onBack = {},
            onButton = {},
        )
    }
}

@PreviewScreens
@Composable
private fun AnnouncementScreenMissingPreview() {
    DurtTheme { AnnouncementScreen(AnnouncementUiState.Missing, onBack = {}, onButton = {}) }
}
