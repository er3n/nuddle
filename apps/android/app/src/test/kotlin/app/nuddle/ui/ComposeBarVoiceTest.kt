package app.nuddle.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.nuddle.ui.components.ComposeBar
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.components.VoiceNotice
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The compose bar while sending (NUD-54), and 4h (NUD-48): a tap on the mic dictates, the transcript shows in
 * the box, a tap on stop ends it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ComposeBarVoiceTest {
    @get:Rule
    val compose = createComposeRule()

    private val did = mutableListOf<String>()

    private fun bar(
        state: ComposeState = ComposeState.Idle,
        notice: VoiceNotice? = null,
    ) {
        compose.setContent {
            DurtTheme {
                ComposeBar(
                    state,
                    {},
                    {},
                    onVoiceStart = { did += "start" },
                    onVoiceEnd = { did += if (it) "cancel" else "stop" },
                    notice = notice,
                )
            }
        }
    }

    @Test
    fun `while sending the text stays in the box and cannot be edited (NUD-54)`() {
        bar(ComposeState.Sending("merhaba"))
        compose.onNodeWithText("merhaba").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `a tap on the mic starts listening`() {
        bar()
        compose.onNodeWithTag(TestTags.COMPOSE_MIC).performClick()
        assertEquals(listOf("start"), did)
    }

    @Test
    fun `while listening the box shows what was heard and a tap on stop ends it`() {
        bar(ComposeState.Listening("eren yarın akşam"))
        compose.onNodeWithText("Dinliyorum").assertIsDisplayed()
        compose.onNodeWithText("eren yarın akşam").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.COMPOSE_MIC_STOP).performClick()
        assertEquals(listOf("stop"), did)
    }

    @Test
    fun `a voice note shows by the mic`() {
        bar(notice = VoiceNotice.NoPermission)
        compose.onNodeWithText("Mikrofon izni olmadan dinleyemem. Yazarak da olur.").assertIsDisplayed()
    }
}
