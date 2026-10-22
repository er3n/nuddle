package app.nuddle.ui.screens.poke

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.domain.TimeLeft
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.components.SlideToConfirm
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/** Ertele: when it rings again ("Ertele · 08:30", NUD-63) and whether it is the last one. */
data class SnoozeUi(
    val time: String,
    val last: Boolean,
)

/**
 * Ara or Mesaj at on the poke (NUD-128): the task is about calling or messaging this connection, and [phone] is the
 * number the phone has for them.
 */
data class PokeContactUi(
    val phone: String,
    val message: Boolean,
)

@Immutable
data class PokeUiState(
    val pokeNumber: Int,
    val time: String,
    val title: String,
    val detail: String?,
    /** Null for an `at` task: its moment has come, "Zamanı geldi." (open-questions#43). */
    val timeLeft: TimeLeft?,
    /** A sentence for this poke; none until the server sends poke copy (feature slice 1). */
    val message: String? = null,
    /** What Ertele gives now; null hides it (no snooze left, or it would pass the deadline). */
    val snooze: SnoozeUi? = SnoozeUi(time = "08:30", last = false),
    /** A one-time message (NUD-79): "Tamam" only. */
    val notice: Boolean = false,
    /** Whom to call or message from here (NUD-128); null hides the button, always null on a one-time message. */
    val contact: PokeContactUi? = null,
    /** The step's poke title over the task (NUD-130, glossary.md, Poke titles); null on a one-time message. */
    val pokeTitle: String? = null,
    /** Shown over a locked phone (NUD-131): Bitti or Tamam is a slide, so a pocket tap cannot close the task. */
    val locked: Boolean = false,
)

@Composable
fun timeLeftText(timeLeft: TimeLeft): String =
    when (timeLeft) {
        is TimeLeft.Minutes -> stringResource(R.string.duration_minutes, timeLeft.value)
        is TimeLeft.Hours -> pluralStringResource(R.plurals.duration_hours, timeLeft.value, timeLeft.value)
    }

/**
 * Full screen poke (3b): `poke` background with static rings, the poke title (NUD-130), the task, and the three
 * answers; Ara or Mesaj at under Bitti when the task is about calling or messaging a connection (NUD-128). [onContact]
 * leaves the task open. Over a locked phone Bitti is a slide ([PokeUiState.locked], NUD-131).
 */
