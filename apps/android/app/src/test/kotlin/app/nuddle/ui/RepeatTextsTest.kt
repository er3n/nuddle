package app.nuddle.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.nuddle.testing.aTask
import app.nuddle.ui.screens.home.RepeatUi
import app.nuddle.ui.screens.home.repeat
import app.nuddle.ui.screens.home.text
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A repeating task's rule as the screens show it (ADR 0021, NUD-77). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatTextsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the rule reads as Her gün, Her salı or Hafta içi with the time`() {
        val texts = mutableListOf<String>()
        compose.setContent {
            DurtTheme {
                texts += RepeatUi("day", emptyList(), "09:00").text()
                texts += RepeatUi("week", listOf(2), "19:00").text()
                texts += RepeatUi("week", listOf(1, 4), "07:30").text()
                texts += RepeatUi("weekdays", emptyList(), "07:00").text()
            }
        }
        assertEquals(listOf("Her gün 09:00", "Her salı 19:00", "Her pazartesi, perşembe 07:30", "Hafta içi 07:00"), texts)
    }

    @Test
    fun `the rule is read from the task row, and a one-off has none`() {
        assertNull(aTask().repeat)
        val weekly = aTask().copy(repeatEvery = "week", repeatDays = "2,4", repeatTime = "18:00")
        assertEquals(RepeatUi("week", listOf(2, 4), "18:00"), weekly.repeat)
        val daily = aTask().copy(repeatEvery = "day", repeatDays = null, repeatTime = "09:00")
        assertEquals(RepeatUi("day", emptyList(), "09:00"), daily.repeat)
    }
}
