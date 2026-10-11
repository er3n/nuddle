package app.nuddle.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.nuddle.ui.theme.DurtTheme

/**
 * Screenshot matrix for screens (docs/testing.md, Android UI tests): design width 390 dp and small phone 360 dp,
 * font scale 1.0 / 1.5 / 2.0, Turkish. Roborazzi records one image per preview.
 */
@Preview(name = "390", widthDp = 390, heightDp = 844, locale = "tr")
@Preview(name = "390 font 1.5", widthDp = 390, heightDp = 844, fontScale = 1.5f, locale = "tr")
@Preview(name = "390 font 2", widthDp = 390, heightDp = 844, fontScale = 2f, locale = "tr")
@Preview(name = "360", widthDp = 360, heightDp = 780, locale = "tr")
@Preview(name = "360 font 1.5", widthDp = 360, heightDp = 780, fontScale = 1.5f, locale = "tr")
@Preview(name = "360 font 2", widthDp = 360, heightDp = 780, fontScale = 2f, locale = "tr")
annotation class PreviewScreens

/** Components are captured once at design width; screens cover the font scales. */
@Preview(name = "component", widthDp = 390, locale = "tr")
annotation class PreviewComponent

/** Theme and a background so component previews look like they do on a screen. */
@Composable
fun PreviewSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DurtTheme {
        Box(
            modifier
                .background(DurtTheme.colors.background)
                .padding(DurtTheme.dimens.space16),
        ) { content() }
    }
}
