package app.nuddle.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.components.DurtBottomSheet
import app.nuddle.ui.components.DurtBottomSheetContent
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.SheetAction
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

/** "Diğer cihazlardan çıkılsın mı?" (5e): Çıkış yap is Danger, as on 7h and 7i. */
@Composable
fun SignOutOthersSheetView(
    sheet: SignOutOthersSheet,
    callbacks: DevicesCallbacks,
) {
    DurtBottomSheet(
        stringResource(R.string.devices_confirm_title),
        stringResource(R.string.devices_confirm_body),
        callbacks.onDismiss,
        confirmAction(sheet, callbacks),
    ) { Failed(sheet) }
}

@Composable
private fun confirmAction(
    sheet: SignOutOthersSheet,
    callbacks: DevicesCallbacks,
) = SheetAction(
    stringResource(R.string.devices_confirm),
    callbacks.onConfirm,
    destructive = true,
    enabled = !sheet.busy,
    loading = sheet.busy,
)

@Composable
private fun Failed(sheet: SignOutOthersSheet) {
    if (sheet.failed) InlineAlert(stringResource(R.string.profile_failed_title), stringResource(R.string.profile_failed_body))
}

@Composable
private fun ConfirmSheetPreviewOf(sheet: SignOutOthersSheet) {
    DurtTheme {
        // The sheet is a ModalBottomSheet window; screenshots show its content over the screen behind the scrim.
        Box(Modifier.fillMaxSize().background(DurtTheme.colors.background), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().background(DurtTheme.colors.scrim))
            DurtBottomSheetContent(
                stringResource(R.string.devices_confirm_title),
                stringResource(R.string.devices_confirm_body),
                onDismiss = {},
                primary = confirmAction(sheet, DevicesCallbacks()),
            ) { Failed(sheet) }
        }
    }
}

@PreviewScreens
@Composable
private fun DevicesConfirmSheetPreview() {
    ConfirmSheetPreviewOf(SignOutOthersSheet())
}

@PreviewScreens
@Composable
private fun DevicesConfirmSheetBusyPreview() {
    ConfirmSheetPreviewOf(SignOutOthersSheet(busy = true))
}

@PreviewScreens
@Composable
private fun DevicesConfirmSheetFailedPreview() {
    ConfirmSheetPreviewOf(SignOutOthersSheet(failed = true))
}
