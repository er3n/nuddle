package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

/** A one line note with a placeholder: the re-request note (4f) and the drop note (7f). */
@Composable
fun NoteField(
    note: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    BasicTextField(
        value = note,
        onValueChange = onChange,
        singleLine = true,
        textStyle = DurtTheme.type.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, shape)
                    .border(dimens.borderStrong, colors.borderStrong, shape)
                    .padding(horizontal = dimens.space16, vertical = dimens.space16),
            ) {
                if (note.isEmpty()) Text(placeholder, style = DurtTheme.type.body, color = colors.textMuted)
                inner()
            }
        },
    )
}

@PreviewComponent
@Composable
private fun NoteFieldPreview() {
    PreviewSurface { NoteField(note = "", onChange = {}, placeholder = "Ya da kendin yaz") }
}
