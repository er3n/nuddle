package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.nuddle.R
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

const val CODE_LENGTH = 6

/**
 * Six boxes over one text field (docs/ux/components.md, 9). The active box has a 2 dp `ink` border; on error every
 * box gets a `poke` border and `highlightSoft` fill. The field is marked as an SMS code so the keyboard offers the
 * code from the message (screen 0d hint); [onComplete] fires on the sixth digit.
 */
@Composable
fun CodeInput(
    value: String,
    error: Boolean,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = CODE_LENGTH,
    enabled: Boolean = true,
    focusRequester: FocusRequester = FocusRequester(),
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val label = stringResource(R.string.code_input_label)
    val errorText = stringResource(R.string.code_wrong_error)
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(length)
            if (digits != value) {
                onValueChange(digits)
                if (digits.length == length) onComplete(digits)
            }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics {
                    contentDescription = label
                    contentType = ContentType.SmsOtpCode
                    if (error) error(errorText)
                },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.codeBoxGap)) {
                repeat(length) { index ->
                    val digit = value.getOrNull(index)
                    val active = !error && index == value.length
                    val border: Color =
                        when {
                            error -> colors.poke
                            active -> colors.ink
                            digit != null -> colors.textMuted
                            else -> colors.borderStrong
                        }
                    val shape = RoundedCornerShape(dimens.radiusCode)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = dimens.codeBoxHeight)
                                .background(if (error) colors.highlightSoft else colors.surface, shape)
                                .border(
                                    if (active ||
                                        error
                                    ) {
                                        dimens.borderFocused
                                    } else {
                                        dimens.borderCard
                                    },
                                    border,
                                    shape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit?.toString() ?: "", style = DurtTheme.type.codeDigit, color = colors.ink)
                    }
                }
            }
        },
    )
}

@PreviewComponent
@Composable
private fun CodeInputPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            CodeInput(value = "481", error = false, onValueChange = {}, onComplete = {})
            CodeInput(value = "481927", error = true, onValueChange = {}, onComplete = {})
        }
    }
}
