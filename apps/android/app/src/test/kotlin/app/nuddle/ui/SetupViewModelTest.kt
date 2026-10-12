package app.nuddle.ui

import app.cash.turbine.test
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.SetupStep
import app.nuddle.testing.FakePermissions
import app.nuddle.testing.InMemorySetupStore
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingStarter
import app.nuddle.ui.screens.setup.SetupEffect
import app.nuddle.ui.screens.setup.SetupUiState
import app.nuddle.ui.screens.setup.SetupViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Setup (docs/product.md, Permissions and setup; docs/ux/screens.md 2c, 2d, 2e). */
class SetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val setup = InMemorySetupStore()
    private val permissions =
        FakePermissions(GrantedPermissions(notifications = false, exactAlarm = false, fullScreen = false))
    private val starter = RecordingStarter()
    private val vm by lazy { SetupViewModel(setup, permissions, starter, CoroutineScope(main.dispatcher)) }

    private fun steps(): SetupUiState.Steps = assertIs(vm.state.value)

    private fun grant(block: GrantedPermissions.() -> GrantedPermissions) {
        permissions.granted = permissions.granted.block()
        vm.onResume()
    }

    @OptIn(ExperimentalCoroutinesApi::class) // Reason: advanceTimeBy is how virtual time moves (ADR 0009).
    @Test
    fun `Bir dene rings the demo poke after 3 seconds`() =
        runTest(main.dispatcher) {
            vm.onTry()
            assertEquals(SetupUiState.Intro(starting = true), vm.state.value)
            advanceTimeBy(2_999)
            assertIs<SetupUiState.Intro>(vm.state.value)
            advanceTimeBy(2)
            assertEquals(SetupUiState.Demo(settingsLeft = 2), vm.state.value)
        }

    @Test
    fun `the steps open with the notification dialog`() =
        runTest(main.dispatcher) {
            vm.effects.test {
                vm.onStartSteps()
                assertEquals(SetupEffect.RequestNotifications, awaitItem())
            }
            assertEquals(SetupStep.NOTIFICATIONS, steps().active)
            assertEquals(2, steps().settingsCount)
        }

    @Test
    fun `granted steps move on and the last one finishes setup`() =
        runTest(main.dispatcher) {
            vm.effects.test {
                vm.onStartSteps()
                assertEquals(SetupEffect.RequestNotifications, awaitItem())

                grant { copy(notifications = true) }
                assertEquals(SetupStep.EXACT_ALARM, steps().active)
                vm.onOpenActive()
                assertEquals(SetupEffect.OpenSettings(SetupStep.EXACT_ALARM), awaitItem())

                grant { copy(exactAlarm = true) }
                assertEquals(SetupStep.FULL_SCREEN, steps().active)
                assertEquals(2, steps().settingsCount, "the title keeps the count it opened with")

                grant { copy(fullScreen = true) }
                assertEquals(SetupEffect.Finished, awaitItem())
            }
            assertTrue(setup.done)
            assertEquals(1, starter.starts, "the new permissions go up with the device registration")
        }

    @Test
    fun `alarm and full screen can be skipped, notifications cannot`() =
        runTest(main.dispatcher) {
            vm.effects.test {
                vm.onStartSteps()
                awaitItem()
                vm.onSkipActive()
                assertEquals(SetupStep.NOTIFICATIONS, steps().active)

                grant { copy(notifications = true) }
                vm.onSkipActive()
                assertEquals(SetupStep.FULL_SCREEN, steps().active)
                assertTrue(steps().steps.single { it.step == SetupStep.EXACT_ALARM }.skipped)

                vm.onSkipActive()
                assertEquals(SetupEffect.Finished, awaitItem())
            }
            assertTrue(setup.done)
        }

    @Test
    fun `Denemeden kur with everything but notifications granted asks only for notifications`() =
        runTest(main.dispatcher) {
            permissions.granted = GrantedPermissions(notifications = false, exactAlarm = true, fullScreen = true)
            vm.effects.test {
                vm.onStartSteps()
                assertEquals(SetupEffect.RequestNotifications, awaitItem())
                assertEquals(0, steps().settingsCount)

                grant { copy(notifications = true) }
                assertEquals(SetupEffect.Finished, awaitItem())
            }
        }
}
