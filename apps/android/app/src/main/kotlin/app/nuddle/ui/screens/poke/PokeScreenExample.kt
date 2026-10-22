package app.nuddle.ui.screens.poke

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.nuddle.domain.TimeLeft
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

private const val PREVIEW_PHOTO_W = 120
private const val PREVIEW_PHOTO_H = 160

/** "Markete git, fotodakileri al" from Sibel (NUD-138, screens.md 3b): her picture under the task. */
@PreviewScreens
@Composable
private fun PokeScreenExamplePhotoPreview() {
    DurtTheme {
        PokeScreen(
            state =
                PokeUiState(
                    pokeNumber = 3,
                    time = "18:15",
                    title = "Markete git, fotodakileri al",
                    detail = null,
                    timeLeft = TimeLeft.Minutes(45),
                    pokeTitle = "Pes etmem",
                    examplePhoto = ImageBitmap(PREVIEW_PHOTO_W, PREVIEW_PHOTO_H),
                ),
            onDone = {},
            onSnooze = {},
            onCantToday = {},
        )
    }
}
