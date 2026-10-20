package app.nuddle.ui

import app.nuddle.ui.components.MdBlock
import app.nuddle.ui.components.MdSpan
import app.nuddle.ui.components.imageUrl
import app.nuddle.ui.components.parseMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** An announcement's body as blocks (ADR 0025): what the detail screen draws. */
class MarkdownTest {
    @Test
    fun `headings, emphasis and links become styled spans`() {
        assertEquals(
            listOf(
                MdBlock.Heading(2, listOf(MdSpan("Neler var?"))),
                MdBlock.Paragraph(
                    listOf(
                        MdSpan("Bir "),
                        MdSpan("kere", bold = true),
                        MdSpan(" söyle, "),
                        MdSpan("yap", italic = true),
                        MdSpan(". "),
                        MdSpan("Site", link = "https://nuddle.app"),
                    ),
                ),
            ),
            parseMarkdown("## Neler var?\n\nBir **kere** söyle, *yap*. [Site](https://nuddle.app)"),
        )
    }

    @Test
    fun `a link that is not http or https stays plain text`() {
        assertEquals(
            listOf(MdBlock.Paragraph(listOf(MdSpan("tıkla")))),
            parseMarkdown("[tıkla](javascript:alert(1))"),
        )
    }

    @Test
    fun `a picture inside a paragraph splits it into text, picture, text`() {
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(MdSpan("Önce"))),
                MdBlock.Image("/announcements/p/hero.png", "Hero"),
                MdBlock.Paragraph(listOf(MdSpan("Sonra"))),
            ),
            parseMarkdown("Önce\n![Hero](/announcements/p/hero.png)\nSonra"),
        )
    }

    @Test
    fun `an html img is a picture too`() {
        assertEquals(
            listOf(MdBlock.Image("/announcements/p/a.png", "A")),
            parseMarkdown("<img src=\"/announcements/p/a.png\" alt=\"A\">"),
        )
    }

    @Test
    fun `lists keep their items and an ordered list its start`() {
        assertEquals(
            listOf(
                MdBlock.ListBlock(
                    ordered = true,
                    start = 3,
                    items = listOf(listOf(MdBlock.Paragraph(listOf(MdSpan("üç")))), listOf(MdBlock.Paragraph(listOf(MdSpan("dört"))))),
                ),
                MdBlock.ListBlock(ordered = false, start = 1, items = listOf(listOf(MdBlock.Paragraph(listOf(MdSpan("madde")))))),
            ),
            parseMarkdown("3. üç\n4. dört\n\n- madde"),
        )
    }

    @Test
    fun `quotes and rules`() {
        assertEquals(
            listOf(MdBlock.Quote(listOf(MdBlock.Paragraph(listOf(MdSpan("alıntı"))))), MdBlock.Rule),
            parseMarkdown("> alıntı\n\n---"),
        )
    }

    @Test
    fun `only root-relative pictures load, from the API host`() {
        assertEquals("https://nuddle.app/announcements/p/a.png", imageUrl("/announcements/p/a.png", "https://nuddle.app/"))
        assertNull(imageUrl("https://elsewhere.example/a.png", "https://nuddle.app/"))
        assertNull(imageUrl("//elsewhere.example/a.png", "https://nuddle.app/"))
    }
}
