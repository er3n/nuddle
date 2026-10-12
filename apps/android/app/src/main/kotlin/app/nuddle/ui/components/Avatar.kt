package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.theme.SpaceGrotesk
import app.nuddle.ui.uppercaseForUi

private const val INITIAL_SCALE = 0.4f

/**
 * First letter on a colored circle, Space Grotesk 700 at about 40% of the size (docs/ux/components.md, 9).
 * No photos in the MVP. The user's own avatar is always `ink`. Decorative: callers describe the person.
 */
@Composable
fun Avatar(
    name: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val initial = name.trim().take(1).uppercaseForUi()
    // The initial scales with the circle, not with the font scale: it must always fit.
    val fontSize = with(LocalDensity.current) { (size * INITIAL_SCALE).toSp() }
    Box(
        modifier =
            modifier
                .size(size)
                .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = DurtTheme.colors.surface,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        )
    }
}

@PreviewComponent
@Composable
private fun AvatarPreview() {
    PreviewSurface {
        val dimens = DurtTheme.dimens
        val selin = DurtTheme.colors.avatarOthers.first()
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar("Eren", DurtTheme.colors.avatarSelf, dimens.avatarProfile)
            Avatar("Selin", selin, dimens.avatarTopBar)
            Avatar("Selin", selin, dimens.avatarList)
            Avatar("Selin", selin, dimens.avatarCard)
            Avatar("Selin", selin, dimens.avatarInline)
            Avatar("Eren", DurtTheme.colors.avatarSelf, dimens.avatarSmall)
        }
    }
}
