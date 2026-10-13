package app.nuddle.ui.screens.name

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.Avatar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.BigTextField
import app.nuddle.ui.screens.FieldLabel
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.SignInHeader
import app.nuddle.ui.theme.DurtTheme

/** 0e. New people only. When invited by [invitedBy], the name is prefilled with what they typed ([invitedAs]). */
@Immutable
data class NameUiState(
    val name: String = "",
    val invitedBy: String? = null,
    val invitedAs: String? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    val canContinue: Boolean get() = name.isNotBlank() && !saving
}

@Composable
fun NameScreen(
    state: NameUiState,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier,
        bottom = {
            Text(
                text = stringResource(R.string.name_photo_note),
                style = DurtTheme.type.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            DurtButton(
                text = stringResource(R.string.name_continue),
                onClick = onContinue,
                enabled = state.canContinue || state.saving,
                loading = state.saving,
                modifier = Modifier.testTag(TestTags.NAME_CONTINUE),
            )
        },
    ) {
        SignInHeader(
            title = stringResource(R.string.name_title),
            subtitle = stringResource(R.string.name_subtitle),
            modifier = Modifier.padding(top = dimens.space48),
        )
        // Photo is optional and not in the MVP: initials only, the camera badge is decoration (screen 0e).
        Box(Modifier.fillMaxWidth().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Box {
                Avatar(state.name, colors.avatarSelf, dimens.avatarOnboarding)
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = dimens.space4, y = -dimens.space4)
                            .size(dimens.avatarBadge)
                            .border(dimens.borderFocused, colors.surface, CircleShape)
                            .padding(dimens.borderFocused)
                            .background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        DurtIcons.Camera,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(dimens.icon),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
            FieldLabel(stringResource(R.string.name_label))
            BigTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.name_label),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onContinue() }),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.NAME_INPUT),
            )
            if (state.invitedBy != null && state.invitedAs != null) {
                Text(
                    text = stringResource(R.string.name_invited_hint, state.invitedBy, state.invitedAs),
                    style = DurtTheme.type.body,
                    color = colors.textSecondary,
                )
            }
        }
        if (state.failed) {
            InlineAlert(stringResource(R.string.error_connection_title), stringResource(R.string.error_connection_body))
        }
    }
}

@PreviewScreens
@Composable
private fun NameScreenPreview() {
    DurtTheme { NameScreen(NameUiState(name = "Eren"), onNameChange = {}, onContinue = {}) }
}

@PreviewScreens
@Composable
private fun NameScreenInvitedPreview() {
    DurtTheme { NameScreen(NameUiState(name = "Eren", invitedBy = "Sibel", invitedAs = "Eren"), onNameChange = {}, onContinue = {}) }
}

@PreviewScreens
@Composable
private fun NameScreenEmptyPreview() {
    DurtTheme { NameScreen(NameUiState(), onNameChange = {}, onContinue = {}) }
}
