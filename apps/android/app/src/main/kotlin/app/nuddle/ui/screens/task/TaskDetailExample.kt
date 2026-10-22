package app.nuddle.ui.screens.task

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.theme.DurtTheme

// The task detail with the sender's example photo (NUD-138, docs/ux/screens.md, Task detail).

private const val PREVIEW_W = 120
private const val PREVIEW_H = 160

/** Eren's side: Sibel's picture under the title, "Sibel ekledi". */
@PreviewScreens
@Composable
private fun TaskDetailExamplePhotoPreview() {
    DurtTheme {
        TaskDetailScreen(
            previewState.copy(
                title = "Markete git, fotodakileri al",
                rawText = null,
                detail = null,
                photo = false,
                side = DetailSide.ToMe,
                from = FromUi("Sibel", WrittenText.Today("14:32"), phone = "+905551112233"),
                examplePhoto = ImageBitmap(PREVIEW_W, PREVIEW_H),
            ),
            TaskDetailCallbacks(),
        )
    }
}
