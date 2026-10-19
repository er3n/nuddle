package app.nuddle.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.nuddle.data.db.AnnouncementEntity
import app.nuddle.domain.Ago
import app.nuddle.testing.FakeClock
import app.nuddle.testing.InMemoryAnnouncementDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.testing.RecordingNewsAnnouncer
import app.nuddle.ui.screens.announcement.AnnouncementButtonUi
import app.nuddle.ui.screens.announcement.AnnouncementUiState
import app.nuddle.ui.screens.announcement.AnnouncementViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours

/** `announcement/{id}` (ADR 0025): opening it marks it read and clears its notification. */
class AnnouncementViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock()
    private val dao = InMemoryAnnouncementDao()
    private val announcer = RecordingNewsAnnouncer()

    private fun vm(id: String) =
        AnnouncementViewModel(SavedStateHandle(mapOf(AnnouncementViewModel.ARG_ANNOUNCEMENT to id)), dao, announcer, clock)

    @Test
    fun `shows the message, marks it opened and withdraws its notification`() =
        runTest(main.dispatcher) {
            val sent = clock.now() - 2.hours
            dao.upsert(
                listOf(AnnouncementEntity("n1", "Premium geldi", "Özet", "**Yeni**", "Davet et", "invite", sent.toEpochMilliseconds())),
            )

            vm("n1").state.test {
                val ready = assertIs<AnnouncementUiState.Ready>(expectMostRecentItem())
                assertEquals("Premium geldi", ready.title)
                assertEquals("**Yeni**", ready.body)
                assertEquals(Ago.Hours(2), ready.ago)
                assertEquals(AnnouncementButtonUi("Davet et", "invite"), ready.button)
                cancelAndIgnoreRemainingEvents()
            }
            assertNotNull(dao.get("n1")?.openedAt)
            assertEquals(listOf("n1"), announcer.withdrawn)
        }

    @Test
    fun `a message not on the phone yet says so`() =
        runTest(main.dispatcher) {
            vm("unknown").state.test {
                assertEquals(AnnouncementUiState.Missing, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
