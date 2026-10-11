package app.nuddle.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.nuddle.R
import app.nuddle.domain.TurkishPhone
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.BigTextField
import app.nuddle.ui.screens.FieldLabel
import app.nuddle.ui.screens.NoteCard
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.SignInHeader
import app.nuddle.ui.theme.DurtTheme

/** 0c. `national` holds the ten digits after +90. */
@Immutable
data class PhoneUiState(
    val national: String = "",
    val sending: Boolean = false,
    val failed: Boolean = false,
) {
    val canSend: Boolean get() = TurkishPhone.isValidMobile(national) && !sending
}

@Composable
fun PhoneScreen(
    state: PhoneUiState,
    onNumberChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier,
        bottom = {
            DurtButton(
                text = stringResource(R.string.phone_send_sms),
                onClick = onSend,
                enabled = state.canSend || state.sending,
                loading = state.sending,
            )
        },
    ) {
        BackBar(title = null, onBack = onBack)
        SignInHeader(stringResource(R.string.phone_title), stringResource(R.string.phone_subtitle))
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
            FieldLabel(stringResource(R.string.phone_label))
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.space10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountryBox()
                BigTextField(
                    value = state.national,
                    onValueChange = onNumberChange,
                    label = stringResource(R.string.phone_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (state.canSend) onSend() }),
                    visualTransformation = PhoneGrouping,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        NoteCard {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
                Box(
                    Modifier
                        .size(dimens.avatarCard)
                        .background(colors.successSoft, RoundedCornerShape(dimens.radiusS)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        DurtIcons.Shield,
                        contentDescription = null,
                        tint = colors.onSuccessSoft,
                        modifier = Modifier.size(dimens.icon),
                    )
                }
                Text(stringResource(R.string.phone_privacy), style = DurtTheme.type.body, color = colors.textBody)
            }
        }
        if (state.failed) {
            InlineAlert(stringResource(R.string.error_connection_title), stringResource(R.string.error_connection_body))
        }
    }
}

/** TR +90 only in the MVP; the picker arrives with other countries. Read together with the number field. */
@Composable
private fun CountryBox() {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusL)
    Row(
        modifier =
            Modifier
                .widthIn(min = dimens.countryButtonWidth)
                .heightIn(min = dimens.inputHeight)
                .background(colors.surface, shape)
                .border(dimens.borderStrong, colors.borderStrong, shape)
                .padding(horizontal = dimens.space14)
                .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space6),
    ) {
        Text(stringResource(R.string.phone_country), style = DurtTheme.type.button, color = colors.ink)
        Icon(
            DurtIcons.ChevronDown,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(dimens.iconSmall),
        )
    }
}

/** Shows "5321234567" as "532 123 45 67" while the state keeps digits only. */
private object PhoneGrouping : VisualTransformation {
    private val spacesAfter = intArrayOf(3, 6, 8)

    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = TurkishPhone.group(text.text)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset + spacesAfter.count { it < offset }

                // A space sits at transformed index `at + i` (the i-th space), present once the number is longer than `at`.
                override fun transformedToOriginal(offset: Int): Int {
                    val spaces =
                        spacesAfter
                            .withIndex()
                            .filter { (_, at) ->
                                text.length > at
                            }.map { (i, at) -> at + i }
                    return (offset - spaces.count { it < offset }).coerceIn(0, text.length)
                }
            }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

@PreviewScreens
@Composable
private fun PhoneScreenEmptyPreview() {
    DurtTheme { PhoneScreen(PhoneUiState(), onNumberChange = {}, onSend = {}, onBack = {}) }
}

@PreviewScreens
@Composable
private fun PhoneScreenFilledPreview() {
    DurtTheme { PhoneScreen(PhoneUiState(national = "5321234567"), onNumberChange = {}, onSend = {}, onBack = {}) }
}

@PreviewScreens
@Composable
private fun PhoneScreenFailedPreview() {
    DurtTheme {
        PhoneScreen(PhoneUiState(national = "5321234567", failed = true), onNumberChange = {}, onSend = {}, onBack = {})
    }
}
