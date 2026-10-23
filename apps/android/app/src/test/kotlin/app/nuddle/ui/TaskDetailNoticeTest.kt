package app.nuddle.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.nuddle.api.model.Reachability
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.task.TaskDetailCallbacks
import app.nuddle.ui.screens.task.TaskDetailScreen
import app.nuddle.ui.screens.task.TaskDetailUiState
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/** 7c for a one-time message (NUD-140): it rings once with sound, so the sender never reads "en güçlü dürtme: tam ekran". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TaskDetailNoticeTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent =
        TaskDetailUiState(
            myName = "Sibel",
            title = "Yemek hazır",
            rawText = "erene yemek hazır de",
            toName = "Eren",
            reachability = Reachability.FULL_SCREEN,
            deadline = DeadlineText.Today("20:00"),
            detail = null,
            photo = false,
        )

    private fun show(state: TaskDetailUiState) {
        compose.setContent { DurtTheme { TaskDetailScreen(state, TaskDetailCallbacks()) } }
    }

    @Test
    fun `the sender of a message reads that it rings once with sound`() {
        show(sent.copy(notice = true))
        compose.onNodeWithText("Bu mesajı bir kez, sesli bildirimle iletirim.").assertIsDisplayed()
        compose.onNodeWithText("en güçlü dürtme", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the sender of a task still reads the strongest poke`() {
        show(sent)
        compose.onNodeWithText("Şu an Eren için en güçlü dürtme: tam ekran. Bitince sana yazarım.").assertIsDisplayed()
    }

    @Test
    fun `a message to a phone that cannot be poked says so`() {
        show(sent.copy(notice = true, reachability = Reachability.NONE))
        compose.onNodeWithText("Şu an Eren telefonunda dürtülemiyor. Bitince sana yazarım.").assertIsDisplayed()
    }
}
