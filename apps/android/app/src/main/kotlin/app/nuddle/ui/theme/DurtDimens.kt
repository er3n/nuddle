package app.nuddle.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing, sizes, radii and borders from docs/ux/tokens.json (canvas px = dp). */
@Immutable
data class DurtDimens(
    // space
    val screenPaddingHorizontal: Dp = 20.dp,
    val screenPaddingHorizontalSmallPhone: Dp = 16.dp,
    val screenPaddingBottom: Dp = 24.dp,
    val screenPaddingTop: Dp = 8.dp,
    val space4: Dp = 4.dp,
    val space6: Dp = 6.dp,
    val space8: Dp = 8.dp,
    val space10: Dp = 10.dp,
    val space12: Dp = 12.dp,
    val space14: Dp = 14.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space32: Dp = 32.dp,
    val space48: Dp = 48.dp,
    val minTouchTarget: Dp = 44.dp,
    // size.button
    val buttonPoke: Dp = 62.dp,
    val buttonLarge: Dp = 56.dp,
    val buttonMedium: Dp = 52.dp,
    val buttonSmall: Dp = 44.dp,
    val buttonCompact: Dp = 40.dp,
    val buttonCard: Dp = 42.dp,
    // size.listRow
    val listRowSingle: Dp = 52.dp,
    val listRowWithDescription: Dp = 64.dp,
    // size.avatar
    val avatarHero: Dp = 88.dp,
    // Name screen (0e), measured on the canvas.
    val avatarOnboarding: Dp = 112.dp,
    val avatarProfile: Dp = 64.dp,
    val avatarTopBar: Dp = 44.dp,
    val avatarNotification: Dp = 40.dp,
    val avatarList: Dp = 36.dp,
    val avatarCard: Dp = 32.dp,
    val avatarInbox: Dp = 48.dp,
    val avatarInline: Dp = 28.dp,
    val avatarSmall: Dp = 24.dp,
    // size.switch, pill, sheet handle
    val switchWidth: Dp = 52.dp,
    val switchHeight: Dp = 32.dp,
    val switchThumb: Dp = 26.dp,
    val pillHeight: Dp = 24.dp,
    // size.tabs: home tabs (NUD-67)
    val tabHeight: Dp = 40.dp,
    val tabPadding: Dp = 4.dp,
    val tabSwipe: Dp = 48.dp,
    // size.slide: SlideToConfirm, Bitti over the lock screen (NUD-131)
    val slideHeight: Dp = 62.dp,
    val slideThumb: Dp = 54.dp,
    val slideInset: Dp = 4.dp,
    val slideConfirm: Float = 0.85f,
    val pillPaddingHorizontal: Dp = 10.dp,
    val sheetHandleWidth: Dp = 40.dp,
    val sheetHandleHeight: Dp = 5.dp,
    // components (docs/ux/components.md)
    val iconSmall: Dp = 16.dp,
    val icon: Dp = 20.dp,
    val iconLarge: Dp = 24.dp,
    val iconHero: Dp = 40.dp,
    val spinner: Dp = 20.dp,
    val spinnerStroke: Dp = 2.dp,
    val badgeSize: Dp = 18.dp,
    val unreadDot: Dp = 8.dp,
    val statusDot: Dp = 8.dp,
    // The compose bar's mic and send button: slimmer since the phone test (NUD-48).
    val composeMic: Dp = 40.dp,
    val composeMaxLines: Int = 4,
    val codeBoxHeight: Dp = 64.dp,
    val codeBoxGap: Dp = 8.dp,
    val inputHeight: Dp = 60.dp,
    val countryButtonWidth: Dp = 106.dp,
    val toastOffset: Dp = 10.dp,
    val toastProgress: Dp = 3.dp,
    val pokeRingOuter: Dp = 156.dp,
    val pokeRingInner: Dp = 112.dp,
    val pokeBell: Dp = 68.dp,
    val avatarBadge: Dp = 36.dp,
    // 4e: the photo that went out, on the next-up card (canvas 64 px).
    val photoThumb: Dp = 64.dp,
    val illustrationTilt: Float = 2f,
    // 2c: the phone that shows what a poke looks like, and the progress bar of 2e.
    val setupPhoneWidth: Dp = 210.dp,
    val setupPhoneHeight: Dp = 360.dp,
    val setupPhoneFrame: Dp = 6.dp,
    val setupPhoneRadius: Dp = 32.dp,
    val setupPhoneRing: Dp = 76.dp,
    val setupPhoneBell: Dp = 48.dp,
    val setupPhoneButton: Dp = 36.dp,
    val setupPhoneButtonRadius: Dp = 10.dp,
    val progressBar: Dp = 6.dp,
    // radius
    val radiusPill: Dp = 999.dp,
    val radiusSheet: Dp = 26.dp,
    val radiusXl: Dp = 20.dp,
    val radiusL: Dp = 18.dp,
    val radiusM: Dp = 16.dp,
    val radiusS: Dp = 12.dp,
    val radiusCode: Dp = 14.dp,
    // border
    val borderCard: Dp = 1.dp,
    val borderStrong: Dp = 1.5.dp,
    val borderFocused: Dp = 2.dp,
    // size.smallPhoneWidth: below this the horizontal screen padding shrinks to 16.
    val smallPhoneWidth: Dp = 360.dp,
)

/** Motion from docs/ux/tokens.json (motion). */
object DurtMotion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 200
    const val LONG_MS = 250
    const val UNDO_TOAST_MS = 5_000L
    const val UNDO_TOAST_TALKBACK_MS = 10_000L
    const val DONE_NOTE_MS = 4_000L
    const val REPLY_NOTE_MS = 8_000L
    const val PRESSED_DARKEN = 0.08f
    const val HINT_BUBBLE_MS = 2_000L
    const val VOICE_PULSE_MS = 1_600
    const val VOICE_PULSE_SCALE = 1.7f
    const val VOICE_PULSE_ALPHA = 0.55f
}
