package app.nuddle.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme
import app.nuddle.ui.uppercaseForUi

/** `overline` title over a white box; rows are separated by 1 dp `surfaceMuted` dividers (components.md, 5). */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = DurtTheme.dimens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.space6)) {
        Text(title.uppercaseForUi(), style = DurtTheme.type.overline, color = DurtTheme.colors.textTertiary)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radiusM))
                    .background(DurtTheme.colors.surface)
                    .padding(horizontal = dimens.space16),
            content = content,
        )
    }
}

/** Divider between rows of a [SettingsGroup]; not used after the last row. */
@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = DurtTheme.dimens.borderCard, color = DurtTheme.colors.surfaceMuted)
}

/**
 * 52 dp row with a label and an optional value or [trailing] mark; with `onClick` it shows a chevron and the row is
 * tappable.
 */
@Composable
fun LineRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    labelColor: Color = DurtTheme.colors.ink,
    labelStyle: TextStyle = DurtTheme.type.body,
    valueColor: Color = DurtTheme.colors.ink,
    onClick: (() -> Unit)? = null,
) {
    val dimens = DurtTheme.dimens
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = dimens.listRowSingle).then(clickModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        leading?.invoke()
        Text(label, style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = DurtTheme.type.bodyStrong, color = valueColor, textAlign = TextAlign.End)
        trailing?.invoke()
        if (onClick != null) {
            Icon(
                DurtIcons.ChevronRight,
                contentDescription = null,
                tint = DurtTheme.colors.textMuted,
                modifier = Modifier.size(dimens.iconSmall),
            )
        }
    }
}

/** 64 dp row with a 52x32 switch: on `success`, off `borderStrong`. Whole row toggles, `Role.Switch`. */
@Composable
fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = dimens.listRowWithDescription)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = dimens.space10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = DurtTheme.type.bodyStrong, color = colors.ink)
            Text(description, style = DurtTheme.type.caption, color = colors.textSecondary)
        }
        Box(
            modifier =
                Modifier
                    .size(dimens.switchWidth, dimens.switchHeight)
                    .background(if (checked) colors.success else colors.borderStrong, CircleShape)
                    .padding((dimens.switchHeight - dimens.switchThumb) / 2),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(dimens.switchThumb).background(colors.surface, CircleShape))
        }
    }
}

/** 40 dp single choice chip; selected is `ink` with white text. `Role.RadioButton` with selection state. */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusPill)
    Box(
        modifier =
            modifier
                .heightIn(min = dimens.buttonCompact)
                .clip(shape)
                .background(if (selected) colors.ink else colors.surface)
                .border(BorderStroke(dimens.borderStrong, if (selected) colors.ink else colors.borderStrong), shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = dimens.space16, vertical = dimens.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DurtTheme.type.bodySmallStrong, color = if (selected) colors.surface else colors.ink)
    }
}

@PreviewComponent
@Composable
private fun SettingsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            SettingsGroup("Bizimkiler") {
                LineRow(
                    "Sibel",
                    value = "Eşin",
                    leading = { Avatar("Sibel", DurtTheme.colors.avatarOthers.first(), DurtTheme.dimens.avatarInline) },
                    onClick = {},
                )
                GroupDivider()
                LineRow("Sessiz saatler", value = "23:00-07:00", onClick = {})
            }
            SettingsGroup("Dürtme") {
                ToggleRow(
                    "Başkalarının işleri için bana haber verilebilir",
                    "Bir tanıdığım işini kaçırırsa bana söyleyebilirsin.",
                    true,
                    {},
                )
                GroupDivider()
                ToggleRow("Kapalı örnek", "Kapalı switch borderStrong zeminde.", false, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space8)) {
                ChoiceChip("Yarın hallederim", selected = true, onClick = {})
                ChoiceChip("Vaktim olmadı", selected = false, onClick = {})
            }
        }
    }
}
