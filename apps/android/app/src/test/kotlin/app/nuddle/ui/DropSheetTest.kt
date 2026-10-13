package app.nuddle.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.nuddle.ui.screens.poke.DropCallbacks
import app.nuddle.ui.screens.poke.DropReason
import app.nuddle.ui.screens.poke.DropSheet
import app.nuddle.ui.screens.poke.DropUi
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** 7f: the note is optional, a chip or a written one, and the written one wins (open-questions#36). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class DropSheetTest {
    @get:Rule
    val compose = createComposeRule()

    /** What each drop sent: the note, or null for "Notsuz kapat". */
    private val dropped = mutableListOf<String?>()

    private fun showSheet() {
        compose.setContent {
            var state by remember { mutableStateOf(DropUi(senderName = "Sibel")) }
            DurtTheme {
                DropSheet(
                    state,
                    DropCallbacks(
                        onReason = { state = state.copy(reason = it.takeIf { r -> r != state.reason }) },
                        onNoteChange = { state = state.copy(note = it) },
                        onDrop = { dropped += it },
                    ),
                )
            }
        }
    }

    @Test
    fun `Notla gönder waits for a note, then sends the chosen chip`() {
        showSheet()
        compose.onNodeWithTag(TestTags.SHEET_PRIMARY).assertIsNotEnabled()

        compose.onNodeWithTag(TestTags.dropReason(DropReason.Tomorrow.name)).performClick()
        compose.onNodeWithTag(TestTags.SHEET_PRIMARY).assertIsEnabled().performClick()

        assertEquals(listOf<String?>("Yarın hallederim"), dropped)
    }

    @Test
    fun `a written note wins over the chip`() {
        showSheet()
        compose.onNodeWithTag(TestTags.dropReason(DropReason.NoTime.name)).performClick()
        compose.onNodeWithTag(TestTags.DROP_NOTE).performTextInput("  Eve geç kaldım ")
        compose.onNodeWithTag(TestTags.SHEET_PRIMARY).performClick()

        assertEquals(listOf<String?>("Eve geç kaldım"), dropped)
    }

    @Test
    fun `Notsuz kapat drops without a note even with a chip chosen`() {
        showSheet()
        compose.onNodeWithTag(TestTags.dropReason(DropReason.NoTime.name)).performClick()
        compose.onNodeWithTag(TestTags.SHEET_DISMISS).performClick()

        assertEquals(listOf<String?>(null), dropped)
    }
}