@Composable
fun PokeScreen(
    state: PokeUiState,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onCantToday: () -> Unit,
    modifier: Modifier = Modifier,
    onContact: () -> Unit = {},
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val timeLeft =
        state.timeLeft?.let { stringResource(R.string.poke_time_left, timeLeftText(it)) } ?: stringResource(R.string.poke_moment_now)
    ScreenLayout(
        background = colors.poke,
        modifier = modifier.testTag(TestTags.POKE),
        horizontalAlignment = Alignment.CenterHorizontally,
        bottom = {
            PokeDoneButton(state, onDone)
            state.contact?.let { PokeContactButton(it, onContact) }
            if (state.snooze != null && !state.notice) {
                DurtButton(
                    text =
                        stringResource(
                            if (state.snooze.last) R.string.poke_snooze_last else R.string.poke_snooze,
                            state.snooze.time,
                        ),
                    onClick = onSnooze,
                    kind = DurtButtonKind.OnPokeOutline,
                    size = DurtButtonSize.Medium,
                    modifier = Modifier.testTag(TestTags.POKE_SNOOZE),
                )
            }
            if (!state.notice) {
                TextLink(
                    text = stringResource(R.string.poke_cant_today),
                    onClick = onCantToday,
                    color = colors.surface,
                    style = DurtTheme.type.button,
                    modifier = Modifier.testTag(TestTags.POKE_CANT_TODAY),
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.poke_count_at, state.pokeNumber, state.time),
            style = DurtTheme.type.bodySmallStrong,
            color = colors.surface,
            modifier =
                Modifier
                    .background(colors.pokeDeep, RoundedCornerShape(dimens.radiusPill))
                    .padding(horizontal = dimens.space14, vertical = dimens.space6),
        )
        PokeRings()
        if (state.pokeTitle != null) {
            Text(
                text = state.pokeTitle,
                style = DurtTheme.type.bodyLarge,
                color = colors.onPokeSoft,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = state.title,
            style = DurtTheme.type.displayPoke,
            color = colors.surface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = state.detail?.let { stringResource(R.string.separator_dot, it, timeLeft) } ?: timeLeft,
            style = DurtTheme.type.bodyLarge,
            color = colors.onPokeSoft,
            textAlign = TextAlign.Center,
        )
        if (state.message != null) {
            Text(
                text = state.message,
                style = DurtTheme.type.body,
                color = colors.onPokeSoft,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.pokeDeep, RoundedCornerShape(dimens.radiusXl))
                        .padding(dimens.space20),
            )
        }
    }
}

/** Bitti (Tamam on a one-time message); a slide over the lock screen (NUD-131, screens.md 3b). */
@Composable
private fun PokeDoneButton(
    state: PokeUiState,
    onDone: () -> Unit,
) {
    val label = stringResource(if (state.notice) R.string.poke_ok else R.string.poke_done)
    if (state.locked) {
        SlideToConfirm(
            text = stringResource(if (state.notice) R.string.poke_slide_ok else R.string.poke_slide_done),
            accessibilityLabel = label,
            onConfirm = onDone,
            modifier = Modifier.testTag(TestTags.POKE_DONE),
        )
    } else {
        DurtButton(
            text = label,
            onClick = onDone,
            kind = DurtButtonKind.OnPoke,
            size = DurtButtonSize.Poke,
            leadingIcon = DurtIcons.Check,
            modifier = Modifier.testTag(TestTags.POKE_DONE),
        )
    }
}

/** Ara or Mesaj at under Bitti (NUD-128). */
@Composable
private fun PokeContactButton(
    contact: PokeContactUi,
    onClick: () -> Unit,
) {
    DurtButton(
        text = stringResource(if (contact.message) R.string.poke_message else R.string.poke_call),
        onClick = onClick,
        kind = DurtButtonKind.OnPokeOutline,
        size = DurtButtonSize.Medium,
        leadingIcon = if (contact.message) DurtIcons.Message else DurtIcons.Phone,
        modifier = Modifier.testTag(TestTags.POKE_CONTACT),
    )
}

/** The bell in two static rings (3b, and the demo poke 2d). Poke rings stay still (platform.md, Accessibility). */
@Composable
internal fun PokeRings(modifier: Modifier = Modifier) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Box(
        modifier.size(dimens.pokeRingOuter).border(dimens.borderFocused, colors.pokeRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(dimens.pokeRingInner).border(dimens.borderFocused, colors.pokeRing, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(dimens.pokeBell).background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    DurtIcons.Bell,
                    contentDescription = null,
                    tint = colors.poke,
                    modifier = Modifier.size(dimens.iconHero),
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun PokeScreenPreview() {
    DurtTheme {
        PokeScreen(
            state =
                PokeUiState(
                    pokeNumber = 3,
                    time = "08:15",
                    title = "Para çek",
                    detail = "1.000 TL",
                    timeLeft = TimeLeft.Minutes(45),
                    message = "İki dürtmeyi geçtin. Bu sefer ciddiyim, 09:00'a az kaldı.",
                    snooze = SnoozeUi(time = "08:25", last = true),
                    pokeTitle = "Pes etmem",
                ),
            onDone = {},
            onSnooze = {},
            onCantToday = {},
        )
    }
}

@PreviewScreens
@Composable
private fun PokeScreenNoSnoozePreview() {
    DurtTheme {
        PokeScreen(
            state =
                PokeUiState(
                    pokeNumber = 4,
                    time = "08:40",
                    title = "Para çek",
                    detail = "1.000 TL",
                    timeLeft = TimeLeft.Minutes(20),
                    snooze = null,
                    pokeTitle = "Ben gitmiyorum",
                ),
            onDone = {},
            onSnooze = {},
            onCantToday = {},
        )
    }
}

/** An `at` task, "2 dakika sonra su iç": its moment has come (open-questions#43). */
@PreviewScreens
@Composable
private fun PokeScreenMomentPreview() {
    DurtTheme {
        PokeScreen(
            state =
                PokeUiState(
                    pokeNumber = 2,
                    time = "19:51",
                    title = "Su iç",
                    detail = null,
                    timeLeft = null,
                    pokeTitle = "Hadi, şimdi tam sırası",
                ),
            onDone = {},
            onSnooze = {},
            onCantToday = {},
        )
    }
}

/** "Annemi ara" to self (NUD-128): Ara under Bitti opens the dialer with Ayşe's number. */
@PreviewScreens
@Composable
private fun PokeScreenCallPreview() {
    DurtTheme {
        PokeScreen(
            state =
                PokeUiState(
                    pokeNumber = 3,
                    time = "18:45",
                    title = "Annemi ara",
                    detail = null,
                    timeLeft = TimeLeft.Minutes(15),
                    snooze = SnoozeUi(time = "18:55", last = false),
                    contact = PokeContactUi(phone = "+905320000005", message = false),
                    pokeTitle = "İnatçıyımdır, bilirsin",
                ),
            onDone = {},
            onSnooze = {},
            onCantToday = {},
        )
    }
}

/** "Sibel'e mesaj at" (NUD-128): Mesaj at opens the message apps' chooser. */
@PreviewScreens
@Composable
private fun PokeScreenMessagePreview() {
    DurtTheme {
        PokeScreen(
            state =
                PokeUiState(
                    pokeNumber = 2,
                    time = "19:30",
                    title = "Sibel'e mesaj at",
                    detail = null,
                    timeLeft = null,
                    snooze = null,
                    contact = PokeContactUi(phone = "+905320000001", message = true),
                    pokeTitle = "Pes etmem",
                ),
            onDone = {},
            onSnooze = {},
            onCantToday = {},
        )
    }
}

/** Over the lock screen (NUD-131): Bitti is a slide, the other answers stay taps. */
@PreviewScreens
@Composable
private fun PokeScreenLockedPreview() {
    DurtTheme {
        PokeScreen(
            state =
                PokeUiState(
                    pokeNumber = 3,
                    time = "08:15",
                    title = "Para çek",
                    detail = "1.000 TL",
                    timeLeft = TimeLeft.Minutes(45),
                    snooze = SnoozeUi(time = "08:25", last = true),
                    pokeTitle = "Pes etmem",
                    locked = true,
                ),
            onDone = {},
            onSnooze = {},
            onCantToday = {},
        )
    }
}
