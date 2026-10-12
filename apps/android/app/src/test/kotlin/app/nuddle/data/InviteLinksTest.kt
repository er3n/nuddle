package app.nuddle.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Invite links `https://nuddle.app/i/{code}` (open-questions#13). */
class InviteLinksTest {
    @Test
    fun `a link carries its code both ways`() {
        assertEquals("https://nuddle.app/i/abc_-9", InviteLinks.of("abc_-9"))
        assertEquals("abc_-9", InviteLinks.codeOf("https://nuddle.app/i/abc_-9"))
        assertEquals("abc", InviteLinks.codeOf("https://nuddle.app/i/abc/"))
    }

    @Test
    fun `other links are not invites`() {
        val notInvites =
            listOf(
                null,
                "",
                "http://nuddle.app/i/abc",
                "https://evil.app/i/abc",
                "https://nuddle.app/x/abc",
                "https://nuddle.app/i/",
                "https://nuddle.app/i/a/b",
            )
        for (link in notInvites) {
            assertNull(InviteLinks.codeOf(link), link)
        }
    }
}
