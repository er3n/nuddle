package app.nuddle.ui.screens.task

import androidx.compose.runtime.Composable
import app.nuddle.data.Delivery
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

/** My task to Eren after two pokes rang: the past-tense line under the sentence (NUD-81). */
@PreviewScreens
@Composable
private fun TaskDetailScreenPokesPreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(delivery = Delivery.Seen, pokesSoFar = PokesSoFar(2, "09:10")),
            TaskDetailCallbacks(),
        )
    }
}
