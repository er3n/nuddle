package app.nuddle.ui.screens.setup

import androidx.compose.runtime.Composable
import app.nuddle.domain.SetupStep
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.theme.DurtTheme

/** 2e on a Samsung: the battery step last, active with "İzin ver" (NUD-75). */
@PreviewScreens
@Composable
private fun PermissionsScreenBatteryPreview() {
    DurtTheme {
        PermissionsScreen(
            SetupUiState.Steps(
                settingsCount = 3,
                notificationsGranted = true,
                steps =
                    listOf(
                        PermissionStepUi(SetupStep.NOTIFICATIONS, granted = true, skipped = false),
                        PermissionStepUi(SetupStep.EXACT_ALARM, granted = true, skipped = false),
                        PermissionStepUi(SetupStep.FULL_SCREEN, granted = true, skipped = false),
                        PermissionStepUi(SetupStep.BATTERY, granted = false, skipped = false),
                    ),
                active = SetupStep.BATTERY,
            ),
            onOpenActive = {},
            onSkipActive = {},
        )
    }
}
