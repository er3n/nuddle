package app.nuddle.ui.screens.poke

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.ChoiceChip
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.NoteField
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

/** The ready notes of 7f; one at a time, and a written note wins over it (open-questions#36). */
enum class DropReason(
    @param:StringRes val label: Int,
) {
    NoTime(R.string.drop_reason_no_time),
    Tomorrow(R.string.drop_reason_tomorrow),
    CouldNotGoOut(R.string.drop_reason_could_not_go_out),
}

/** 7f over the full screen poke: who the note is for, the chosen chip and what is written. */
@Immutable
data class DropUi(
    val senderName: String,
    val reason: DropReason? = null,
    val note: String = "",
)

data class DropCallbacks(
    val onReason: (DropReason) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    /** Closes the task as dropped with this note, or null for "Notsuz kapat". */
    val onDrop: (String?) -> Unit = {},
    /** Back, swipe or a tap on the scrim: the poke stays. */
    val onDismiss: () -> Unit = {},
)

/** The note the sender will read: the written one, else the chip; null when there is neither. */
@Composable
private fun DropUi.noteText(): String? = note.trim().ifEmpty { null } ?: reason?.let { stringResource(it.label) }

@Composable
private fun sendAction(
    state: DropUi,
    callbacks: DropCallbacks,
): SheetAction {
    val note = state.noteText()
    return SheetAction(stringResource(R.string.drop_send_note), { callbacks.onDrop(note) }, enabled = note != null)
}

@Composable
private fun withoutNoteAction(callbacks: DropCallbacks) =
    SheetAction(stringResource(R.string.drop_without_note), { callbacks.onDrop(null) })

/** 7f: "Tamam, bugün olmuyor", three ready notes or a written one, "Notla gönder" and "Notsuz kapat". */
@Composable
fun DropSheet(
    state: DropUi,
    callbacks: DropCallbacks,
) {
    DurtBottomSheet(
        title = stringResource(R.string.drop_title),
        description = stringResource(R.string.drop_body, state.senderName),
        onDismiss = callbacks.onDismiss,
        primary = sendAction(state, callbacks),
        secondary = withoutNoteAction(callbacks),
    ) { DropNote(state, callbacks) }
}

@OptIn(ExperimentalLayoutApi::class) // Reason: FlowRow is the layout for wrapping chips; no stable alternative.
@Composable
private fun ColumnScope.DropNote(
    state: DropUi,
    callbacks: DropCallbacks,
) {
    val dimens = DurtTheme.dimens
    FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.space10), verticalArrangement = Arrangement.spacedBy(dimens.space10)) {
        DropReason.entries.forEach { reason ->
            ChoiceChip(
                stringResource(reason.label),
                selected = reason == state.reason,
                onClick = { callbacks.onReason(reason) },
                modifier = Modifier.testTag(TestTags.dropReason(reason.name)),
            )
        }
    }
    NoteField(
        note = state.note,
        onChange = callbacks.onNoteChange,
        placeholder = stringResource(R.string.drop_note_placeholder),
        modifier = Modifier.testTag(TestTags.DROP_NOTE),
    )
    Text(stringResource(R.string.drop_hint), style = DurtTheme.type.caption, color = DurtTheme.colors.textSecondary)
}

@Composable
private fun DropSheetPreviewOf(state: DropUi) {
    DurtTheme {
        // The sheet is a ModalBottomSheet window; screenshots show its content over the poke behind the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.poke), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim))
            DurtBottomSheetContent(
                title = stringResource(R.string.drop_title),
                description = stringResource(R.string.drop_body, state.senderName),
                onDismiss = {},
                primary = sendAction(state, DropCallbacks()),
                secondary = withoutNoteAction(DropCallbacks()),
            ) { DropNote(state, DropCallbacks()) }
        }
    }
}

@PreviewScreens
@Composable
private fun DropSheetPreview() {
    DropSheetPreviewOf(DropUi(senderName = "Selin", reason = DropReason.Tomorrow))
}

@PreviewScreens
@Composable
private fun DropSheetEmptyPreview() {
    DropSheetPreviewOf(DropUi(senderName = "Selin"))
}
