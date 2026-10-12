package app.nuddle.ui

import app.cash.turbine.test
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.sync.Syncer
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.Cast
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeInviteRepository
import app.nuddle.testing.InMemoryConsentPrompts
import app.nuddle.testing.InMemoryInviteDao
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.ui.screens.home.HomeInvitesViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bell badge and the consent screen that opens by itself after sign-in (docs/ux/screens.md 0f, 1b). */
class HomeInvitesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val inviteDao = InMemoryInviteDao()
    private val prompts = InMemoryConsentPrompts()
    private var syncs = 0
    private val syncer =
        Syncer {
            syncs++
            true
        }
    private val vm by lazy { HomeInvitesViewModel(inviteDao, prompts, FakeInviteRepository(), IdGenerator(FakeClock()), syncer) }

    private fun invite(
        id: String,
        sent: Boolean = false,
        status: String = "open",
        createdAt: Long = 1,
    ) = InviteEntity(id, "c-$id", sent, "Selin", null, Cast.SELIN_ID, "Eren", "spouse", status, createdAt)

    @Test
    fun `the badge counts open invites to me only`() =
        runTest(main.dispatcher) {
            backgroundScope.launch { vm.unread.collect {} }
            inviteDao.upsert(listOf(invite("a"), invite("b", status = "declined"), invite("c", sent = true)))
            assertEquals(1, vm.unread.value)
        }

    @Test
    fun `the consent screen opens once per invite, oldest first`() =
        runTest(main.dispatcher) {
            inviteDao.upsert(listOf(invite("new", createdAt = 2), invite("old", createdAt = 1)))
            vm.openConsent.test {
                assertEquals("old", awaitItem())
                awaitComplete()
            }
            assertEquals(setOf("old"), prompts.ids)
            HomeInvitesViewModel(inviteDao, prompts, FakeInviteRepository(), IdGenerator(FakeClock()), syncer).openConsent.test {
                assertEquals("new", awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `an invite link finds my invite after a sync, or nothing`() =
        runTest(main.dispatcher) {
            inviteDao.upsert(listOf(invite("a"), invite("mine-sent", sent = true)))
            assertEquals("a", vm.inviteForLink("c-a"))
            assertEquals(1, syncs)
            assertEquals(null, vm.inviteForLink("c-mine-sent"), "my own invite's link is not an invite to me")
        }
}
