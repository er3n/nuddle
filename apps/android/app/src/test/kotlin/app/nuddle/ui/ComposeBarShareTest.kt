package app.nuddle.ui

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.nuddle.data.SharedText
import app.nuddle.ui.components.ComposeBar
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.nav.SharedTextEffect
import app.nuddle.ui.screens.home.typedText
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Share to Nuddle (NUD-127): the shared text waits in home's compose box, after what was typed, until the arrow. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ComposeBarShareTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a shared text lands in the box after what was typed and goes out only with the arrow`() {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Veli toplantısı")
                .putExtra(Intent.EXTRA_TEXT, "Cuma 18:00, 3B sınıfı")
        var shared by mutableStateOf(SharedText.of(intent))
        var sends = 0
        compose.setContent {
            var bar by remember { mutableStateOf<ComposeState>(ComposeState.Typing("sibel")) }
            SharedTextEffect(shared, bar.typedText(), onTake = { shared = null }, onText = { bar = ComposeState.Typing(it) })
            DurtTheme { ComposeBar(bar, { bar = ComposeState.Typing(it) }, { sends++ }, onVoiceStart = {}, onVoiceEnd = {}) }
        }

        compose
            .onNodeWithTag(TestTags.COMPOSE_INPUT)
            .assertIsDisplayed()
            .assertTextEquals("sibel Veli toplantısı\nCuma 18:00, 3B sınıfı")
        assertEquals(null, shared)
        assertEquals(0, sends)
        compose.onNodeWithTag(TestTags.COMPOSE_SEND).performClick()
        assertEquals(1, sends)
    }
}
