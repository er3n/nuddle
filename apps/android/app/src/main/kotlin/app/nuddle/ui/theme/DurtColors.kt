package app.nuddle.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors from docs/ux/tokens.json. This package is the only place a [Color] is constructed (Konsist enforces it).
 * Light theme only in the MVP; the `dark*` values are for the welcome and matched screens, not a dark theme.
 */
@Immutable
data class DurtColors(
    val ink: Color = Color(0xFF11151A),
    val surface: Color = Color(0xFFFFFFFF),
    val background: Color = Color(0xFFF2F4F6),
    val surfaceMuted: Color = Color(0xFFEEF1F4),
    val border: Color = Color(0xFFDDE2E7),
    val borderStrong: Color = Color(0xFFC9D0D7),
    val textBody: Color = Color(0xFF3B4550),
    val textSecondary: Color = Color(0xFF4D5763),
    val textTertiary: Color = Color(0xFF5A6470),
    val textMuted: Color = Color(0xFF8A96A3),
    val accent: Color = Color(0xFFFF5A1F),
    val poke: Color = Color(0xFFC83C08),
    val pokeDeep: Color = Color(0xFFA93206),
    val pokeRing: Color = Color(0xFFE8774C),
    val onPokeSoft: Color = Color(0xFFFFF1EB),
    val link: Color = Color(0xFFC23A06),
    val linkPressed: Color = Color(0xFF8F2A03),
    val success: Color = Color(0xFF0F6E5C),
    val successSoft: Color = Color(0xFFDDF1EC),
    val onSuccessSoft: Color = Color(0xFF0B5446),
    val warning: Color = Color(0xFF9A2E05),
    val warningSoft: Color = Color(0xFFFFF1E8),
    val warningBorder: Color = Color(0xFFFFB596),
    val warningPill: Color = Color(0xFFFFE3D6),
    val onWarningPill: Color = Color(0xFF7A2604),
    val highlightSoft: Color = Color(0xFFFFF6F1),
    val highlightBorder: Color = Color(0xFFFFD6C4),
    val danger: Color = Color(0xFF7A1A1A),
    val infoPill: Color = Color(0xFFE2EAFB),
    val onInfoPill: Color = Color(0xFF1A43A3),
    val neutralPill: Color = Color(0xFFE3E7EB),
    val onNeutralPill: Color = Color(0xFF3B4550),
    val scrim: Color = Color(0xFF11151A).copy(alpha = 0.5f),
    val toastBackground: Color = Color(0xFF11151A),
    val toastButton: Color = Color(0xFF2C333B),
    val toastTick: Color = Color(0xFF7FD8C3),
    val toastProgress: Color = Color(0xFFFF5A1F),
    val darkBackground: Color = Color(0xFF11151A),
    val darkSurface: Color = Color(0xFF1C222A),
    val darkBorder: Color = Color(0xFF2C333B),
    val darkText: Color = Color(0xFFFFFFFF),
    val darkTextSecondary: Color = Color(0xFFC9D0D7),
    val avatarSelf: Color = Color(0xFF11151A),
    // TODO(open-questions#20): six colors, each 4.5:1 with white text. Only the first (Sibel) is from the design.
    val avatarOthers: List<Color> =
        listOf(
            Color(0xFFC83C08),
            Color(0xFF1A43A3),
            Color(0xFF0F6E5C),
            Color(0xFF7A1A1A),
            Color(0xFF5B3A9E),
            Color(0xFF3B4550),
        ),
)
