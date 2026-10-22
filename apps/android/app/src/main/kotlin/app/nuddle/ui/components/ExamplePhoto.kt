package app.nuddle.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

// The sender's example photo (NUD-138, docs/ux/screens.md 2a, task detail, 3b). Never the recipient's photo.

/**
 * The picture as a 64 dp rounded thumbnail; a tap opens it full screen ([ExamplePhotoViewer]). [border] for a dark
 * background (3b).
 */
@Composable
fun ExamplePhotoThumb(
    image: ImageBitmap,
    modifier: Modifier = Modifier,
    border: Color? = null,
) {
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusS)
    val description = stringResource(R.string.example_photo_open)
    var viewing by remember { mutableStateOf(false) }
    Image(
        image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(dimens.photoThumb)
                .clip(shape)
                .background(DurtTheme.colors.surfaceMuted)
                .then(if (border != null) Modifier.border(dimens.borderCard, border, shape) else Modifier)
                .clickable(role = Role.Button) { viewing = true }
                .semantics { contentDescription = description }
                .testTag(TestTags.EXAMPLE_PHOTO),
    )
    if (viewing) ExamplePhotoViewer(image) { viewing = false }
}

/** The picture full screen on `ink`; a tap anywhere, the close button or back closes it. */
@Composable
private fun ExamplePhotoViewer(
    image: ImageBitmap,
    onClose: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.ink)
                .clickable(role = Role.Button, onClick = onClose)
                .testTag(TestTags.EXAMPLE_PHOTO_VIEWER),
        ) {
            Image(
                image,
                contentDescription = stringResource(R.string.example_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            )
            CloseButton(onClose, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(dimens.space8))
        }
    }
}

@Composable
private fun CloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val description = stringResource(R.string.photo_close)
    Box(
        modifier =
            modifier
                .size(dimens.minTouchTarget)
                .clip(CircleShape)
                .background(colors.toastBackground)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = description }
                .testTag(TestTags.EXAMPLE_PHOTO_CLOSE),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DurtIcons.Close, contentDescription = null, tint = colors.surface, modifier = Modifier.size(dimens.icon))
    }
}

/** The example photo on the task detail: the thumbnail, "Ekli fotoğraf" and, on a task to me, who added it. */
@Composable
fun ExamplePhotoRow(
    image: ImageBitmap,
    from: String?,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExamplePhotoThumb(image)
        Column {
            Text(stringResource(R.string.example_photo), style = DurtTheme.type.body, color = colors.ink)
            from?.let {
                Text(
                    stringResource(R.string.example_photo_from, it),
                    style = DurtTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

private const val PREVIEW_W = 120
private const val PREVIEW_H = 90

@PreviewComponent
@Composable
private fun ExamplePhotoRowPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            ExamplePhotoRow(ImageBitmap(PREVIEW_W, PREVIEW_H), from = "Sibel")
            ExamplePhotoRow(ImageBitmap(PREVIEW_W, PREVIEW_H), from = null)
            ExamplePhotoThumb(ImageBitmap(PREVIEW_W, PREVIEW_H), border = DurtTheme.colors.onPokeSoft)
        }
    }
}
