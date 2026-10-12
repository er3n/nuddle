package app.nuddle.ui.screens.errors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.ui.theme.DurtTheme

/** The centered part of 6d and 6f: an icon in a circle, the title and one paragraph. */
@Composable
internal fun ColumnScope.CenteredError(
    icon: ImageVector,
    circle: Color,
    tint: Color,
    title: String,
    body: String,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Box(Modifier.size(dimens.avatarHero).background(circle, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.iconHero))
    }
    Text(
        title,
        style = DurtTheme.type.h2,
        color = colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        body,
        style = DurtTheme.type.body,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = dimens.space8),
    )
}
