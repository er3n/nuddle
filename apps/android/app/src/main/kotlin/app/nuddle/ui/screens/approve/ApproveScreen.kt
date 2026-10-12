package app.nuddle.ui.screens.approve

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.NoteField
import app.nuddle.ui.components.PillStatus
import app.nuddle.ui.components.StatusPill
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

@Immutable
data class ApproveCallbacks(
    val onBack: () -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onApprove: () -> Unit = {},
    val onReRequest: () -> Unit = {},
    val onRetryPhoto: () -> Unit = {},
)

/**
 * 4f: the sender looks at the photo and approves it, or asks again with an optional note. A forgotten approval
 * closes by itself after 3 hours (docs/product.md, Photo and approval). Copy: open-questions#35.
 */
@Composable
fun ApproveScreen(
    state: ApproveUiState,
    callbacks: ApproveCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.APPROVE),
        bottom = { Answers(state, callbacks) },
    ) {
        BackBar(title = null, onBack = callbacks.onBack, trailing = { StatusPill(PillStatus.AwaitingMyApproval) })
        Text(state.title, style = DurtTheme.type.h1, color = colors.ink, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space10), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clearAndSetSemantics { }) { Avatar(state.fromName, colors.ink, dimens.avatarInline) }
            val parts =
                listOfNotNull(
                    stringResource(R.string.approve_from, state.fromName),
                    state.sentAt,
                    state.detail?.let { stringResource(R.string.approve_asked, it) },
                )
            Text(
                parts.reduce { line, part -> stringResource(R.string.separator_dot, line, part) },
                style = DurtTheme.type.bodySmall,
                color = colors.textSecondary,
            )
        }
        Photo(state.photo, state.fromName, callbacks.onRetryPhoto)
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.approve_note_label))
                append(' ')
                withStyle(SpanStyle(color = colors.textSecondary)) { append(stringResource(R.string.approve_note_optional)) }
            },
            style = DurtTheme.type.bodySmallStrong,
            color = colors.ink,
        )
        NoteField(
            note = state.note,
            onChange = callbacks.onNoteChange,
            placeholder = stringResource(R.string.approve_note_placeholder),
            modifier = Modifier.testTag(TestTags.APPROVE_NOTE),
        )
    }
}

@Composable
private fun Photo(
    photo: PhotoView,
    fromName: String,
    onRetry: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    val description = stringResource(R.string.approve_photo_description, fromName)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_ASPECT)
            .clip(shape)
            .background(colors.surfaceMuted)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (photo) {
            PhotoView.Loading -> {
                CircularProgressIndicator(
                    color = colors.textMuted,
                    strokeWidth = dimens.spinnerStroke,
                    modifier = Modifier.size(dimens.iconHero),
                )
            }

            is PhotoView.Shown -> {
                Image(photo.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }

            PhotoView.Failed -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
                    Text(stringResource(R.string.approve_photo_failed), style = DurtTheme.type.body, color = colors.textSecondary)
                    TextLink(stringResource(R.string.compose_retry), onRetry, modifier = Modifier.testTag(TestTags.APPROVE_RETRY_PHOTO))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Answers(
    state: ApproveUiState,
    callbacks: ApproveCallbacks,
) {
    val dimens = DurtTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space10)) {
        DurtButton(
            text = stringResource(R.string.approve_rerequest),
            onClick = callbacks.onReRequest,
            kind = DurtButtonKind.Secondary,
            enabled = !state.sending,
            modifier = Modifier.weight(REREQUEST_WEIGHT).testTag(TestTags.APPROVE_REREQUEST),
        )
        DurtButton(
            text = stringResource(R.string.approve_ok),
            onClick = callbacks.onApprove,
            kind = DurtButtonKind.Success,
            loading = state.sending,
            modifier = Modifier.weight(APPROVE_WEIGHT).testTag(TestTags.APPROVE_OK),
        )
    }
    Text(
        stringResource(R.string.approve_auto),
        style = DurtTheme.type.caption,
        color = DurtTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The canvas: a portrait photo frame, and Onayla twice as wide as Tekrar iste. */
private const val PHOTO_ASPECT = 0.92f
private const val REREQUEST_WEIGHT = 1f
private const val APPROVE_WEIGHT = 2f

private val previewState =
    ApproveUiState(title = "Temizlikçi için para çek", fromName = "Eren", sentAt = "08:24", detail = "1.000 TL")

@PreviewScreens
@Composable
private fun ApproveScreenPreview() {
    DurtTheme { ApproveScreen(previewState.copy(photo = PhotoView.Shown(ImageBitmap(PREVIEW_W, PREVIEW_H))), ApproveCallbacks()) }
}

@PreviewScreens
@Composable
private fun ApproveScreenLoadingPreview() {
    DurtTheme { ApproveScreen(previewState.copy(note = "1.500 lazımdı"), ApproveCallbacks()) }
}

@PreviewScreens
@Composable
private fun ApproveScreenFailedPreview() {
    DurtTheme { ApproveScreen(previewState.copy(photo = PhotoView.Failed), ApproveCallbacks()) }
}

private const val PREVIEW_W = 300
private const val PREVIEW_H = 400
