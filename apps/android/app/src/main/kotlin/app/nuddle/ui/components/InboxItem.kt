package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

/** The icon's colors: a warning, something finished, or something that stopped (7b). */
enum class InboxTone { Warning, Success, Neutral }

/**
 * One entry in the bell (components.md, 7). Unread: title 600 and an 8 dp `poke` dot on the right. Without
 * [onClick] the entry is only text.
 */
@Composable
fun InboxItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    time: String,
    unread: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tone: InboxTone = InboxTone.Warning,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val (background, tint) =
        when (tone) {
            InboxTone.Warning -> colors.warningSoft to colors.warning
            InboxTone.Success -> colors.successSoft to colors.success
            InboxTone.Neutral -> colors.neutralPill to colors.textSecondary
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = dimens.listRowWithDescription)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = dimens.space10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Box(
            Modifier.size(dimens.avatarList).background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.icon))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = if (unread) DurtTheme.type.bodyStrong else DurtTheme.type.body, color = colors.ink)
            Text(subtitle, style = DurtTheme.type.caption, color = colors.textSecondary)
        }
        Text(time, style = DurtTheme.type.caption, color = colors.textTertiary)
        if (unread) Box(Modifier.size(dimens.unreadDot).background(colors.poke, CircleShape))
    }
}

@PreviewComponent
@Composable
private fun InboxItemPreview() {
    PreviewSurface {
        SettingsGroup("Bugün") {
            InboxItem(
                DurtIcons.Warning,
                "Son dürtmen çalmadı",
                "Telefonun beni kapatmış",
                "08:20",
                unread = true,
                onClick = {},
            )
            GroupDivider()
            InboxItem(
                DurtIcons.Check,
                "Eren bitirdi: \"Çöpü at\"",
                "Bitti dedi",
                "07:58",
                unread = false,
                onClick = null,
                tone = InboxTone.Success,
            )
        }
    }
}
