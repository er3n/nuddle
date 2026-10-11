package app.nuddle.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val LocalDurtColors = staticCompositionLocalOf { DurtColors() }
private val LocalDurtType = staticCompositionLocalOf { DurtType() }
private val LocalDurtDimens = staticCompositionLocalOf { DurtDimens() }

private const val SELECTION_ALPHA = 0.3f

/**
 * Material 3 as a base, our tokens on top (docs/ux/components.md, Theme). Screens read [DurtTheme.colors],
 * [DurtTheme.type] and [DurtTheme.dimens]; Material components only get a matching color scheme.
 */
@Composable
fun DurtTheme(content: @Composable () -> Unit) {
    val colors = DurtColors()
    val scheme =
        lightColorScheme(
            primary = colors.ink,
            onPrimary = colors.surface,
            secondary = colors.accent,
            onSecondary = colors.ink,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            error = colors.poke,
            outline = colors.borderStrong,
            scrim = colors.scrim,
        )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalDurtColors provides colors,
            LocalDurtType provides DurtType(),
            LocalDurtDimens provides DurtDimens(),
            LocalTextSelectionColors provides
                TextSelectionColors(
                    handleColor = colors.accent,
                    backgroundColor = colors.accent.copy(alpha = SELECTION_ALPHA),
                ),
            content = content,
        )
    }
}

object DurtTheme {
    val colors: DurtColors
        @Composable @ReadOnlyComposable
        get() = LocalDurtColors.current

    val type: DurtType
        @Composable @ReadOnlyComposable
        get() = LocalDurtType.current

    val dimens: DurtDimens
        @Composable @ReadOnlyComposable
        get() = LocalDurtDimens.current
}

/** Pressed state: the background darkens 8% (docs/ux/components.md, DurtButton). */
fun Color.pressed(): Color = lerp(this, Color.Black, DurtMotion.PRESSED_DARKEN)

/** Avatar color for a person other than the user, derived deterministically from their id. */
fun DurtColors.avatarFor(personId: String): Color = avatarOthers[Math.floorMod(personId.hashCode(), avatarOthers.size)]
