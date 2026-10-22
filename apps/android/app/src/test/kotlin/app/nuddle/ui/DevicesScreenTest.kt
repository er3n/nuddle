package app.nuddle.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.nuddle.ui.screens.devices.DevicesCallbacks
import app.nuddle.ui.screens.devices.DevicesScreen
import app.nuddle.ui.screens.devices.DevicesUiState
import app.nuddle.ui.screens.devices.PhoneRowUi
import app.nuddle.ui.screens.devices.PhonesList
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.profile.ProfileCallbacks
import app.nuddle.ui.screens.profile.ProfileScreen
import app.nuddle.ui.screens.profile.previewProfile
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** 5e Cihazlarım (NUD-142) and its row on profile. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class DevicesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val did = mutableListOf<String>()
    private val mine = PhoneRowUi("Google Pixel 8", "0.1.7", WrittenText.Today("14:32"), current = true)
    private val other = PhoneRowUi(null, "0.1.6", WrittenText.OnDate(12, 10, "18:05"), current = false)

    private fun show(state: DevicesUiState) {
        compose.setContent {
            DurtTheme {
                DevicesScreen(
                    state,
                    DevicesCallbacks(onRetry = { did += "retry" }, onSignOutOthers = { did += "signOutOthers" }),
                )
            }
        }
    }

    @Test
    fun `this phone says so, another shows its version and when it was last seen`() {
        show(DevicesUiState(PhonesList.Shown(listOf(mine, other))))
        compose.onNodeWithText("Bu telefon").assertIsDisplayed()
        compose.onNodeWithText("Sürüm 0.1.7").assertIsDisplayed()
        compose.onNodeWithText("Bilinmeyen telefon").assertIsDisplayed()
        compose.onNodeWithText("Sürüm 0.1.6 · Son görülme 12 Ekim 18:05").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.DEVICES_SIGN_OUT_OTHERS).performScrollTo().performClick()
        assertEquals(listOf("signOutOthers"), did)
    }

    @Test
    fun `with only this phone there is nothing to sign out`() {
        show(DevicesUiState(PhonesList.Shown(listOf(mine))))
        assertEquals(1, compose.onAllNodesWithTag(TestTags.DEVICES_PHONE).fetchSemanticsNodes().size)
        compose.onNodeWithTag(TestTags.DEVICES_SIGN_OUT_OTHERS).assertDoesNotExist()
    }

    @Test
    fun `a list that did not come offers Tekrar dene`() {
        show(DevicesUiState(PhonesList.Failed))
        compose.onNodeWithText("Telefonların yüklenemedi").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.DEVICES_RETRY).performClick()
        assertEquals(listOf("retry"), did)
    }

    @Test
    fun `profile opens Cihazlarım`() {
        compose.setContent { DurtTheme { ProfileScreen(previewProfile, ProfileCallbacks(onDevices = { did += "devices" })) } }
        compose.onNodeWithTag(TestTags.PROFILE_DEVICES).performScrollTo().performClick()
        assertEquals(listOf("devices"), did)
    }
}
