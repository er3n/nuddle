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
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.BigTextField
import app.nuddle.ui.theme.DurtTheme
import kotlinx.datetime.LocalTime

@Composable
internal fun ColumnScope.LogOutNote(sheet: ProfileSheet.LogOut) {
    val dimens = DurtTheme.dimens
    Text(
        stringResource(R.string.logout_kept),
        style = DurtTheme.type.caption,
        color = DurtTheme.colors.textSecondary,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(DurtTheme.colors.background, RoundedCornerShape(dimens.radiusS))
                .padding(horizontal = dimens.space16, vertical = dimens.space12),
    )
    Failed(sheet)
}

/** 7i: what goes, one point each; the last is bold. Without a connection nobody's tasks are cancelled. */
@Composable
internal fun ColumnScope.DeletePoints(
    sheet: ProfileSheet.Delete,
    alone: Boolean,
) {
    val points =
        listOfNotNull(
            R.string.delete_point_data,
            R.string.delete_point_others.takeUnless { alone },
            R.string.delete_point_learning,
        )
    points.forEach { Point(stringResource(it), strong = false) }
    Point(stringResource(R.string.delete_point_final), strong = true)
    Failed(sheet)
}

@Composable
private fun Point(
    text: String,
    strong: Boolean,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
        Box(Modifier.padding(top = dimens.space8).size(dimens.space8).background(colors.danger, CircleShape))
        Text(text, style = if (strong) DurtTheme.type.bodyStrong else DurtTheme.type.body, color = colors.ink)
    }
}

@PreviewScreens
@Composable
private fun LogOutSheetPreview() {
    ProfileSheetPreviewOf(ProfileSheet.LogOut())
}

@PreviewScreens
@Composable
private fun LogOutSheetAloneBusyPreview() {
    ProfileSheetPreviewOf(ProfileSheet.LogOut(busy = true), alone = true)
}

@PreviewScreens
@Composable
private fun DeleteSheetPreview() {
    ProfileSheetPreviewOf(ProfileSheet.Delete())
}

@PreviewScreens
@Composable
private fun DeleteSheetAloneFailedPreview() {
    ProfileSheetPreviewOf(ProfileSheet.Delete(failed = true), alone = true)
}
