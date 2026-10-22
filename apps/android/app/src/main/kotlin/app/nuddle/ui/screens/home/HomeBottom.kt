package app.nuddle.ui.screens.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ComposeBar
import app.nuddle.ui.components.ComposePhoto
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.ReplyNote
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

/** The undo toast, the done note or the reply note, 6b's alert while a send failed, and the compose bar. */
@Composable
internal fun ColumnScope.HomeBottom(
    state: HomeUiState,
    undo: UndoCallbacks,
    onComposeTextChange: (String) -> Unit,
    onComposeSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: (cancelled: Boolean) -> Unit,
    photo: ComposePhotoCallbacks,
) {
    val dimens = DurtTheme.dimens
    state.undo?.let { TaskUndoToast(it, undo, Modifier.padding(bottom = dimens.space10)) }
    state.doneNote?.let { TaskDoneNote(it, Modifier.padding(bottom = dimens.space10)) }
    state.reply?.let {
        ReplyNote(it, undo.onReplyTimeout, Modifier.padding(bottom = dimens.space10).testTag(TestTags.HOME_REPLY))
    }
    val compose = state.compose
    if (compose is ComposeState.Failed) {
        val until = compose.limitUntil
        InlineAlert(
            if (until != null) stringResource(R.string.compose_limit_title) else stringResource(R.string.compose_failed_title),
            if (until != null) stringResource(R.string.compose_limit_body, until) else stringResource(R.string.compose_failed_body),
            Modifier.padding(bottom = dimens.space10).testTag(TestTags.HOME_SEND_FAILED),
        )
    }
    ComposeBar(
        state.compose,
        onComposeTextChange,
        onComposeSend,
        onVoiceStart,
        onVoiceEnd,
        notice = state.voiceNotice,
        photo = ComposePhoto(state.composePhoto, photo.onAttach, photo.onRemove),
    )
}

/** The example photo in the compose bar (NUD-138): the gallery button and the ✕ of the attached picture. */
@Immutable
data class ComposePhotoCallbacks(
    val onAttach: () -> Unit = {},
    val onRemove: () -> Unit = {},
)

@PreviewScreens
@Composable
private fun HomeScreenPhotoPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Sibel",
                    compose = ComposeState.Typing("eren markete gitsin fotodakileri alsın"),
                    composePhoto = ImageBitmap(PREVIEW_PHOTO_W, PREVIEW_PHOTO_H),
                ),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}

private const val PREVIEW_PHOTO_W = 120
private const val PREVIEW_PHOTO_H = 160

@PreviewScreens
@Composable
private fun HomeScreenReplyPreview() {
    DurtTheme {
        HomeScreen(
            state =
                HomeUiState(
                    myName = "Eren",
                    reply = "Merhaba! Kime ne hatırlatayım? Mesela: Eren yarın 9'dan önce ekmek alsın.",
                ),
            onBell = {},
            onProfile = {},
            onInvite = {},
            onComposeTextChange = {},
            onComposeSend = {},
            onVoiceStart = {},
            onVoiceEnd = {},
        )
    }
}
