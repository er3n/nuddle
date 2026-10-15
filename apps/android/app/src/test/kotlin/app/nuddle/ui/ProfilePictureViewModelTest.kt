package app.nuddle.ui

import android.net.Uri
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import app.nuddle.testing.FakeAccountRepository
import app.nuddle.testing.FakeAuthRepository
import app.nuddle.testing.FakeAvatarRepository
import app.nuddle.testing.FakeClock
import app.nuddle.testing.FakePermissions
import app.nuddle.testing.InMemoryConnectionDao
import app.nuddle.testing.InMemoryInviteDao
import app.nuddle.testing.InMemorySessionStore
import app.nuddle.testing.MainDispatcherRule
import app.nuddle.ui.screens.profile.ProfileSheet
import app.nuddle.ui.screens.profile.ProfileViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The profile picture on 5a (NUD-72): the sheet, a pick, a removal, and a failure. Robolectric for [Uri]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfilePictureViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val sessions = InMemorySessionStore()
    private val avatars = FakeAvatarRepository()
    private val vm by lazy {
        ProfileViewModel(
            sessions,
            InMemoryConnectionDao(),
            InMemoryInviteDao(),
            FakePermissions(),
            FakeAccountRepository(),
            FakeAuthRepository(),
            avatars,
            IdGenerator(FakeClock()),
        )
    }

    private suspend fun signedIn(avatarPath: String? = null) {
        sessions.saveMe("Eren", QuietHours.default(TimeZone.of("Europe/Istanbul")))
        avatarPath?.let { path -> sessions.profile.value?.let { sessions.saveMe(it.copy(avatarPath = path)) } }
    }

    private fun kotlinx.coroutines.test.TestScope.watch() = backgroundScope.launch { vm.state.collect {} }

    @Test
    fun `the avatar opens the picture sheet, a picked picture goes out and closes it`() =
        runTest(main.dispatcher) {
            signedIn()
            watch()
            assertEquals(false, vm.state.value?.hasPicture)
            vm.onPicture()
            assertEquals(ProfileSheet.Picture(hasPicture = false), vm.state.value?.sheet)
            vm.sheets.onPicked(null)
            assertIs<ProfileSheet.Picture>(vm.state.value?.sheet)
            val uri = Uri.parse("content://media/picker/7")
            vm.sheets.onPicked(uri)
            assertEquals(listOf(uri), avatars.picked)
            assertNull(vm.state.value?.sheet)
        }

    @Test
    fun `with a picture the sheet offers to remove it, and a removal closes it`() =
        runTest(main.dispatcher) {
            signedIn(avatarPath = "avatars/me/1.jpg")
            watch()
            assertEquals(true, vm.state.value?.hasPicture)
            vm.onPicture()
            assertEquals(ProfileSheet.Picture(hasPicture = true), vm.state.value?.sheet)
            vm.sheets.onRemovePicture()
            assertEquals(1, avatars.removed)
            assertNull(vm.state.value?.sheet)
        }

    @Test
    fun `a failure keeps the sheet with the alert`() =
        runTest(main.dispatcher) {
            signedIn(avatarPath = "avatars/me/1.jpg")
            watch()
            avatars.ok = false
            vm.onPicture()
            vm.sheets.onRemovePicture()
            val sheet = vm.state.value?.sheet
            assertIs<ProfileSheet.Picture>(sheet)
            assertTrue(sheet.failed)
            assertEquals(false, sheet.busy)
        }
}
