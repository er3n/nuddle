package app.nuddle.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import app.nuddle.domain.TurkishPhone
import app.nuddle.ui.screens.invite.InviteCallbacks
import app.nuddle.ui.screens.invite.InviteScreen
import app.nuddle.ui.screens.invite.InviteUiState
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A number typed by hand on 1e keeps the order of the digits. The E2E smoke found "5320000002" stored as
 * "5320000200": the field showed the grouped number as its value, so the cursor fell behind each space.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InviteNumberInputTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `digits typed one by one stay in order`() {
        var typed = ""
        compose.setContent {
            var state by remember { mutableStateOf(InviteUiState(typing = true)) }
            DurtTheme {
                InviteScreen(
                    state,
                    InviteCallbacks(
                        onTypedNumber = {
                            state = state.copy(typedNumber = TurkishPhone.sanitize(it))
                            typed = state.typedNumber
                        },
                    ),
                )
            }
        }
        "5320000002".forEach { compose.onNodeWithTag(TestTags.INVITE_NUMBER).performTextInput(it.toString()) }
        assertEquals("5320000002", typed)
    }

    @Test
    fun `the typed name and number fields have visible labels`() {
        compose.setContent { DurtTheme { InviteScreen(InviteUiState(typing = true), InviteCallbacks()) } }
        compose.onNodeWithText("Adı").assertIsDisplayed()
        compose.onNodeWithText("Telefon numarası").assertIsDisplayed()
    }
}
