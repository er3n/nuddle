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

    data class Failed(
        val text: String,
    ) : ComposeState

    data object Sending : ComposeState

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
            ComposeState.Idle, ComposeState.Sending, is ComposeState.Listening -> ""
        }

/**
 * Text and voice input for a new task (docs/ux/components.md, 3). Idle shows the orange mic; with text the mic
 * turns into a send arrow in the same place. Hold the mic to record (4h): [ComposeState.Listening] shows what is
 * heard, lifting the finger sends, sliding it left cancels (open-questions#6). A short tap shows the "Basılı tut" hint
 * for 2 s, [notice] a note about voice input. Callers place it above the keyboard with `imePadding()`.
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
    val mic = remember { MicPlace() }
    val cancelSlide = with(LocalDensity.current) { dimens.voiceCancelSlide.toPx() }
    var focused by remember { mutableStateOf(false) }
    var showHoldHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHoldHint) {
        if (showHoldHint) {
            delay(DurtMotion.HINT_BUBBLE_MS)
            showHoldHint = false
        }
    }
    val gestures = rememberUpdatedState(VoiceGestures(onVoiceStart, onVoiceEnd, onShortTap = { showHoldHint = true }))
    val text = state.text
    val active = focused || text.isNotEmpty()
    val shape = RoundedCornerShape(dimens.radiusXl)
    val label = stringResource(R.string.compose_label)
    val hint =
        when {
            notice != null -> stringResource(notice.text)
            showHoldHint -> stringResource(R.string.compose_hold_hint)
            else -> null
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned { mic.root = it }
                .holdToSpeak(mic, cancelSlide, gestures),
        horizontalAlignment = Alignment.End,
    ) {
        if (hint != null) HintBubble(hint)
        if (state is ComposeState.Listening) {
            Listening(state.heard)
            return@Column
        }
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
                    ).padding(start = dimens.space20, end = dimens.space6, top = dimens.space6, bottom = dimens.space6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space8),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = state != ComposeState.Sending,
                textStyle = (if (active) DurtTheme.type.input else DurtTheme.type.inputIdle).copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                maxLines = dimens.composeMaxLines,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = dimens.space8)
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
            BarAction(state, hasText = text.isNotEmpty(), onSend, mic)
        }
    }
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

/** The button at the end of the bar: Tekrar dene, a spinner while sending, or the mic that turns into send. */
@Composable
private fun RowScope.BarAction(
    state: ComposeState,
    hasText: Boolean,
    onSend: () -> Unit,
    mic: MicPlace,
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

        ComposeState.Sending -> {
            Box(Modifier.size(dimens.composeMic), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    Modifier.size(dimens.spinner),
                    color = colors.ink,
                    strokeWidth = dimens.spinnerStroke,
                )
            }
        }

        else -> {
            Crossfade(
                targetState = hasText,
                animationSpec = tween(DurtMotion.SHORT_MS),
                label = "micToSend",
            ) { showSend ->
                if (showSend) SendButton(onSend) else MicButton(mic)
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

/** 4h: "Dinliyorum" with the live transcript (the word still being heard lighter), the big mic and how to cancel. */
@Composable
private fun Listening(heard: String) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTags.VOICE_LISTENING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space24),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .border(dimens.borderFocused, colors.ink, shape)
                    .padding(horizontal = dimens.space20, vertical = dimens.space20)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(dimens.space8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
                Box(Modifier.size(dimens.statusDot).background(colors.poke, CircleShape))
                Text(stringResource(R.string.voice_listening), style = DurtTheme.type.captionStrong, color = colors.link)
            }
            if (heard.isNotEmpty()) {
                val settled = heard.substringBeforeLast(' ', missingDelimiterValue = "")
                val last = heard.substringAfterLast(' ')
                Text(
                    buildAnnotatedString {
                        if (settled.isNotEmpty()) append("$settled ")
                        withStyle(SpanStyle(color = colors.textMuted)) { append(last) }
                    },
                    style = DurtTheme.type.input,
                    color = colors.ink,
                    modifier = Modifier.testTag(TestTags.VOICE_HEARD),
                )
            }
        }
        VoiceMic()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
            Text(stringResource(R.string.voice_release), style = DurtTheme.type.bodyStrong, color = colors.ink)
            Text(stringResource(R.string.voice_cancel_hint), style = DurtTheme.type.caption, color = colors.textSecondary)
        }
    }
}

/** The big mic of 4h with a ring that keeps pulsing out of it (tokens.json, motion.voicePulse). */
@Composable
private fun VoiceMic() {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val progress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DurtMotion.VOICE_PULSE_MS, easing = LinearOutSlowInEasing)),
        label = "voicePulseProgress",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dimens.voiceMic)) {
        Box(
            Modifier
                .size(dimens.voiceMic)
                .graphicsLayer {
                    val scale = 1f + (DurtMotion.VOICE_PULSE_SCALE - 1f) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = DurtMotion.VOICE_PULSE_ALPHA * (1f - progress)
                }.background(colors.accent, CircleShape),
        )
        Box(
            Modifier.size(dimens.voiceMic).background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DurtIcons.Mic, contentDescription = null, tint = colors.ink, modifier = Modifier.size(dimens.iconHero))
        }
    }
}

/** Where the small mic is: a hold starts only on it, and only while it is shown. */
private class MicPlace {
    var root: LayoutCoordinates? = null
    var mic: LayoutCoordinates? = null

    fun bounds(): Rect? {
        val root = root?.takeIf { it.isAttached } ?: return null
        val mic = mic?.takeIf { it.isAttached } ?: return null
        return root.localBoundingBoxOf(mic)
    }
}

/**
 * The hold gesture lives on the whole bar, not on the mic: while listening the mic gives way to 4h's panel, and the
 * finger that is still down must keep its gesture. A tap shows the hint; a hold starts listening until the finger is
 * lifted, or slid left by `voiceCancelSlide`, which cancels at once.
 */
private fun Modifier.holdToSpeak(
    mic: MicPlace,
    cancelSlide: Float,
    gestures: State<VoiceGestures>,
): Modifier =
    pointerInput(mic, cancelSlide) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (mic.bounds()?.contains(down.position) != true) return@awaitEachGesture
            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() ?: down }
            if (early != null) {
                if (early !== down) gestures.value.onShortTap()
                return@awaitEachGesture
            }
            gestures.value.onStart()
            var cancelled: Boolean
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                cancelled = change != null && change.position.x - down.position.x < -cancelSlide
                val holding = change != null && change.pressed && !cancelled
            } while (holding)
            gestures.value.onEnd(cancelled)
        }
    }

private class VoiceGestures(
    val onStart: () -> Unit,
    val onEnd: (cancelled: Boolean) -> Unit,
    val onShortTap: () -> Unit,
)

@Composable
private fun MicButton(mic: MicPlace) {
    val description = stringResource(R.string.cd_mic)
    Box(
        modifier =
            Modifier
                .size(DurtTheme.dimens.composeMic)
                .clip(CircleShape)
                .background(DurtTheme.colors.accent)
                .onGloballyPositioned { mic.mic = it }
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }.testTag(TestTags.COMPOSE_MIC),
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
            ComposeBar(ComposeState.Failed("selin kuru temizlemeyi alsın"), {}, {}, {}, {})
            ComposeBar(ComposeState.Sending, {}, {}, {}, {})
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
