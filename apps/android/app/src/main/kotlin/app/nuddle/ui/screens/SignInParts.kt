package app.nuddle.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.nuddle.domain.TurkishPhone
import app.nuddle.ui.theme.DurtTheme

/** Title and sub line of the sign-in screens (0c to 0e): `onboardingTitle` from the canvas. */
@Composable
fun SignInHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space8)) {
        Text(
            text = title,
            style = DurtTheme.type.onboardingTitle,
            color = DurtTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) Text(subtitle, style = DurtTheme.type.bodyLarge, color = DurtTheme.colors.textSecondary)
        extra()
    }
}

/** A white note card with a 1 dp border (privacy note on 0c, autofill hint on 0d). */
@Composable
fun NoteCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    Column(
        modifier
            .fillMaxWidth()
            .background(DurtTheme.colors.surface, shape)
            .border(dimens.borderCard, DurtTheme.colors.border, shape)
            .padding(dimens.space16),
    ) { content() }
}

/** The big single line input of 0c and 0e: 2 dp `ink` border when focused, `borderStrong` otherwise. */
@Composable
fun BigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(dimens.radiusL)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = DurtTheme.type.inputLarge.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
        decorationBox = { inner ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, shape)
                    .border(
                        if (focused) dimens.borderFocused else dimens.borderStrong,
                        if (focused) colors.ink else colors.borderStrong,
                        shape,
                    ).padding(horizontal = dimens.space16, vertical = dimens.space16),
            ) { inner() }
        },
    )
}

/** Small bold label above an input ("Telefon numarası", "Adın"). */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = DurtTheme.type.bodySmallStrong, color = DurtTheme.colors.ink, modifier = modifier)
}

/** Shows "5321234567" as "532 123 45 67" while the state keeps digits only (0c, and the typed number on 1e). */
internal object PhoneGrouping : VisualTransformation {
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
