package app.nuddle.ui.screens.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

@Immutable
data class PhotoCallbacks(
    val onClose: () -> Unit = {},
    val onShoot: () -> Unit = {},
    val onRetake: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onDoneWithoutPhoto: () -> Unit = {},
)

/**
 * 4d: the in-app camera on a dark screen. The live [viewfinder] until the shot, then the shot with Gönder and
 * Tekrar çek. Without a camera, the plain Bitti fallback. Copy without suffixes after names: open-questions#35.
 */
@Composable
fun PhotoScreen(
    state: PhotoUiState,
    callbacks: PhotoCallbacks,
    viewfinder: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(colors.darkBackground)
            .safeDrawingPadding()
            .padding(horizontal = dimens.screenPaddingHorizontal, vertical = dimens.space16)
            .testTag(TestTags.PHOTO),
        verticalArrangement = Arrangement.spacedBy(dimens.space16),
    ) {
        Header(state, callbacks.onClose)
        val frame = RoundedCornerShape(dimens.radiusXl)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(frame)
                .background(colors.darkSurface),
            contentAlignment = Alignment.Center,
        ) {
            when (val stage = state.stage) {
                PhotoStage.Camera -> {
                    viewfinder(Modifier.fillMaxSize())
                }

                is PhotoStage.Review -> {
                    Image(
                        stage.image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PhotoStage.NoCamera -> {
                    NoCamera()
                }
            }
        }
        Actions(state, callbacks)
    }
}

@Composable
private fun Header(
    state: PhotoUiState,
    onClose: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
        val close = stringResource(R.string.photo_close)
        Box(
            Modifier
                .size(dimens.minTouchTarget)
                .clip(CircleShape)
                .background(colors.darkSurface)
                .clickable(onClickLabel = close, role = Role.Button, onClick = onClose)
                .testTag(TestTags.PHOTO_CLOSE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DurtIcons.Close, contentDescription = close, tint = colors.darkText, modifier = Modifier.size(dimens.icon))
        }
        Column(Modifier.weight(1f)) {
            Text(state.title, style = DurtTheme.type.bodyStrong, color = colors.darkText, modifier = Modifier.semantics { heading() })
            val line =
                when {
                    state.toName == null -> state.detail
                    state.detail == null -> stringResource(R.string.photo_to, state.toName)
                    else -> stringResource(R.string.photo_to_detail, state.toName, state.detail)
                }
            line?.let { Text(it, style = DurtTheme.type.caption, color = colors.darkTextSecondary) }
        }
    }
}

@Composable
private fun ColumnScope.Actions(
    state: PhotoUiState,
    callbacks: PhotoCallbacks,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    when (state.stage) {
        PhotoStage.Camera -> {
            val shoot = stringResource(R.string.photo_shoot)
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(dimens.buttonPoke)
                    .clip(CircleShape)
                    .border(dimens.borderStrong * 2, colors.darkText, CircleShape)
                    .padding(dimens.space6)
                    .clip(CircleShape)
                    .background(if (state.busy) colors.darkTextSecondary else colors.darkText)
                    .clickable(enabled = !state.busy, onClickLabel = shoot, role = Role.Button, onClick = callbacks.onShoot)
                    .testTag(TestTags.PHOTO_SHOOT),
            )
        }

        is PhotoStage.Review -> {
            Text(
                stringResource(if (state.toName == null) R.string.photo_privacy_self else R.string.photo_privacy, state.toName.orEmpty()),
                style = DurtTheme.type.bodySmall,
                color = colors.darkTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            DurtButton(
                text = stringResource(R.string.photo_send),
                onClick = callbacks.onSend,
                kind = DurtButtonKind.Accent,
                loading = state.busy,
                modifier = Modifier.testTag(TestTags.PHOTO_SEND),
            )
            DurtButton(
                text = stringResource(R.string.photo_retake),
                onClick = callbacks.onRetake,
                kind = DurtButtonKind.OnPokeOutline,
                enabled = !state.busy,
                modifier = Modifier.testTag(TestTags.PHOTO_RETAKE),
            )
        }

        PhotoStage.NoCamera -> {
            DurtButton(
                text = stringResource(R.string.photo_done_without),
                onClick = callbacks.onDoneWithoutPhoto,
                kind = DurtButtonKind.Accent,
                loading = state.busy,
                modifier = Modifier.testTag(TestTags.PHOTO_DONE_WITHOUT),
            )
        }
    }
}

@Composable
private fun NoCamera() {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        Modifier.padding(dimens.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space8),
    ) {
        Icon(DurtIcons.Camera, contentDescription = null, tint = colors.darkTextSecondary, modifier = Modifier.size(dimens.iconHero))
        Text(
            stringResource(R.string.photo_no_camera_title),
            style = DurtTheme.type.bodyStrong,
            color = colors.darkText,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.photo_no_camera_body),
            style = DurtTheme.type.bodySmall,
            color = colors.darkTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewState = PhotoUiState(title = "Temizlikçi için para çek", toName = "Selin", detail = "1.000 TL")

@Composable
private fun PreviewViewfinder(modifier: Modifier = Modifier) {
    Box(modifier.background(DurtTheme.colors.darkBorder))
}

@PreviewScreens
@Composable
private fun PhotoScreenCameraPreview() {
    DurtTheme { PhotoScreen(previewState, PhotoCallbacks(), viewfinder = { PreviewViewfinder(it) }) }
}

@PreviewScreens
@Composable
private fun PhotoScreenNoCameraPreview() {
    DurtTheme { PhotoScreen(previewState.copy(stage = PhotoStage.NoCamera), PhotoCallbacks(), viewfinder = { PreviewViewfinder(it) }) }
}

@PreviewScreens
@Composable
private fun PhotoScreenReviewPreview() {
    DurtTheme {
        PhotoScreen(
            previewState.copy(stage = PhotoStage.Review(ImageBitmap(PREVIEW_SHOT_WIDTH, PREVIEW_SHOT_HEIGHT))),
            PhotoCallbacks(),
            viewfinder = { PreviewViewfinder(it) },
        )
    }
}

private const val PREVIEW_SHOT_WIDTH = 300
private const val PREVIEW_SHOT_HEIGHT = 400
