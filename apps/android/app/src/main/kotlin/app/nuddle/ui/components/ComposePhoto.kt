package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

/** The example photo in the compose bar (NUD-138, screens.md 2a): the attached picture, and what the gallery button and ✕ do. */
@Immutable
data class ComposePhoto(
    val image: ImageBitmap? = null,
    val onAttach: () -> Unit = {},
    val onRemove: () -> Unit = {},
)

/** A picture can be picked while the bar is idle, being typed in or kept after a failure; not while sending or dictating. */
internal fun ComposePhoto?.attachable(state: ComposeState): Boolean =
    this != null && (state is ComposeState.Idle || state is ComposeState.Typing || state is ComposeState.Failed)

/** Opens the system photo picker for the example photo (2a). */
@Composable
internal fun GalleryButton(onAttach: () -> Unit) {
    val description = stringResource(R.string.compose_attach_photo)
    Box(
        modifier =
            Modifier
                .size(DurtTheme.dimens.minTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onAttach)
                .semantics { contentDescription = description }
                .testTag(TestTags.COMPOSE_ATTACH),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            DurtIcons.Gallery,
            contentDescription = null,
            tint = DurtTheme.colors.textMuted,
            modifier = Modifier.size(DurtTheme.dimens.iconLarge),
        )
    }
}

/** The attached picture above the bar, with ✕ to take it off before the task goes out; nothing without one. */
@Composable
internal fun AttachedPhoto(
    photo: ComposePhoto?,
    modifier: Modifier = Modifier,
) {
    val image = photo?.image ?: return
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val description = stringResource(R.string.compose_remove_photo)
    Row(
        modifier = modifier.testTag(TestTags.COMPOSE_PHOTO),
        horizontalArrangement = Arrangement.spacedBy(dimens.space4),
        verticalAlignment = Alignment.Top,
    ) {
        ExamplePhotoThumb(image)
        Box(
            modifier =
                Modifier
                    .size(dimens.minTouchTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = photo.onRemove)
                    .semantics { contentDescription = description }
                    .testTag(TestTags.COMPOSE_PHOTO_REMOVE),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(dimens.iconLarge).background(colors.ink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(DurtIcons.Close, contentDescription = null, tint = colors.surface, modifier = Modifier.size(dimens.iconSmall))
            }
        }
    }
}

private const val PREVIEW_W = 120
private const val PREVIEW_H = 160

@PreviewComponent
@Composable
private fun AttachedPhotoPreview() {
    PreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            GalleryButton {}
            AttachedPhoto(ComposePhoto(ImageBitmap(PREVIEW_W, PREVIEW_H)))
        }
    }
}
