package app.nuddle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Setup steps (docs/product.md, Permissions and setup; docs/ux/screens.md 2e). */
class SetupRulesTest {
    private val none = GrantedPermissions(notifications = false, exactAlarm = false, fullScreen = false)

    @Test
    fun `steps go in order and granted ones are skipped`() {
        assertEquals(SetupStep.NOTIFICATIONS, SetupRules.active(none, emptySet()))
        assertEquals(SetupStep.EXACT_ALARM, SetupRules.active(none.copy(notifications = true), emptySet()))
        assertEquals(
            SetupStep.FULL_SCREEN,
            SetupRules.active(none.copy(notifications = true, exactAlarm = true), emptySet()),
        )
    }

    @Test
    fun `a skipped step is passed over and setup ends when nothing is left`() {
        val notificationsOnly = none.copy(notifications = true)
        assertEquals(SetupStep.FULL_SCREEN, SetupRules.active(notificationsOnly, setOf(SetupStep.EXACT_ALARM)))
        assertNull(SetupRules.active(notificationsOnly, setOf(SetupStep.EXACT_ALARM, SetupStep.FULL_SCREEN)))
        assertNull(SetupRules.active(GrantedPermissions(notifications = true, exactAlarm = true, fullScreen = true), emptySet()))
    }

    @Test
    fun `notifications cannot be skipped`() {
        assertEquals(SetupStep.NOTIFICATIONS, SetupRules.active(none, SetupStep.entries.toSet()))
    }

    @Test
    fun `the settings count leaves out the notification dialog`() {
        assertEquals(2, SetupRules.settingsLeft(none))
        assertEquals(1, SetupRules.settingsLeft(none.copy(exactAlarm = true)))
        assertEquals(0, SetupRules.settingsLeft(none.copy(exactAlarm = true, fullScreen = true)))
    }

    @Test
    fun `the battery step exists only on phones that put apps to sleep`() {
        assertEquals(listOf(SetupStep.NOTIFICATIONS, SetupStep.EXACT_ALARM, SetupStep.FULL_SCREEN), none.steps)
        val samsung = none.copy(battery = false)
        assertEquals(SetupStep.entries, samsung.steps)
        assertEquals(3, SetupRules.settingsLeft(samsung))
        assertEquals(
            SetupStep.BATTERY,
            SetupRules.active(samsung.copy(notifications = true, exactAlarm = true, fullScreen = true), emptySet()),
        )
        assertNull(SetupRules.active(samsung.copy(notifications = true, exactAlarm = true, fullScreen = true, battery = true), emptySet()))
        assertNull(
            SetupRules.active(samsung.copy(notifications = true, exactAlarm = true, fullScreen = true), setOf(SetupStep.BATTERY)),
        )
    }

    @Test
    fun `the brands that put apps to sleep, in any case`() {
        assertEquals(true, SetupRules.needsBatteryStep("samsung"))
        assertEquals(true, SetupRules.needsBatteryStep("Xiaomi"))
        assertEquals(true, SetupRules.needsBatteryStep("OnePlus"))
        assertEquals(false, SetupRules.needsBatteryStep("Google"))
        assertEquals(false, SetupRules.needsBatteryStep(""))
    }
}
