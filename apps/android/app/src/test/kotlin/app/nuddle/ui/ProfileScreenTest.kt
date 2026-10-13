package app.nuddle.ui

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.SetupStep
import app.nuddle.ui.screens.profile.ProfileCallbacks
import app.nuddle.ui.screens.profile.ProfileScreen
import app.nuddle.ui.screens.profile.ProfileSheet
import app.nuddle.ui.screens.profile.ProfileSheetCallbacks
import app.nuddle.ui.screens.profile.ProfileUiState
import app.nuddle.ui.screens.profile.previewProfile
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** 5a and its sheets: what each row does (open-questions#37). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ProfileScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val did = mutableListOf<String>()

    private fun show(state: ProfileUiState = previewProfile) {
        compose.setContent {
            DurtTheme {
                ProfileScreen(
                    state,
                    ProfileCallbacks(
                        onOpenPerson = { did += "person $it" },
                        onUseConnections = { did += "useConnections $it" },
                        onFixPermission = { did += "fix $it" },
                        onQuietHours = { did += "quiet" },
                        onLogOut = { did += "logOut" },
                    ),
                    sheetCallbacks = ProfileSheetCallbacks(onConfirm = { did += "confirm" }),
                )
            }
        }
    }

    @Test
    fun `rows open the person, the quiet hours sheet and log out, and a switch reports its new value`() {
        show()
        compose.onNodeWithTag(TestTags.profilePerson("Sibel")).performClick()
        compose.onNodeWithTag(TestTags.PROFILE_USE_CONNECTIONS).performClick()
        compose.onNodeWithTag(TestTags.PROFILE_QUIET).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.PROFILE_LOG_OUT).performScrollTo().performClick()
        assertEquals(listOf("person sibel", "useConnections false", "quiet", "logOut"), did)
    }

    @Test
    fun `only a permission that is off can be fixed`() {
        show(previewProfile.copy(permissions = GrantedPermissions(notifications = true, exactAlarm = false, fullScreen = true)))
        compose.onNodeWithTag(TestTags.profilePermission(SetupStep.NOTIFICATIONS.name)).performScrollTo().assertHasNoClickAction()
        compose.onNodeWithTag(TestTags.profilePermission(SetupStep.EXACT_ALARM.name)).performScrollTo().performClick()
        assertEquals(listOf("fix ${SetupStep.EXACT_ALARM}"), did)
    }

    @Test
    fun `a switch that is off shows off`() {
        show(previewProfile.copy(notifyMeForOthersTasks = false))
        compose.onNodeWithTag(TestTags.PROFILE_NOTIFY_ME).assertIsOff()
    }

    @Test
    fun `quiet hours without a length cannot be saved`() {
        show(previewProfile.copy(sheet = ProfileSheet.Quiet(LocalTime(7, 0), LocalTime(7, 0))))
        compose.onNodeWithTag(TestTags.SHEET_PRIMARY).assertIsNotEnabled()
    }

    @Test
    fun `without a connection, deleting names nobody else`() {
        show(previewProfile.copy(people = emptyList(), sheet = ProfileSheet.Delete()))
        compose.onNodeWithText("Sana yazılan açık işler iptal olur, yazana haber veririm.").assertDoesNotExist()
        compose.onNodeWithText("Geri alınamaz.").assertExists()
    }
}
