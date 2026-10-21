package app.nuddle.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtMotion
import app.nuddle.ui.theme.DurtTheme
import kotlinx.coroutines.delay

sealed interface ComposeState {
    data object Idle : ComposeState

    data class Typing(
        val text: String,
    ) : ComposeState

    /** [limitUntil]: refused over the daily write cap, when writing works again, "21:15" (6b, open-questions#54). */
    data class Failed(
        val text: String,
        val limitUntil: String? = null,
    ) : ComposeState

    /** On its way: [text] stays in the box, dimmed and not editable, so it does not look lost (NUD-54). */
    data class Sending(
        val text: String,
    ) : ComposeState

    /** Holding the mic (4h): [heard] is the live transcript. */
    data class Listening(
        val heard: String,
    ) : ComposeState
}

/** A short note by the mic about voice input (open-questions#40). */
enum class VoiceNotice { NotHeard, NoPermission, Unavailable }

private val ComposeState.text: String
    get() =
        when (this) {
            is ComposeState.Typing -> text
            is ComposeState.Failed -> text
            is ComposeState.Sending -> text
            ComposeState.Idle, is ComposeState.Listening -> ""
        }

/**
 * Text and voice input for a new task (docs/ux/components.md, 3). Idle shows the orange mic; with text the mic turns
 * into a send arrow in the same place. A tap on the mic dictates (4h, NUD-48): [ComposeState.Listening] shows the live
 * transcript in the box and the mic pulses as the stop button; a second tap ([onVoiceEnd]) or silence ends it, and the
 * text stays in the box to be sent with the arrow. [notice] shows a note about voice input. Callers place it above the
 * keyboard with `imePadding()`.
 */
@Composable
fun ComposeBar(
    state: ComposeState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    notice: VoiceNotice? = null,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    var focused by remember { mutableStateOf(false) }
    val listening = state is ComposeState.Listening
    val text = state.text
    val active = focused || text.isNotEmpty() || listening
    val shape = RoundedCornerShape(dimens.radiusXl)
    val label = stringResource(R.string.compose_label)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (notice != null) HintBubble(stringResource(notice.text))
        if (state is ComposeState.Listening) ListeningLine()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .border(
                        if (active) dimens.borderFocused else dimens.borderCard,
                        if (active) colors.ink else colors.border,
                        shape,
                    ).padding(start = dimens.space16, end = dimens.space4, top = dimens.space4, bottom = dimens.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space8),
        ) {
            if (state is ComposeState.Listening) {
                Heard(state.heard, Modifier.weight(1f))
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = state !is ComposeState.Sending,
                    textStyle =
                        (if (active) DurtTheme.type.input else DurtTheme.type.inputIdle).copy(
                            color = if (state is ComposeState.Sending) colors.textTertiary else colors.ink,
                        ),
                    cursorBrush = SolidColor(colors.ink),
                    maxLines = dimens.composeMaxLines,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(vertical = dimens.space6)
                            .onFocusChanged { focused = it.isFocused }
                            .semantics { contentDescription = label }
                            .testTag(TestTags.COMPOSE_INPUT),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.compose_placeholder),
                                    style = DurtTheme.type.inputIdle,
                                    color = colors.textTertiary,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            BarAction(state, hasText = text.isNotEmpty(), onSend, onVoiceStart, onVoiceEnd)
        }
    }
}

/** "Dinliyorum" with the red dot, above the bar while dictating. */
@Composable
private fun ListeningLine() {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = dimens.space16, bottom = dimens.space6).testTag(TestTags.VOICE_LISTENING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space8),
    ) {
        Box(Modifier.size(dimens.statusDot).background(colors.poke, CircleShape))
        Text(stringResource(R.string.voice_listening), style = DurtTheme.type.captionStrong, color = colors.link)
    }
}

/** The live transcript in the box, the word still being heard lighter. */
@Composable
private fun Heard(
    heard: String,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val settled = heard.substringBeforeLast(' ', missingDelimiterValue = "")
    val last = heard.substringAfterLast(' ')
    Text(
        buildAnnotatedString {
            if (settled.isNotEmpty()) append("$settled ")
            withStyle(SpanStyle(color = colors.textMuted)) { append(last) }
        },
        style = DurtTheme.type.input,
        color = colors.ink,
        maxLines = DurtTheme.dimens.composeMaxLines,
        modifier =
            modifier
                .padding(vertical = DurtTheme.dimens.space6)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(TestTags.VOICE_HEARD),
    )
}

/** "Basılı tut" or a voice note, over the mic. */
@Composable
private fun HintBubble(text: String) {
    val dimens = DurtTheme.dimens
    Text(
        text = text,
        style = DurtTheme.type.captionStrong,
        color = DurtTheme.colors.surface,
        modifier =
            Modifier
                .padding(bottom = dimens.space8)
                .background(DurtTheme.colors.toastBackground, RoundedCornerShape(dimens.radiusS))
                .padding(horizontal = dimens.space12, vertical = dimens.space6)
                .testTag(TestTags.COMPOSE_HINT),
    )
}

