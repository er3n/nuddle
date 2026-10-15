package app.nuddle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

/** The key of my own picture in [LocalAvatars]; connections are keyed by their person id. */
const val MY_AVATAR = "me"

/**
 * The profile pictures the phone holds, by person id (NUD-72). Provided once at the root from the cached files;
 * previews and tests leave it empty, which shows the initials.
 */
val LocalAvatars = staticCompositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }

/** [Avatar] with the person's picture when [LocalAvatars] has one for [personId], else their initial. */
@Composable
fun PersonAvatar(
    personId: String?,
    name: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    letterColor: Color = DurtTheme.colors.surface,
) {
    val image = personId?.let { LocalAvatars.current[it] }
    Avatar(name, color, size, modifier, letterColor, image)
}

@PreviewComponent
@Composable
private fun PersonAvatarPreview() {
    PreviewSurface {
        val dimens = DurtTheme.dimens
        val pictures = mapOf("sibel" to ImageBitmap(PREVIEW_PICTURE, PREVIEW_PICTURE))
        CompositionLocalProvider(LocalAvatars provides pictures) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12), verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar("sibel", "Sibel", DurtTheme.colors.avatarOthers.first(), dimens.avatarList)
                PersonAvatar("eren", "Eren", DurtTheme.colors.avatarSelf, dimens.avatarList)
                PersonAvatar(null, "Bahar", DurtTheme.colors.avatarOthers.first(), dimens.avatarList)
            }
        }
    }
}

private const val PREVIEW_PICTURE = 48
