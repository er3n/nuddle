package app.nuddle.data

import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.data.avatar.ME
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The profile pictures cached on the phone, one per person next to the server path they are for (NUD-72). */
class AvatarFilesTest {
    @get:Rule val temp = TemporaryFolder()

    private val files by lazy { AvatarFiles(temp.newFolder("avatars")) }

    @Test
    fun `a saved picture is found by its key with the path it was fetched for`() {
        assertNull(files.pathOf("sibel"))
        files.save("sibel", "avatars/sibel/1.jpg", byteArrayOf(1, 2, 3))
        assertEquals("avatars/sibel/1.jpg", files.pathOf("sibel"))
        assertEquals(listOf<Byte>(1, 2, 3), files.of("sibel").readBytes().toList())
        files.delete("sibel")
        assertNull(files.pathOf("sibel"))
    }

    @Test
    fun `every change bumps the version, a delete of nothing does not`() {
        val start = files.version.value
        files.delete("nobody")
        assertEquals(start, files.version.value)
        files.save(ME, "avatars/me/1.jpg", byteArrayOf(1))
        files.delete(ME)
        assertEquals(start + 2, files.version.value)
    }

    @Test
    fun `the picked picture waits apart from the kept ones until it is uploaded`() {
        assertNull(files.pending())
        files.savePending(byteArrayOf(9))
        assertEquals(listOf<Byte>(9), files.pending()?.readBytes()?.toList())
        assertNull(files.pathOf(ME))
        files.deletePending()
        assertNull(files.pending())
    }

    @Test
    fun `signing out removes everything`() {
        files.save(ME, "avatars/me/1.jpg", byteArrayOf(1))
        files.savePending(byteArrayOf(2))
        files.deleteAll()
        assertNull(files.pathOf(ME))
        assertNull(files.pending())
        assertTrue(files.of(ME).parentFile?.exists() != true)
    }
}
