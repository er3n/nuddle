package app.nuddle.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nuddle.R

private fun variableFont(
    resId: Int,
    weight: FontWeight,
) = Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Space Grotesk: headings, logo, big numbers (tokens.json typography.families.display). */
val SpaceGrotesk =
    FontFamily(
        variableFont(R.font.space_grotesk, FontWeight.Medium),
        variableFont(R.font.space_grotesk, FontWeight.SemiBold),
        variableFont(R.font.space_grotesk, FontWeight.Bold),
    )

/** IBM Plex Sans: everything else (tokens.json typography.families.text). */
val IbmPlexSans =
    FontFamily(
        variableFont(R.font.ibm_plex_sans, FontWeight.Normal),
        variableFont(R.font.ibm_plex_sans, FontWeight.Medium),
        variableFont(R.font.ibm_plex_sans, FontWeight.SemiBold),
        variableFont(R.font.ibm_plex_sans, FontWeight.Bold),
    )

private val headingSpacing = (-0.02).em

private fun display(
    size: Int,
    lineHeight: Int?,
) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = lineHeight?.sp ?: TextStyle.Default.lineHeight,
    letterSpacing = headingSpacing,
)

private fun text(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(fontFamily = IbmPlexSans, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

/** Text styles from docs/ux/tokens.json (typography.styles). Sizes in sp follow the system font scale. */
@Immutable
data class DurtType(
    val displayPoke: TextStyle = display(size = 44, lineHeight = 44),
    val h1: TextStyle = display(size = 30, lineHeight = 33),
    val h2: TextStyle = display(size = 26, lineHeight = 29),
    val h3: TextStyle = display(size = 24, lineHeight = 28),
    val brand: TextStyle = display(size = 22, lineHeight = null),
    val onboardingTitle: TextStyle = display(size = 32, lineHeight = 35),
    val welcomeTitle: TextStyle = display(size = 40, lineHeight = 41),
    val bodyLarge: TextStyle = text(size = 17, lineHeight = 24),
    val input: TextStyle = text(size = 18, lineHeight = 25),
    val inputIdle: TextStyle = text(size = 16, lineHeight = 22),
    val inputLarge: TextStyle = display(size = 26, lineHeight = 32).copy(letterSpacing = 0.em),
    val body: TextStyle = text(size = 15, lineHeight = 22),
    val bodyStrong: TextStyle = text(size = 15, lineHeight = 22, weight = FontWeight.SemiBold),
    val bodySmall: TextStyle = text(size = 14, lineHeight = 20),
    val bodySmallStrong: TextStyle = text(size = 14, lineHeight = 20, weight = FontWeight.SemiBold),
    val caption: TextStyle = text(size = 13, lineHeight = 19),
    val captionStrong: TextStyle = text(size = 13, lineHeight = 19, weight = FontWeight.SemiBold),
    val overline: TextStyle =
        text(
            size = 12,
            lineHeight = 16,
            weight = FontWeight.SemiBold,
        ).copy(letterSpacing = 0.06.em),
    val micro: TextStyle = text(size = 12, lineHeight = 16, weight = FontWeight.SemiBold),
    val button: TextStyle = text(size = 16, lineHeight = 22, weight = FontWeight.SemiBold),
    val buttonSmall: TextStyle = text(size = 15, lineHeight = 20, weight = FontWeight.SemiBold),
    val buttonPoke: TextStyle = text(size = 18, lineHeight = 24, weight = FontWeight.Bold),
    val codeDigit: TextStyle = display(size = 28, lineHeight = 34).copy(letterSpacing = 0.em),
)
