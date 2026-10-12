package app.nuddle.ui.screens.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/**
 * 6e: the upload failed, the photo is kept on the phone and is retried when there is a connection; the deadline
 * counts from the send attempt (docs/product.md, Errors). Copy without a suffix after the name: open-questions#35.
 */
@Composable
fun UnsentPhotoScreen(
    state: UnsentPhotoUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.UNSENT_PHOTO),
        bottom = {
            DurtButton(
                text = stringResource(R.string.unsent_retry),
                onClick = onRetry,
                kind = DurtButtonKind.Accent,
                modifier = Modifier.testTag(TestTags.UNSENT_RETRY),
            )
        },
    ) {
        BackBar(title = stringResource(R.string.unsent_title), onBack = onBack)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PHOTO_ASPECT)
                .clip(RoundedCornerShape(dimens.radiusXl))
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.BottomCenter,
        ) {
            state.photo?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Text(
                stringResource(R.string.unsent_kept),
                style = DurtTheme.type.bodySmall,
                color = colors.darkText,
                modifier =
                    Modifier
                        .padding(dimens.space12)
                        .fillMaxWidth()
                        .background(colors.toastBackground, RoundedCornerShape(dimens.radiusS))
                        .padding(horizontal = dimens.space12, vertical = dimens.space10),
            )
        }
        InlineAlert(stringResource(R.string.unsent_title_alert, state.toName), stringResource(R.string.unsent_body))
    }
}

/** The canvas: a portrait photo frame. */
private const val PHOTO_ASPECT = 0.84f

@PreviewScreens
@Composable
private fun UnsentPhotoScreenPreview() {
    DurtTheme { UnsentPhotoScreen(UnsentPhotoUiState("Selin", ImageBitmap(PREVIEW_W, PREVIEW_H)), onBack = {}, onRetry = {}) }
}

private const val PREVIEW_W = 300
private const val PREVIEW_H = 400
