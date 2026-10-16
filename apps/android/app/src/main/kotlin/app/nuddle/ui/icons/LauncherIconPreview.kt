package app.nuddle.ui.icons

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import app.nuddle.R
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

/**
 * The launcher icon (res/mipmap-anydpi/ic_launcher.xml) as the launcher draws it: masked at home screen sizes, as a
 * themed icon from the monochrome layer, and the whole 108 dp canvas with the 66 dp safe zone. Only for the
 * screenshot set; the app never draws its own icon.
 */
@Composable
private fun LauncherIcon(
    size: Dp,
    themed: Color? = null,
    themedBackground: Color? = null,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap =
        remember(px, themed, themedBackground) {
            val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
            val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) icon.monochrome else null
            val drawn =
                if (themed != null && themedBackground != null && monochrome != null) {
                    AdaptiveIconDrawable(
                        themedBackground.toArgb().toDrawable(),
                        monochrome.mutate().apply { setTint(themed.toArgb()) },
                    )
                } else {
                    icon
                }
            drawn.toBitmap(px, px)
        }
    Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
}

/** Both layers unmasked on the full canvas, the safe zone circle on top. */
@Composable
private fun LauncherIconCanvas() {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 108.dp.roundToPx() }
    val bitmap =
        remember(px) {
            val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
            val background = icon.background.toBitmap(px, px)
            android.graphics.Canvas(background).also { icon.foreground.apply { setBounds(0, 0, px, px) }.draw(it) }
            background
        }
    val safeZone = DurtTheme.colors.textMuted
    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(108.dp))
        Canvas(Modifier.size(66.dp)) { drawCircle(safeZone, style = Stroke(width = 1.dp.toPx())) }
    }
}

@PreviewComponent
@Composable
private fun LauncherIconPreview() {
    PreviewSurface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIconCanvas()
            LauncherIcon(48.dp)
            LauncherIcon(48.dp, themed = DurtTheme.colors.onInfoPill, themedBackground = DurtTheme.colors.infoPill)
            LauncherIcon(36.dp)
            LauncherIcon(24.dp)
        }
    }
}
