package app.nuddle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtMotion
import app.nuddle.ui.theme.DurtTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun ToastBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DurtTheme.dimens.radiusL))
            .background(DurtTheme.colors.toastBackground)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) { content() }
}

@Composable
private fun ToastButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = DurtTheme.dimens
    Box(
        modifier =
            modifier
                .heightIn(min = dimens.buttonCompact)
                .clip(RoundedCornerShape(dimens.radiusS))
                .background(DurtTheme.colors.toastButton)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ToastLabel(text, DurtTheme.colors.surface)
    }
}

/** A button label that wraps inside its half at large font scales (screens.md 4i). */
@Composable
private fun ToastLabel(
    text: String,
    color: Color,
) {
    Text(
        text,
        style = DurtTheme.type.bodySmallStrong,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = DurtTheme.dimens.space8),
    )
}

/** One half of the finish switch (NUD-137): selected is white with `ink` text, the other the button color. */
@Composable
private fun ToastChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Box(
        modifier =
            modifier
                .heightIn(min = dimens.buttonCompact)
                .clip(RoundedCornerShape(dimens.radiusS))
                .background(if (selected) colors.surface else colors.toastButton)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ToastLabel(text, if (selected) colors.ink else colors.surface)
    }
}

/**
 * Sent, with a 5 s window to undo or edit (docs/ux/components.md, 6). A 3 dp orange line fills over the window;
 * timeout confirms. With TalkBack on it lasts 10 s and takes focus. With [photo] not null a "Bitti" | "Fotoğrafla"
 * switch sits above the buttons (NUD-137, screens.md 4i); a change starts the window again.
 */
@Composable
fun UndoToast(
    title: String,
    subtitle: String,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = DurtMotion.UNDO_TOAST_MS,
    photo: Boolean? = null,
    onPhoto: (Boolean) -> Unit = {},
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val accessibility = LocalAccessibilityManager.current
    val window = accessibility?.calculateRecommendedTimeoutMillis(durationMs, containsControls = true) ?: durationMs
    val progress = remember { Animatable(0f) }
    val currentTimeout = rememberUpdatedState(onTimeout)
    val focus = remember { FocusRequester() }
    LaunchedEffect(window, photo) {
        // The window is a timer, not the animation: with animations off (animator scale 0) the line
        // fills at once, but the toast must still stay for the whole window. A finish change starts it again.
        progress.snapTo(0f)
        launch { progress.animateTo(1f, tween(window.toInt(), easing = LinearEasing)) }
        delay(window)
        currentTimeout.value()
    }
    ToastBox(modifier.focusRequester(focus).focusable()) {
        Column {
            Column(Modifier.padding(dimens.space14), verticalArrangement = Arrangement.spacedBy(dimens.space12)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.space12),
                ) {
                    Icon(
                        DurtIcons.Check,
                        contentDescription = null,
                        tint = colors.toastTick,
                        modifier = Modifier.size(dimens.icon),
                    )
                    Column {
                        Text(title, style = DurtTheme.type.bodyStrong, color = colors.surface)
                        Text(subtitle, style = DurtTheme.type.caption, color = colors.darkTextSecondary)
                    }
                }
                if (photo != null) {
                    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
                        ToastChoice(stringResource(R.string.undo_switch_done), !photo, { if (photo) onPhoto(false) }, Modifier.weight(1f))
                        ToastChoice(stringResource(R.string.undo_switch_photo), photo, { if (!photo) onPhoto(true) }, Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
                    ToastButton(stringResource(R.string.action_undo), onUndo, Modifier.weight(1f))
                    ToastButton(stringResource(R.string.action_edit), onEdit, Modifier.weight(1f))
                }
            }
            Box(Modifier.fillMaxWidth(progress.value).height(dimens.toastProgress).background(colors.toastProgress))
        }
    }
    LaunchedEffect(Unit) { if (window > durationMs) focus.requestFocus() }
}

/**
 * The one-line answer to a text that is not a task (components.md, 6; NUD-52). Stays 8 s, longer with TalkBack, which
 * also gets focus; the caller removes it sooner when the bar changes.
 */
@Composable
fun ReplyNote(
    text: String,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = DurtMotion.REPLY_NOTE_MS,
) {
    val accessibility = LocalAccessibilityManager.current
    val window = accessibility?.calculateRecommendedTimeoutMillis(durationMs, containsText = true) ?: durationMs
    val currentTimeout = rememberUpdatedState(onTimeout)
    val focus = remember { FocusRequester() }
    LaunchedEffect(text, window) {
        delay(window)
        currentTimeout.value()
    }
    ToastBox(modifier.focusRequester(focus).focusable()) {
        Text(
            text,
            style = DurtTheme.type.body,
            color = DurtTheme.colors.surface,
            modifier = Modifier.padding(DurtTheme.dimens.space14),
        )
    }
    LaunchedEffect(text) { if (window > durationMs) focus.requestFocus() }
}

/** Shown for 4 s after a task completes, with an optional learning line (components.md, 6). */
@Composable
fun DoneNote(
    title: String,
    learning: String?,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ToastBox(modifier) {
        Row(
            Modifier.padding(dimens.space14),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
        ) {
            Box(
                Modifier.size(dimens.avatarCard).background(colors.success, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    DurtIcons.Check,
                    contentDescription = null,
                    tint = colors.surface,
                    modifier = Modifier.size(dimens.iconSmall),
                )
            }
            Column {
                Text(title, style = DurtTheme.type.bodyStrong, color = colors.surface)
                if (learning != null) Text(learning, style = DurtTheme.type.caption, color = colors.darkTextSecondary)
            }
        }
    }
}

/** Stays inside the screen and never auto-dismisses (components.md, 6). */
@Composable
fun InlineAlert(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = DurtIcons.Warning,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.warningSoft)
                .border(dimens.borderCard, colors.warningBorder, shape)
                .padding(dimens.space14)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Icon(icon, contentDescription = null, tint = colors.warning, modifier = Modifier.size(dimens.icon))
        Column {
            Text(title, style = DurtTheme.type.bodyStrong, color = colors.ink)
            Text(body, style = DurtTheme.type.caption, color = colors.textBody)
        }
    }
}

/** Ongoing state such as offline, right below the TopBar (components.md, 6). */
@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radiusS))
                .background(colors.ink)
                .padding(horizontal = dimens.space14, vertical = dimens.space10)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space10),
    ) {
        Box(Modifier.size(dimens.statusDot).background(colors.accent, CircleShape))
        Text(text, style = DurtTheme.type.bodySmall, color = colors.surface)
    }
}

@PreviewComponent
@Composable
private fun FeedbackPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            UndoToast("Eren'e gitti", "Para çek · yarın 09:00", onUndo = {}, onEdit = {}, onTimeout = {}, photo = false)
            UndoToast("Eren'e gitti", "Para çek · yarın 09:00 · bitince fotoğraf", onUndo = {}, onEdit = {}, onTimeout = {}, photo = true)
            UndoToast("Gönderildi", "Eren · seni seviyorum · bugün 15:00", onUndo = {}, onEdit = {}, onTimeout = {})
            DoneNote("\"Para çek\" tamamlandı", "Bu sefer seni 2. dürtmede yakaladım.")
            ReplyNote("Merhaba! Kime ne hatırlatayım? Mesela: Eren yarın 9'dan önce ekmek alsın.", onTimeout = {})
            InlineAlert("Şu an anlayamadım", "Yazdığın duruyor, tekrar dene.")
            StatusBanner("İnternet yok. Dürtmeler yine zamanında çalar.")
        }
    }
}
