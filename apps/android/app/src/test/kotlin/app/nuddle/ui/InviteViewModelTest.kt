package app.nuddle.ui

import app.cash.turbine.test
import app.nuddle.api.model.EdgeType
import app.nuddle.domain.IdGenerator
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakeInviteRepository
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.ui.screens.invite.InviteViewModel
import app.nuddle.ui.screens.invite.Invitee
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inviting someone (docs/ux/screens.md 1e, 1f; docs/product.md, Connections). */
class InviteViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val invites = FakeInviteRepository()
    private val vm by lazy { InviteViewModel(invites, IdGenerator(FakeClock())) }

    @Test
    fun `a contact and a relationship are both needed, nothing is preselected`() {
        assertNull(vm.state.value.relationship)
        assertFalse(vm.state.value.canSend)

        vm.onContactPicked("Selin", "0532 987 65 43")
        assertEquals(Invitee("Selin", "+905329876543"), vm.state.value.contact)
        assertFalse(vm.state.value.canSend)

        vm.onRelationship(EdgeType.SPOUSE)
        assertTrue(vm.state.value.canSend)
    }

    @Test
    fun `a contact number that is not a Turkish mobile is refused`() {
        vm.onContactPicked("Tom", "+44 20 7946 0958")
        assertNull(vm.state.value.contact)
        assertTrue(vm.state.value.numberInvalid)
    }

    @Test
    fun `a number typed by hand needs a name and a valid number`() {
        vm.onTypeNumber()
        vm.onRelationship(EdgeType.SIBLING)
        vm.onTypedNumber("0532 987 65 43")
        assertFalse(vm.state.value.canSend, "no name yet")
        vm.onTypedName(" Defne ")
        assertEquals(Invitee("Defne", "+905329876543"), vm.state.value.invitee)
    }

    @Test
    fun `Daveti gönder creates the invite and hands the link to the share sheet`() =
        runTest(main.dispatcher) {
            vm.onContactPicked("Selin", "+905329876543")
            vm.onRelationship(EdgeType.SPOUSE)
            vm.share.test {
                vm.onSend()
                assertEquals("https://nuddle.app/i/abc", awaitItem())
            }
            assertEquals(listOf("+905329876543", "Selin", EdgeType.SPOUSE), invites.created.single().take(3))
        }

    @Test
    fun `a failed send says so and a retry reuses the idempotency key`() =
        runTest(main.dispatcher) {
            invites.code = null
            vm.onContactPicked("Selin", "+905329876543")
            vm.onRelationship(EdgeType.SPOUSE)
            vm.onSend()
            assertTrue(vm.state.value.failed)

            invites.code = "abc"
            vm.onSend()
            assertFalse(vm.state.value.failed)
            assertEquals(invites.created[0][3], invites.created[1][3])
        }
}
