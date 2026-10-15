package app.nuddle.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.BigTextField
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime

/** What one settings sheet shows: the parts [DurtBottomSheet] takes. */
private class SheetParts(
    val title: String,
    val description: String?,
    val primary: SheetAction,
    val content: @Composable ColumnScope.() -> Unit,
)

@Composable
private fun partsOf(
    sheet: ProfileSheet,
    alone: Boolean,
    callbacks: ProfileSheetCallbacks,
): SheetParts {
    fun action(
        text: String,
        enabled: Boolean = true,
        destructive: Boolean = false,
    ) = SheetAction(text, callbacks.onConfirm, destructive = destructive, enabled = enabled && !sheet.busy, loading = sheet.busy)
    return when (sheet) {
        is ProfileSheet.Picture -> {
            SheetParts(
                title = stringResource(R.string.picture_title),
                description = stringResource(R.string.picture_body),
                primary = action(stringResource(R.string.picture_pick)),
            ) { PictureActions(sheet, callbacks) }
        }

        is ProfileSheet.Rename -> {
            SheetParts(
                title = stringResource(R.string.rename_title),
                description = null,
                primary = action(stringResource(R.string.action_save), enabled = sheet.name.isNotBlank()),
            ) { RenameField(sheet, callbacks) }
        }

        is ProfileSheet.Quiet -> {
            SheetParts(
                title = stringResource(R.string.quiet_title),
                description = stringResource(R.string.quiet_body),
                primary = action(stringResource(R.string.action_save), enabled = sheet.start != sheet.end),
            ) { QuietPicker(sheet, callbacks) }
        }

        is ProfileSheet.LogOut -> {
            SheetParts(
                title = stringResource(R.string.logout_title),
                description = stringResource(if (alone) R.string.logout_body_alone else R.string.logout_body),
                primary = action(stringResource(R.string.logout_confirm)),
            ) { LogOutNote(sheet) }
        }

        is ProfileSheet.Delete -> {
            SheetParts(
                title = stringResource(R.string.delete_title),
                description = null,
                primary = action(stringResource(R.string.delete_confirm), destructive = true),
            ) { DeletePoints(sheet, alone) }
        }
    }
}

/** The settings sheet on top of profile; [alone] when I have no connection (7h and 7i then name nobody). */
@Composable
fun ProfileSheetView(
    sheet: ProfileSheet,
    alone: Boolean,
    callbacks: ProfileSheetCallbacks,
) {
    val parts = partsOf(sheet, alone, callbacks)
    DurtBottomSheet(parts.title, parts.description, callbacks.onDismiss, parts.primary, content = parts.content)
}

@Composable
internal fun ColumnScope.Failed(sheet: ProfileSheet) {
    if (sheet.failed) InlineAlert(stringResource(R.string.profile_failed_title), stringResource(R.string.profile_failed_body))
}

/** Under "Galeriden seç": the way back to the initial, only while there is a picture (NUD-72). */
@Composable
private fun ColumnScope.PictureActions(
    sheet: ProfileSheet.Picture,
    callbacks: ProfileSheetCallbacks,
) {
    if (sheet.hasPicture) {
        TextLink(
            text = stringResource(R.string.picture_remove),
            onClick = callbacks.onRemovePicture,
            color = DurtTheme.colors.danger,
            style = DurtTheme.type.bodySmallStrong,
            modifier = Modifier.testTag(TestTags.PICTURE_REMOVE),
        )
    }
    Failed(sheet)
}

@Composable
private fun ColumnScope.RenameField(
    sheet: ProfileSheet.Rename,
    callbacks: ProfileSheetCallbacks,
) {
    BigTextField(
        value = sheet.name,
        onValueChange = callbacks.onNameChange,
        label = stringResource(R.string.name_label),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag(TestTags.RENAME_INPUT),
    )
    Failed(sheet)
}

@Composable
internal fun ProfileSheetPreviewOf(
    sheet: ProfileSheet,
    alone: Boolean = false,
) {
    DurtTheme {
        // The sheet is a ModalBottomSheet window; screenshots show its content over profile behind the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.background), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim))
            val parts = partsOf(sheet, alone, ProfileSheetCallbacks())
            DurtBottomSheetContent(parts.title, parts.description, onDismiss = {}, primary = parts.primary, content = parts.content)
        }
    }
}

@PreviewScreens
@Composable
private fun RenameSheetPreview() {
    ProfileSheetPreviewOf(ProfileSheet.Rename("Eren"))
}

@PreviewScreens
@Composable
private fun PictureSheetPreview() {
    ProfileSheetPreviewOf(ProfileSheet.Picture(hasPicture = true))
}
