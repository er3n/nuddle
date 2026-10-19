package app.nuddle.ui.components

import androidx.compose.runtime.Immutable
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.commonmark.node.Text as MdText

/** A run of text with one style. [link] is set only for http and https targets. */
@Immutable
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

/** The part of CommonMark an announcement shows (ADR 0025); anything else is dropped or kept as text. */
@Immutable
sealed interface MdBlock {
    data class Heading(
        val level: Int,
        val spans: List<MdSpan>,
    ) : MdBlock

    data class Paragraph(
        val spans: List<MdSpan>,
    ) : MdBlock

    /** [src] as written; only a root-relative path is loaded, from the app's own API host. */
    data class Image(
        val src: String,
        val alt: String,
    ) : MdBlock

    data class ListBlock(
        val ordered: Boolean,
        val start: Int,
        val items: List<List<MdBlock>>,
    ) : MdBlock

    data class Quote(
        val blocks: List<MdBlock>,
    ) : MdBlock

    data object Rule : MdBlock
}

private val parser: Parser = Parser.builder().build()
private val htmlImage = Regex("""<img\s[^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val htmlAlt = Regex("""alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE)

/** Parses [markdown] into the blocks [MarkdownBody] draws. Pure, so it is tested without Compose. */
fun parseMarkdown(markdown: String): List<MdBlock> = blocksOf(parser.parse(markdown))

private fun blocksOf(parent: Node): List<MdBlock> = children(parent).flatMap(::blockOf)

private fun children(parent: Node): List<Node> = generateSequence(parent.firstChild) { it.next }.toList()

private fun blockOf(node: Node): List<MdBlock> =
    when (node) {
        is Heading -> {
            listOf(MdBlock.Heading(node.level, inlineOf(node)))
        }

        is Paragraph -> {
            paragraphOf(node)
        }

        is BulletList -> {
            listOf(MdBlock.ListBlock(ordered = false, start = 1, items = children(node).map(::blocksOf)))
        }

        is OrderedList -> {
            listOf(MdBlock.ListBlock(ordered = true, start = node.markerStartNumber ?: 1, items = children(node).map(::blocksOf)))
        }

        is ListItem -> {
            blocksOf(node)
        }

        is BlockQuote -> {
            listOf(MdBlock.Quote(blocksOf(node)))
        }

        is ThematicBreak -> {
            listOf(MdBlock.Rule)
        }

        is FencedCodeBlock -> {
            listOf(MdBlock.Paragraph(listOf(MdSpan(node.literal.trimEnd(), code = true))))
        }

        is IndentedCodeBlock -> {
            listOf(MdBlock.Paragraph(listOf(MdSpan(node.literal.trimEnd(), code = true))))
        }

        is HtmlBlock -> {
            htmlImages(node.literal)
        }

        else -> {
            emptyList()
        }
    }

private fun htmlImages(html: String): List<MdBlock> =
    htmlImage
        .findAll(html)
        .map { m ->
            MdBlock.Image(
                m.groupValues[1],
                htmlAlt
                    .find(m.value)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty(),
            )
        }.toList()

/** A paragraph with pictures in it becomes text, picture, text: a picture always takes the full width. */
private fun paragraphOf(node: Paragraph): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val run = mutableListOf<MdSpan>()

    fun flush() {
        val spans = trimRun(run)
        if (spans.isNotEmpty()) out += MdBlock.Paragraph(spans)
        run.clear()
    }
    for (child in children(node)) {
        when {
            child is Image -> {
                flush()
                out += MdBlock.Image(child.destination, plainText(child))
            }

            child is HtmlInline && htmlImage.containsMatchIn(child.literal) -> {
                flush()
                out += htmlImages(child.literal)
            }

            else -> {
                inline(child, Style(), run)
            }
        }
    }
    flush()
    return out
}

private fun trimRun(run: List<MdSpan>): List<MdSpan> {
    val spans = run.filter { it.text.isNotEmpty() }.toMutableList()
    while (spans.firstOrNull()?.text?.isBlank() == true) spans.removeAt(0)
    while (spans.lastOrNull()?.text?.isBlank() == true) spans.removeAt(spans.lastIndex)
    return spans
}

private data class Style(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null,
)

private fun inlineOf(parent: Node): List<MdSpan> =
    mutableListOf<MdSpan>().also { out ->
        children(parent).forEach {
            inline(it, Style(), out)
        }
    }

private fun inline(
    node: Node,
    style: Style,
    out: MutableList<MdSpan>,
) {
    fun span(
        text: String,
        code: Boolean = false,
    ) = MdSpan(text, bold = style.bold, italic = style.italic, code = code, link = style.link)
    when (node) {
        is MdText -> {
            out += span(node.literal)
        }

        is Code -> {
            out += span(node.literal, code = true)
        }

        is SoftLineBreak -> {
            out += span(" ")
        }

        is HardLineBreak -> {
            out += span("\n")
        }

        is Image -> {
            out += span(plainText(node))
        }

        else -> {
            children(node).forEach { inline(it, styleInside(node, style), out) }
        }
    }
}

/** The style of the text inside [node]: emphasis adds italic or bold, a link only an http or https target. */
private fun styleInside(
    node: Node,
    style: Style,
): Style =
    when (node) {
        is Emphasis -> style.copy(italic = true)
        is StrongEmphasis -> style.copy(bold = true)
        is Link -> style.copy(link = node.destination.takeIf { it.startsWith("https://") || it.startsWith("http://") })
        else -> style
    }

private fun plainText(node: Node): String =
    buildString {
        fun walk(n: Node) {
            if (n is MdText) append(n.literal) else children(n).forEach(::walk)
        }
        walk(node)
    }
