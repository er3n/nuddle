package app.nuddle.ui.screens.task

import androidx.compose.runtime.Composable
import app.nuddle.data.Delivery
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.theme.DurtTheme

// The detail's states added in NUD-68: when poking starts, and the sender's tick.

/** NUD-68: Eren's side before the first poke, "Saat 09:00 olunca hatırlatırım". */
@PreviewScreens
@Composable
private fun TaskDetailScreenPokeStartPreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(
                myName = "Eren",
                toName = "",
                side = DetailSide.ToMe,
                from = FromUi("Sibel", WrittenText.Today("08:10"), "+905320000001"),
                pokeStartsAt = "09:00",
            ),
            TaskDetailCallbacks(),
        )
    }
}

/** NUD-68: Sibel's side once Eren has seen the task, "✓✓ Gördü." */
@PreviewScreens
@Composable
private fun TaskDetailScreenSeenPreview() {
    DurtTheme { TaskDetailScreen(previewState.copy(delivery = Delivery.Seen), TaskDetailCallbacks()) }
}
