package app.nuddle.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.nuddle.domain.TimeLeft
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.screens.code.CodeScreen
import app.nuddle.ui.screens.code.CodeUiState
import app.nuddle.ui.screens.home.HomeScreen
import app.nuddle.ui.screens.home.HomeUiState
import app.nuddle.ui.screens.name.NameScreen
import app.nuddle.ui.screens.name.NameUiState
import app.nuddle.ui.screens.phone.PhoneScreen
import app.nuddle.ui.screens.phone.PhoneUiState
import app.nuddle.ui.screens.poke.PokeScreen
import app.nuddle.ui.screens.poke.PokeUiState
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
 * Every interactive element carries its test tag (docs/testing.md, Android `e2e` flavor): the Maestro flows in
 * `e2e/flows` find elements only by these tags, so a lost tag breaks E2E without failing anything else.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class TestTagsTest {
    @get:Rule val compose = createComposeRule()

    private val clicked = mutableListOf<String>()

    private fun content(block: @Composable () -> Unit) {
        compose.setContent { DurtTheme(block) }
    }

    /** Each tag is displayed, clickable, and its click reaches the callback that records `name`. */
    private fun assertTaps(vararg tags: Pair<String, String>) {
        for ((tag, name) in tags) {
            compose
                .onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
            assertEquals(name, clicked.lastOrNull(), "tag $tag")
        }
    }

    private fun assertShown(vararg tags: String) {
        for (tag in tags) compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun welcome() {
        content { WelcomeScreen(WelcomeUiState(), onStart = { clicked += "start" }, onTerms = {}, onPrivacy = {}) }
        assertShown(TestTags.WELCOME_TERMS)
        assertTaps(TestTags.WELCOME_START to "start")
    }

    @Test
    fun phone() {
        content {
            PhoneScreen(
                PhoneUiState(national = "5320000001"),
                onNumberChange = {},
                onSend = { clicked += "send" },
                onBack = { clicked += "back" },
            )
        }
        assertShown(TestTags.PHONE_INPUT)
        assertTaps(TestTags.PHONE_SEND to "send", TestTags.BACK to "back")
    }

    @Test
    fun code() {
        content {
            CodeScreen(
                CodeUiState("+90 532 000 00 01"),
                onCodeChange = {},
                onComplete = {},
                onResend = { clicked += "resend" },
                onChangeNumber = { clicked += "change" },
                onBack = { clicked += "back" },
            )
        }
        assertShown(TestTags.CODE_INPUT)
        assertTaps(TestTags.CODE_CHANGE_NUMBER to "change", TestTags.CODE_RESEND to "resend", TestTags.BACK to "back")
    }

    @Test
    fun `code after a wrong code`() {
        content {
            CodeScreen(
                CodeUiState("+90 532 000 00 01", code = "000000", wrongCode = true),
                onCodeChange = {},
                onComplete = {},
                onResend = { clicked += "resend" },
                onChangeNumber = {},
                onBack = {},
            )
        }
        assertTaps(TestTags.CODE_RESEND to "resend")
    }

    @Test
    fun name() {
        content { NameScreen(NameUiState(name = "Selin"), onNameChange = {}, onContinue = { clicked += "continue" }) }
        assertShown(TestTags.NAME_INPUT)
        assertTaps(TestTags.NAME_CONTINUE to "continue")
    }

    @Test
    fun home() {
        homeWith(ComposeState.Idle)
        assertShown(TestTags.HOME, TestTags.COMPOSE_INPUT, TestTags.COMPOSE_MIC)
        assertTaps(TestTags.BELL to "bell", TestTags.PROFILE to "profile", TestTags.HOME_INVITE to "invite")
    }

    @Test
    fun `home with text`() {
        homeWith(ComposeState.Typing("çöpü at"))
        assertTaps(TestTags.COMPOSE_SEND to "send")
    }

    @Test
    fun `home after a failed send`() {
        homeWith(ComposeState.Failed("çöpü at"))
        assertTaps(TestTags.COMPOSE_RETRY to "send")
    }

    @Test
    fun poke() {
        content {
            PokeScreen(
                PokeUiState(
                    pokeNumber = 1,
                    time = "08:15",
                    title = "Para çek",
                    detail = null,
                    timeLeft = TimeLeft.Minutes(45),
                    snoozeMinutes = 10,
                    lastSnooze = false,
                ),
                onDone = { clicked += "done" },
                onSnooze = { clicked += "snooze" },
                onCantToday = { clicked += "cant" },
            )
        }
        assertShown(TestTags.POKE)
        assertTaps(TestTags.POKE_DONE to "done", TestTags.POKE_SNOOZE to "snooze", TestTags.POKE_CANT_TODAY to "cant")
    }

    private fun homeWith(compose: ComposeState) {
        content {
            HomeScreen(
                state = HomeUiState(myName = "Selin", compose = compose),
                onBell = { clicked += "bell" },
                onProfile = { clicked += "profile" },
                onInvite = { clicked += "invite" },
                onComposeTextChange = {},
                onComposeSend = { clicked += "send" },
                onVoiceStart = {},
                onVoiceEnd = {},
            )
        }
    }
}
