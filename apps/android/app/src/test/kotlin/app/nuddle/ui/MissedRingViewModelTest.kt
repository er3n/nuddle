package app.nuddle.ui

import app.nuddle.api.model.BrandFix
import app.nuddle.api.model.BrandFixStep
import app.nuddle.api.model.BrandFixTarget
import app.nuddle.data.BrandFixRepository
import app.nuddle.data.MissedRings
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemoryMissedRingStore
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.InMemoryTaskDao
import app.nuddle.testing.InMemoryTaskEventDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.aSession
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.missedring.MissedRingViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** 2h: the brand steps one at a time, and when the bell stops warning (open-questions#22, #38). */
class MissedRingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(Instant.parse("2026-10-13T10:00:00+03:00"))
    private val store = InMemoryMissedRingStore()
    private val rings = MissedRings(InMemorySessionStore(aSession()), InMemoryTaskDao(), InMemoryTaskEventDao(), store)
    private var batteryIgnored = false

    private val xiaomi =
        BrandFix(
            brand = "xiaomi",
            name = "Xiaomi",
            reason = "Xiaomi telefonlar uygulamaları arka planda durdurabiliyor.",
            steps =
                listOf(
                    BrandFixStep("autostart", "Otomatik başlatma", "Aç.", listOf(BrandFixTarget(component = "a/b"))),
                    BrandFixStep(
                        "battery",
                        "Pil tasarrufu: Kısıtlama yok",
                        "Seç.",
                        listOf(BrandFixTarget(action = "android.settings.APPLICATION_DETAILS_SETTINGS", withPackage = true)),
                        check = BrandFixStep.Check.BATTERY_OPTIMIZATION,
                    ),
                ),
        )

    private val answers = ArrayDeque<BrandFix?>()
    private val fixes = BrandFixRepository { answers.removeFirstOrNull() }

    private val vm by lazy {
        MissedRingViewModel(rings, store, fixes, { batteryIgnored }, clock, TimeZone.of("Europe/Istanbul"))
    }

    private fun TestScope.watch() = backgroundScope.launch { vm.state.collect {} }

    @Test
    fun `says when the poke should have rung, why, and the first step to do`() =
        runTest(main.dispatcher) {
            store.lost(Instant.parse("2026-10-12T08:15:00+03:00"))
            answers += xiaomi
            watch()
            val state = vm.state.value
            assertEquals(WrittenText.Yesterday("08:15"), state.lostAt)
            assertEquals(xiaomi.reason, state.reason)
            assertEquals(listOf("autostart", "battery"), state.steps.map { it.id })
            assertEquals("autostart", state.active)
            assertFalse(state.loading)
            assertEquals(listOf(BrandFixTarget(component = "a/b")), vm.targets("autostart"))
        }

    @Test
    fun `an opened step is done, a checked one only when the phone says so, and then the bell stops warning`() =
        runTest(main.dispatcher) {
            store.lost(Instant.parse("2026-10-12T08:15:00+03:00"))
            answers += xiaomi
            watch()
            vm.onOpened("autostart")
            vm.onResume()
            assertEquals("battery", vm.state.value.active)
            assertNull(store.value.fixedAt)

            // Opening is not enough when the phone can tell.
            vm.onOpened("battery")
            vm.onResume()
            assertEquals("battery", vm.state.value.active)

            clock.advance(2.minutes)
            batteryIgnored = true
            vm.onResume()
            assertTrue(
                vm.state.value.steps
                    .all { it.done },
            )
            assertEquals(clock.now(), store.value.fixedAt)
        }

    @Test
    fun `steps opened before the loss have to be done again`() =
        runTest(main.dispatcher) {
            store.opened("autostart", Instant.parse("2026-10-11T12:00:00+03:00"))
            store.lost(Instant.parse("2026-10-12T08:15:00+03:00"))
            answers += xiaomi
            watch()
            assertEquals("autostart", vm.state.value.active)
        }

    @Test
    fun `steps that could not be loaded can be tried again`() =
        runTest(main.dispatcher) {
            answers += null
            answers += xiaomi
            watch()
            assertTrue(vm.state.value.failed)
            vm.onRetry()
            assertFalse(vm.state.value.failed)
            assertEquals(2, vm.state.value.steps.size)
        }
}
