package app.nuddle.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.screens.errors.GenericErrorCallbacks
import app.nuddle.ui.screens.errors.GenericErrorScreen
import app.nuddle.ui.screens.errors.InvalidInviteCallbacks
import app.nuddle.ui.screens.errors.InvalidInviteScreen
import app.nuddle.ui.screens.home.HomeScreen
import app.nuddle.ui.screens.home.HomeUiState
import app.nuddle.ui.screens.home.QueuedUi
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** 6a, 6b, 6d and 6f (open-questions#39). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ErrorScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val did = mutableListOf<String>()

    private fun home(state: HomeUiState) {
        compose.setContent {
            DurtTheme { HomeScreen(state, {}, {}, {}, {}, { did += "send" }, {}, {}) }
        }
    }

    @Test
    fun `6a, offline home shows the banner and the queued text as Sırada`() {
        home(HomeUiState(myName = "Eren", connected = true, offline = true, queued = listOf(QueuedUi("k", "faturayı öde"))))
        compose
            .onNodeWithText("İnternet yok. Dürtmeler yine zamanında çalar, değişiklikler bağlantı gelince gider.")
            .assertIsDisplayed()
        compose.onNodeWithTag(TestTags.HOME_QUEUED).assertIsDisplayed()
        compose.onNodeWithContentDescription("faturayı öde, bağlantı gelince gidecek, Sırada").assertIsDisplayed()
    }

    @Test
    fun `6b, a failed send keeps the text under the alert with Tekrar dene`() {
        home(HomeUiState(myName = "Eren", compose = ComposeState.Failed("kuru temizlemeyi al")))
        compose.onNodeWithText("Şu an anlayamadım").assertIsDisplayed()
        compose.onNodeWithText("Tekrar dene").performClick()
        assertEquals(listOf("send"), did)
    }

    @Test
    fun `6d goes home or to the invite screen`() {
        compose.setContent {
            DurtTheme { InvalidInviteScreen(InvalidInviteCallbacks(onHome = { did += "home" }, onInvite = { did += "invite" })) }
        }
        compose.onNodeWithText("Bu davet artık geçerli değil").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.INVALID_INVITE_HOME).performClick()
        compose.onNodeWithText("Birini ben davet edeyim").performClick()
        assertEquals(listOf("home", "invite"), did)
    }

    @Test
    fun `6f shows the code, retries or goes home`() {
        compose.setContent {
            DurtTheme {
                GenericErrorScreen("DRT-5001", GenericErrorCallbacks(onRetry = { did += "retry" }, onHome = { did += "home" }))
            }
        }
        compose.onNodeWithText("Hata kodu: DRT-5001").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.GENERIC_ERROR_RETRY).performClick()
        compose.onNodeWithTag(TestTags.GENERIC_ERROR_HOME).performClick()
        assertEquals(listOf("retry", "home"), did)
    }
}
