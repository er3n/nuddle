package app.nuddle.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.nuddle.R
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.SettingsGroup
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Uppercase labels follow the language of the strings, not the device (NUD-37): on a phone set to English the Turkish
 * default resources still show "SIRADAKİ", never "SIRADAKI".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en")
class UppercaseLocaleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `labels keep the Turkish dotted capital I on an English device`() {
        var label = ""
        compose.setContent {
            DurtTheme {
                label = stringResource(R.string.home_next_up).uppercaseForUi()
                SettingsGroup(title = "Bildirimler") {}
                Avatar(name = "ilker", color = DurtTheme.colors.ink, size = DurtTheme.dimens.avatarList)
            }
        }
        assertEquals("SIRADAKİ", label)
        compose.onNodeWithText("BİLDİRİMLER").assertExists()
        compose.onNodeWithText("İ").assertExists()
    }
}
