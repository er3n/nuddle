package app.nuddle.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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

/** 4h on the compose bar: hold the mic, lift to send, slide left to cancel, a tap shows the hint. */
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
                    onVoiceEnd = { did += if (it) "cancel" else "send" },
                    notice = notice,
                )
            }
        }
    }

    @Test
    fun `holding the mic listens, lifting the finger sends`() {
        bar()
        compose.onNodeWithTag(TestTags.COMPOSE_MIC).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(
                androidx.compose.ui.geometry
                    .Offset(-10f, 0f),
            )
            up()
        }
        assertEquals(listOf("start", "send"), did)
    }

    @Test
    fun `sliding left while holding cancels`() {
        bar()
        compose.onNodeWithTag(TestTags.COMPOSE_MIC).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(
                androidx.compose.ui.geometry
                    .Offset(-width * 4f, 0f),
            )
            up()
        }
        assertEquals(listOf("start", "cancel"), did)
    }

    @Test
    fun `a short tap only shows Basılı tut`() {
        bar()
        compose.onNodeWithTag(TestTags.COMPOSE_MIC).performTouchInput {
            down(center)
            up()
        }
        assertEquals(emptyList(), did)
        compose.onNodeWithText("Basılı tut").assertIsDisplayed()
    }

    @Test
    fun `while listening the panel shows what was heard and how to cancel`() {
        bar(ComposeState.Listening("eren yarın akşam"))
        compose.onNodeWithText("Dinliyorum").assertIsDisplayed()
        compose.onNodeWithText("eren yarın akşam").assertIsDisplayed()
        compose.onNodeWithText("Vazgeçmek için parmağını sola kaydır").assertIsDisplayed()
    }

    @Test
    fun `a voice note shows by the mic`() {
        bar(notice = VoiceNotice.NoPermission)
        compose.onNodeWithText("Mikrofon izni olmadan dinleyemem. Yazarak da olur.").assertIsDisplayed()
    }
}
