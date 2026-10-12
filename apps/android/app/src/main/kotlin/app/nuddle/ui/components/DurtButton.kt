package app.nuddle.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtColors
import app.nuddle.ui.theme.DurtDimens
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.DurtType
import app.nuddle.ui.theme.pressed

enum class DurtButtonKind { Primary, Accent, Secondary, Ghost, Danger, DangerOutline, Soft, Success, OnPoke, OnPokeOutline }

enum class DurtButtonSize { Poke, Large, Medium, Small, Compact }

private data class ButtonColors(
    val background: Color,
    val content: Color,
    val border: Color?,
)

private fun DurtColors.forKind(kind: DurtButtonKind): ButtonColors =
    when (kind) {
        DurtButtonKind.Primary -> ButtonColors(ink, surface, null)

        // Text on accent is always ink (tokens.json, accent).
        DurtButtonKind.Accent -> ButtonColors(accent, ink, null)

        DurtButtonKind.Secondary -> ButtonColors(Color.Transparent, ink, ink)

        DurtButtonKind.Ghost -> ButtonColors(Color.Transparent, ink, null)

        DurtButtonKind.Danger -> ButtonColors(danger, surface, null)

        DurtButtonKind.DangerOutline -> ButtonColors(Color.Transparent, danger, danger)

        DurtButtonKind.Soft -> ButtonColors(surfaceMuted, ink, null)

        // "Şimdiden bitti" on the next-up card (2f).
        DurtButtonKind.Success -> ButtonColors(success, surface, null)

        DurtButtonKind.OnPoke -> ButtonColors(surface, pokeDeep, null)

        DurtButtonKind.OnPokeOutline -> ButtonColors(Color.Transparent, surface, surface)
    }

private fun DurtDimens.height(size: DurtButtonSize): Dp =
    when (size) {
        DurtButtonSize.Poke -> buttonPoke
        DurtButtonSize.Large -> buttonLarge
        DurtButtonSize.Medium -> buttonMedium
        DurtButtonSize.Small -> buttonSmall
        DurtButtonSize.Compact -> buttonCompact
    }

private fun DurtColors.background(
    base: ButtonColors,
    enabled: Boolean,
    pressed: Boolean,
): Color =
    when {
        !enabled -> border
        pressed && base.background != Color.Transparent -> base.background.pressed()
        pressed -> surfaceMuted
        else -> base.background
    }

/** Large and Poke have an 18 dp corner, the others 16 (docs/ux/components.md, 1). */
private fun DurtDimens.radius(size: DurtButtonSize): Dp =
    if (size == DurtButtonSize.Large || size == DurtButtonSize.Poke) radiusL else radiusM

private fun DurtType.forSize(size: DurtButtonSize): TextStyle =
    when (size) {
        DurtButtonSize.Poke -> buttonPoke
        DurtButtonSize.Large, DurtButtonSize.Medium -> button
        DurtButtonSize.Small, DurtButtonSize.Compact -> buttonSmall
    }

/**
 * The one button (docs/ux/components.md, 1). Full width by default; side by side buttons pass `Modifier.weight(1f)`.
 * Pressed darkens the background 8%, no ripple. Loading shows a spinner and blocks taps.
 */
@Composable
fun DurtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: DurtButtonKind = DurtButtonKind.Primary,
    size: DurtButtonSize = DurtButtonSize.Large,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val base = colors.forKind(kind)
    val background = colors.background(base, enabled, isPressed)
    val content = if (enabled) base.content else colors.textTertiary
    val shape = RoundedCornerShape(dimens.radius(size))
    val textStyle = DurtTheme.type.forSize(size)
    val borderModifier =
        if (base.border != null && enabled) {
            Modifier.border(BorderStroke(dimens.borderStrong, base.border), shape)
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = dimens.height(size))
                .clip(shape)
                .background(background)
                .then(borderModifier)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && !loading,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = dimens.space16, vertical = dimens.space8),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(dimens.spinner),
                color = content,
                strokeWidth = dimens.spinnerStroke,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(dimens.icon))
                    Box(Modifier.size(dimens.space10))
                }
                Text(text = text, style = textStyle, color = content, textAlign = TextAlign.Center)
            }
        }
    }
}

@PreviewComponent
@Composable
private fun DurtButtonPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space12)) {
            DurtButton("Birincil", onClick = {})
            DurtButton("Bir dene", onClick = {}, kind = DurtButtonKind.Accent)
            DurtButton("Hesabımı sil", onClick = {}, kind = DurtButtonKind.Danger)
            DurtButton("Kur", onClick = {}, enabled = false)
            DurtButton("Kur", onClick = {}, loading = true)
            Row(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space12)) {
                DurtButton("İkincil", {}, Modifier.weight(1f), DurtButtonKind.Secondary, DurtButtonSize.Medium)
                DurtButton("İptal et", {}, Modifier.weight(1f), DurtButtonKind.DangerOutline, DurtButtonSize.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space12)) {
                DurtButton("Yumuşak", {}, Modifier.weight(1f), DurtButtonKind.Soft, DurtButtonSize.Small)
                DurtButton("Vazgeç", {}, Modifier.weight(1f), DurtButtonKind.Ghost, DurtButtonSize.Small)
            }
        }
    }
}

@PreviewComponent
@Composable
private fun DurtButtonOnPokePreview() {
    PreviewSurface {
        Column(
            Modifier
                .background(DurtTheme.colors.poke)
                .padding(DurtTheme.dimens.space16),
            verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space12),
        ) {
            DurtButton(
                "Bitti",
                {},
                kind = DurtButtonKind.OnPoke,
                size = DurtButtonSize.Poke,
                leadingIcon = DurtIcons.Check,
            )
            DurtButton(
                "10 dk ertele · son hakkın",
                {},
                kind = DurtButtonKind.OnPokeOutline,
                size = DurtButtonSize.Medium,
            )
        }
    }
}
