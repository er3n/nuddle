package app.nuddle.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.nuddle.domain.SetupStep
import app.nuddle.ui.screens.inbox.BellWarning
import app.nuddle.ui.screens.inbox.InboxScreen
import app.nuddle.ui.screens.inbox.InboxUiState
import app.nuddle.ui.screens.inbox.NextPokeUi
import app.nuddle.ui.screens.inbox.PokeDay
import app.nuddle.ui.screens.missedring.MissedRingCallbacks
import app.nuddle.ui.screens.missedring.MissedRingScreen
import app.nuddle.ui.screens.missedring.MissedRingUiState
import app.nuddle.ui.screens.missedring.RearmUi
import app.nuddle.ui.screens.missedring.previewMissedRing
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** 2g and 2h: the warning cards in the bell and the brand steps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class MissedRingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val did = mutableListOf<String>()

    private fun showSteps(state: MissedRingUiState = previewMissedRing) {
        compose.setContent {
            DurtTheme {
                MissedRingScreen(
                    state,
                    MissedRingCallbacks(
                        onOpen = { did += "open $it" },
                        onRetry = { did += "retry" },
                        onRearm = { did += "rearm" },
                    ),
                )
            }
        }
    }

    @Test
    fun `2h says when, why and how many settings, and opens the active step`() {
        showSteps()
        compose
            .onNodeWithText(
                "Seni dün 08:15 için dürtecektim, telefonun beni kapatmış. " +
                    "Xiaomi telefonlar uygulamaları arka planda durdurabiliyor. İki ayarla düzelir.",
            ).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.MISSED_RING_OPEN).performScrollTo().performClick()
        assertEquals(listOf("open battery"), did)
    }

    @Test
    fun `2h offers to try again when the steps did not load`() {
        showSteps(MissedRingUiState(loading = false, failed = true))
        compose.onNodeWithText("Seni dürtecektim, telefonun beni kapatmış.").assertIsDisplayed()
        compose.onNodeWithText("Tekrar dene").performClick()
        assertEquals(listOf("retry"), did)
    }

    @Test
    fun `2h opened by the person asks, and ends with re-installing the pokes (NUD-126)`() {
        showSteps(previewMissedRing.copy(lostAt = null, reported = true))
        compose.onNodeWithText("Dürtmen çalmadı mı?").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Telefonun beni arka planda kapatmış olabilir. " +
                    "Xiaomi telefonlar uygulamaları arka planda durdurabiliyor. İki ayarla düzelir.",
            ).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.MISSED_RING_REARM).performScrollTo().performClick()
        assertEquals(listOf("rearm"), did)
    }

    @Test
    fun `2h says the pokes were re-installed in place of the button (NUD-126)`() {
        showSteps(previewMissedRing.copy(rearm = RearmUi.Done))
        compose.onNodeWithText("Bu telefondaki dürtmelerin yeniden kuruldu.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TestTags.MISSED_RING_REARM).assertDoesNotExist()
    }

    @Test
    fun `2g each warning card fixes its own warning`() {
        val fullScreen = BellWarning.PermissionOff(SetupStep.FULL_SCREEN, NextPokeUi("Para çek", PokeDay.Tomorrow))
        val fixed = mutableListOf<BellWarning>()
        compose.setContent {
            DurtTheme {
                InboxScreen(
                    InboxUiState(warnings = listOf(BellWarning.MissedRing, fullScreen)),
                    onBack = {},
                    onOpenInvite = {},
                    onFix = { fixed += it },
                )
            }
        }
        compose.onNodeWithText("Son dürtmen çalmadı").assertIsDisplayed()
        compose
            .onNodeWithText("Yarınki \"Para çek\" dürtmen sadece bildirim olarak gelecek. Kaçırma ihtimalin artıyor.")
            .assertIsDisplayed()
        compose.onNodeWithTag(TestTags.bellFix(TestTags.bellPermission("FULL_SCREEN"))).performClick()
        compose.onNodeWithTag(TestTags.bellFix(TestTags.BELL_MISSED_RING)).performClick()
        assertEquals(listOf(fullScreen, BellWarning.MissedRing), fixed)
    }
}