/** The button at the end of the bar: Tekrar dene, a spinner while sending, stop while dictating, or mic / send. */
@Composable
private fun RowScope.BarAction(
    state: ComposeState,
    hasText: Boolean,
    onSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: (cancelled: Boolean) -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    when (state) {
        is ComposeState.Failed -> {
            DurtButton(
                text = stringResource(R.string.compose_retry),
                onClick = onSend,
                kind = DurtButtonKind.Accent,
                size = DurtButtonSize.Small,
                modifier = Modifier.weight(1f, fill = false).testTag(TestTags.COMPOSE_RETRY),
            )
        }

        is ComposeState.Sending -> {
            Box(Modifier.size(dimens.composeMic), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    Modifier.size(dimens.spinner),
                    color = colors.ink,
                    strokeWidth = dimens.spinnerStroke,
                )
            }
        }

        is ComposeState.Listening -> {
            StopButton { onVoiceEnd(false) }
        }

        else -> {
            Crossfade(
                targetState = hasText,
                animationSpec = tween(DurtMotion.SHORT_MS),
                label = "micToSend",
            ) { showSend ->
                if (showSend) SendButton(onSend) else MicButton(onVoiceStart)
            }
        }
    }
}

@Composable
private fun SendButton(onSend: () -> Unit) {
    val description = stringResource(R.string.compose_send)
    Box(
        modifier =
            Modifier
                .size(DurtTheme.dimens.composeMic)
                .clip(CircleShape)
                .background(DurtTheme.colors.accent)
                .clickable(role = Role.Button, onClick = onSend)
                .semantics { contentDescription = description }
                .testTag(TestTags.COMPOSE_SEND),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            DurtIcons.Send,
            contentDescription = null,
            tint = DurtTheme.colors.ink,
            modifier = Modifier.size(DurtTheme.dimens.iconLarge),
        )
    }
}

/** The mic: a tap starts dictating (4h, NUD-48). */
@Composable
private fun MicButton(onVoiceStart: () -> Unit) {
    val description = stringResource(R.string.cd_mic)
    Box(
        modifier =
            Modifier
                .size(DurtTheme.dimens.composeMic)
                .clip(CircleShape)
                .background(DurtTheme.colors.accent)
                .clickable(role = Role.Button, onClick = onVoiceStart)
                .semantics { contentDescription = description }
                .testTag(TestTags.COMPOSE_MIC),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            DurtIcons.Mic,
            contentDescription = null,
            tint = DurtTheme.colors.ink,
            modifier = Modifier.size(DurtTheme.dimens.iconLarge),
        )
    }
}

/** While dictating the mic pulses (tokens.json, motion.voicePulse); a tap stops. */
@Composable
private fun StopButton(onStop: () -> Unit) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val description = stringResource(R.string.cd_mic_stop)
    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val progress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DurtMotion.VOICE_PULSE_MS, easing = LinearOutSlowInEasing)),
        label = "voicePulseProgress",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dimens.composeMic)) {
        Box(
            Modifier
                .size(dimens.composeMic)
                .graphicsLayer {
                    val scale = 1f + (DurtMotion.VOICE_PULSE_SCALE - 1f) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = DurtMotion.VOICE_PULSE_ALPHA * (1f - progress)
                }.background(colors.accent, CircleShape),
        )
        Box(
            Modifier
                .size(dimens.composeMic)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onStop)
                .semantics { contentDescription = description }
                .testTag(TestTags.COMPOSE_MIC_STOP),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DurtIcons.Mic, contentDescription = null, tint = colors.ink, modifier = Modifier.size(dimens.iconLarge))
        }
    }
}

private val VoiceNotice.text: Int
    get() =
        when (this) {
            VoiceNotice.NotHeard -> R.string.voice_not_heard
            VoiceNotice.NoPermission -> R.string.voice_no_permission
            VoiceNotice.Unavailable -> R.string.voice_unavailable
        }

@PreviewComponent
@Composable
private fun ComposeBarPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space16)) {
            ComposeBar(ComposeState.Idle, {}, {}, {}, {})
            ComposeBar(
                ComposeState.Typing("1000 tl çek"),
                {},
                {},
                {},
                {},
                Modifier.heightIn(min = DurtTheme.dimens.buttonLarge),
            )
            ComposeBar(ComposeState.Failed("sibel kuru temizlemeyi alsın"), {}, {}, {}, {})
            ComposeBar(ComposeState.Sending("1000 tl çek yarın 9da"), {}, {}, {}, {})
            ComposeBar(ComposeState.Idle, {}, {}, {}, {}, notice = VoiceNotice.NotHeard)
        }
    }
}

@PreviewComponent
@Composable
private fun ComposeBarListeningPreview() {
    PreviewSurface {
        ComposeBar(ComposeState.Listening("eren yarın dokuzdan önce temizlikçi için bin lira çeksin"), {}, {}, {}, {})
    }
}
