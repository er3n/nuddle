package app.nuddle.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

/**
 * An underlined text action. Visually small, so it keeps a 44 dp touch target through
 * `minimumInteractiveComponentSize` (docs/ux/platform.md, Accessibility). Defaults to the `link` color.
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DurtTheme.colors.link,
    style: TextStyle = DurtTheme.type.body,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedColor = if (color == DurtTheme.colors.link) DurtTheme.colors.linkPressed else color
    Text(
        text = text,
        style = style.copy(textDecoration = TextDecoration.Underline),
        color = if (pressed) pressedColor else color,
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    )
}

@PreviewComponent
@Composable
private fun TextLinkPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space8)) {
            TextLink("Numarayı değiştir", onClick = {})
            TextLink("Bugün yapamayacağım", onClick = {}, color = DurtTheme.colors.ink, style = DurtTheme.type.button)
        }
    }
}
