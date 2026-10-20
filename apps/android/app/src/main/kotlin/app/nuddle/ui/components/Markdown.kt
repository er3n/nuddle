package app.nuddle.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * An announcement's body (ADR 0025, screens.md `announcement/{id}`): headings, paragraphs, bold and italic, lists,
 * quotes, links (underlined, they open the browser) and full-width pictures. A picture loads only from a root-relative
 * path, resolved against [imageBase] (the API host); any other source shows its alt text.
 */
@Composable
fun MarkdownBody(
    markdown: String,
    imageBase: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Blocks(blocks, imageBase, modifier)
}

@Composable
private fun Blocks(
    blocks: List<MdBlock>,
    imageBase: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space14)) {
        blocks.forEach { Block(it, imageBase) }
    }
}

@Composable
private fun Block(
    block: MdBlock,
    imageBase: String,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val type = DurtTheme.type
    when (block) {
        is MdBlock.Heading -> {
            val style =
                when (block.level) {
                    1 -> type.h2
                    2 -> type.h3
                    else -> type.bodyStrong
                }
            Text(annotated(block.spans), style = style, color = colors.ink, modifier = Modifier.semantics { heading() })
        }

        is MdBlock.Paragraph -> {
            Text(annotated(block.spans), style = type.body, color = colors.textBody)
        }

        is MdBlock.Image -> {
            MarkdownImage(block, imageBase)
        }

        is MdBlock.ListBlock -> {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
                block.items.forEachIndexed { i, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
                        Text(
                            if (block.ordered) {
                                stringResource(R.string.markdown_number, block.start + i)
                            } else {
                                stringResource(R.string.markdown_bullet)
                            },
                            style = type.body,
                            color = colors.textSecondary,
                        )
                        Blocks(item, imageBase, Modifier.weight(1f))
                    }
                }
            }
        }

        is MdBlock.Quote -> {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
                Box(Modifier.width(dimens.space4).fillMaxHeight().background(colors.border))
                Blocks(block.blocks, imageBase, Modifier.weight(1f))
            }
        }

        MdBlock.Rule -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.space8)
                    .height(dimens.borderCard)
                    .background(colors.border),
            )
        }
    }
}

@Composable
private fun MarkdownImage(
    image: MdBlock.Image,
    imageBase: String,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    val url = imageUrl(image.src, imageBase)
    if (url == null) {
        if (image.alt.isNotBlank()) Text(image.alt, style = DurtTheme.type.caption, color = colors.textSecondary)
        return
    }
    // The original size: the painter is not drawn until it has loaded, so it cannot wait for a draw size.
    val context = LocalContext.current
    val request =
        remember(url) {
            ImageRequest
                .Builder(context)
                .data(url)
                .size(Size.ORIGINAL)
                .build()
        }
    val painter = rememberAsyncImagePainter(request)
    val loaded by painter.state.collectAsState()
    if (loaded is AsyncImagePainter.State.Success) {
        Image(
            painter = painter,
            contentDescription = image.alt.ifBlank { null },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(shape),
        )
    } else {
        // Loading, failed, or a preview without network: the space the picture takes.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.avatarHero)
                .clip(shape)
                .background(colors.surfaceMuted),
        )
    }
}

/** Only our own pictures: a root-relative path on the API host. Null for anything else. */
fun imageUrl(
    src: String,
    imageBase: String,
): String? = if (src.startsWith("/") && !src.startsWith("//")) imageBase.trimEnd('/') + src else null

@Composable
private fun annotated(spans: List<MdSpan>): AnnotatedString {
    val colors = DurtTheme.colors
    return buildAnnotatedString {
        spans.forEach { span ->
            val style =
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.SemiBold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    background = if (span.code) colors.neutralPill else Color.Unspecified,
                )
            if (span.link != null) {
                val linkStyle = TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))
                withLink(LinkAnnotation.Url(span.link, linkStyle)) { withStyle(style) { append(span.text) } }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
}

@PreviewComponent
@Composable
private fun MarkdownBodyPreview() {
    PreviewSurface {
        MarkdownBody(
            """
            ## Neler var?

            Görevi **bir kere** söylersin, unutan *yapana kadar* dürtülür.

            - Kendine görev yaz
            - Bizimkiler'e görev yaz

            ![Nuddle](/announcements/example/hero.png)

            > Daha fazlası için [nuddle.app](https://nuddle.app).
            """.trimIndent(),
            imageBase = "https://nuddle.app/",
        )
    }
}
