package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

data class SheetAction(
    val text: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val loading: Boolean = false,
)

/**
 * Fixed order: handle, `h2` title, description, content, 56 dp primary action, 44 dp "Vazgeç"
 * (docs/ux/components.md, 8). Swipe down and a scrim tap mean Vazgeç. Destructive primaries use `Danger`.
 */
@OptIn(ExperimentalMaterial3Api::class) // Reason: ModalBottomSheet is still experimental in Material 3.
@Composable
fun DurtBottomSheet(
    title: String,
    description: String?,
    onDismiss: () -> Unit,
    primary: SheetAction,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val dimens = DurtTheme.dimens
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // Always fully open: half open would hide the primary action under a tall sheet (2b).
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = dimens.radiusSheet, topEnd = dimens.radiusSheet),
        containerColor = DurtTheme.colors.surface,
        scrimColor = DurtTheme.colors.scrim,
        dragHandle = null,
    ) {
        DurtBottomSheetContent(title, description, onDismiss, primary, content = content)
    }
}

/** The sheet's body without the modal window, so it can be previewed and screenshot tested. */
@Composable
fun DurtBottomSheetContent(
    title: String,
    description: String?,
    onDismiss: () -> Unit,
    primary: SheetAction,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    colors.surface,
                    RoundedCornerShape(topStart = dimens.radiusSheet, topEnd = dimens.radiusSheet),
                ).padding(start = dimens.space20, end = dimens.space20, top = dimens.space12, bottom = dimens.space24),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(dimens.sheetHandleWidth, dimens.sheetHandleHeight)
                .background(colors.borderStrong, CircleShape),
        )
        Text(title, style = DurtTheme.type.h2, color = colors.ink)
        if (description != null) Text(description, style = DurtTheme.type.body, color = colors.textSecondary)
        content()
        DurtButton(
            text = primary.text,
            onClick = primary.onClick,
            kind = if (primary.destructive) DurtButtonKind.Danger else DurtButtonKind.Primary,
            enabled = primary.enabled,
            loading = primary.loading,
            modifier = Modifier.testTag(TestTags.SHEET_PRIMARY),
        )
        DurtButton(
            text = stringResource(R.string.action_dismiss),
            onClick = onDismiss,
            kind = DurtButtonKind.Ghost,
            size = DurtButtonSize.Small,
            modifier = Modifier.testTag(TestTags.SHEET_DISMISS),
        )
    }
}

@PreviewComponent
@Composable
private fun DurtBottomSheetPreview() {
    PreviewSurface {
        DurtBottomSheetContent(
            title = "Görev iptal edilsin mi?",
            description = "Dürtmeler hemen durur.",
            onDismiss = {},
            primary = SheetAction("İptal et", {}, destructive = true),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space8)) {
                ChoiceChip("Ben hallettim", selected = false, onClick = {})
                ChoiceChip("Gerek kalmadı", selected = false, onClick = {})
            }
        }
    }
}
