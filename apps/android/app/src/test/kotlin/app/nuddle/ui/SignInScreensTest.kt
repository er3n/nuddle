package app.nuddle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.nuddle.ui.components.CodeInput
import app.nuddle.ui.components.UndoToast
import app.nuddle.ui.screens.code.CodeScreen
import app.nuddle.ui.screens.code.CodeUiState
import app.nuddle.ui.screens.home.HomeScreen
import app.nuddle.ui.screens.home.HomeUiState
import app.nuddle.ui.screens.name.NameScreen
import app.nuddle.ui.screens.name.NameUiState
import app.nuddle.ui.screens.phone.PhoneScreen
import app.nuddle.ui.screens.phone.PhoneUiState
import app.nuddle.ui.screens.welcome.WelcomeScreen
import app.nuddle.ui.screens.welcome.WelcomeUiState
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Screens render their states and fire their callbacks (docs/testing.md, UI behavior). Accessibility checks (ATF:
 * touch targets, labels, contrast) run on every interaction.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class SignInScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun content(block: @Composable () -> Unit) {
        compose.setContent { DurtTheme(block) }
        compose.enableAccessibilityChecks()
    }

    @Test
    fun `welcome starts sign-in`() {
        var started = 0
        content { WelcomeScreen(WelcomeUiState(), onStart = { started++ }, onTerms = {}, onPrivacy = {}) }
        compose.onNodeWithText("Telefonla başla").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `phone send is enabled only for a full mobile number`() {
        content { PhoneScreen(PhoneUiState(national = "53200"), {}, {}, {}) }
        compose.onNodeWithText("Kodu SMS ile gönder").assertIsNotEnabled()
    }

    @Test
    fun `phone send fires for a valid number`() {
        var sent = 0
        content { PhoneScreen(PhoneUiState(national = "5320000002"), {}, onSend = { sent++ }, onBack = {}) }
        compose.onNodeWithText("Kodu SMS ile gönder").assertIsEnabled().performClick()
        assertEquals(1, sent)
    }

    @Test
    fun `the code completes on the sixth digit`() {
        var completed: String? = null
        compose.setContent {
            DurtTheme {
                var value by remember { mutableStateOf("") }
                CodeInput(value = value, error = false, onValueChange = { value = it }, onComplete = { completed = it })
            }
        }
        compose.onNodeWithContentDescription("6 haneli kod").performTextInput("12a3456")
        assertEquals("123456", completed)
    }

    @Test
    fun `the wrong code state says so and offers a new code`() {
        var resent = 0
        content {
            CodeScreen(
                CodeUiState("+90 532 000 00 02", code = "000000", wrongCode = true),
                {},
                {},
                { resent++ },
                {},
                {},
            )
        }
        compose.onNodeWithText("Kod tutmadı").assertExists()
        compose.onNodeWithText("Kod yanlış.").assertExists()
        compose.onNodeWithText("Yeni kod gönder").performClick()
        assertEquals(1, resent)
    }

    @Test
    fun `6c counts the codes left, then says when a new one can be asked for`() {
        var state by mutableStateOf(CodeUiState("+90 532 000 00 02", code = "000000", wrongCode = true, remainingAttempts = 2))
        content { CodeScreen(state, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Kod yanlış. 2 deneme hakkın kaldı.").assertExists()
        compose
            .onNodeWithText("Hakların biterse 15 dakika sonra yeni kod isteyebilirsin. Bu, numaranı kötüye kullanılmaktan korur.")
            .assertExists()

        state = state.copy(code = "", remainingAttempts = 0, lockedUntil = "21:15")
        compose.onNodeWithText("Çok fazla yanlış deneme.").assertExists()
        compose.onNodeWithText("Yeni kodu saat 21:15 olunca isteyebilirsin.").assertExists()
        compose.onNodeWithText("Yeni kod gönder").assertIsNotEnabled()
    }

    @Test
    fun `invited welcome shows who invites`() {
        content { WelcomeScreen(WelcomeUiState(inviterName = "Sibel"), onStart = {}, onTerms = {}, onPrivacy = {}) }
        compose.onNodeWithText("Sibel seni dürt'e davet etti").assertExists()
    }

    @Test
    fun `the invited name hint keeps the name the inviter typed while the name is edited`() {
        content { NameScreen(NameUiState(name = "Eren Can", invitedBy = "Sibel", invitedAs = "Eren"), {}, {}) }
        compose.onNodeWithText("Sibel seni \"Eren\" diye eklemişti. Değiştirebilirsin.").assertExists()
    }

    @Test
    fun `name continue needs a name`() {
        content { NameScreen(NameUiState(name = ""), {}, {}) }
        compose.onNodeWithText("Devam").assertIsNotEnabled()
    }

    @Test
    fun `home greets the person and offers an invite`() {
        var invites = 0
        content { HomeScreen(HomeUiState("Eren"), {}, {}, onInvite = { invites++ }, {}, {}, {}, {}) }
        compose.onNodeWithText("Merhaba Eren").assertExists()
        compose.onNodeWithText("Henüz görev yok").assertExists()
        compose.onNodeWithText("Birini davet et").performClick()
        assertEquals(1, invites)
    }

    @Test
    fun `the undo toast confirms when its time is up`() {
        var timedOut = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { DurtTheme { UndoToast("Eren'e gitti", "Para çek", {}, {}, onTimeout = { timedOut++ }) } }
        compose.mainClock.advanceTimeBy(4_900)
        assertEquals(0, timedOut)
        compose.mainClock.advanceTimeBy(200)
        assertEquals(1, timedOut)
    }
}
