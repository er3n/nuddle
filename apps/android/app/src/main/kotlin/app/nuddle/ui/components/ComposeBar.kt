package app.nuddle.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.nuddle.R
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
}

private val ComposeState.text: String
    get() =
        when (this) {
            is ComposeState.Typing -> text
            is ComposeState.Failed -> text
            ComposeState.Idle, ComposeState.Sending -> ""
        }

/**
 * Text and voice input for a new task (docs/ux/components.md, 3). Idle shows the orange mic; with text the mic
 * turns into a send arrow in the same place. Hold the mic to record (screen 4h, feature slice 9), a short tap
 * shows the "Basılı tut" hint for 2 s. Callers place it above the keyboard with `imePadding()`.
 */
@Composable
fun ComposeBar(
    state: ComposeState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    var focused by remember { mutableStateOf(false) }
    var showHoldHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHoldHint) {
        if (showHoldHint) {
            delay(DurtMotion.HINT_BUBBLE_MS)
            showHoldHint = false
        }
    }
    val text = state.text
    val active = focused || text.isNotEmpty()
    val shape = RoundedCornerShape(dimens.radiusXl)
    val label = stringResource(R.string.compose_label)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (showHoldHint) {
            Text(
                text = stringResource(R.string.compose_hold_hint),
                style = DurtTheme.type.captionStrong,
                color = colors.surface,
                modifier =
                    Modifier
                        .padding(bottom = dimens.space8)
                        .background(colors.toastBackground, RoundedCornerShape(dimens.radiusS))
                        .padding(horizontal = dimens.space12, vertical = dimens.space6),
            )
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
                        .semantics { contentDescription = label },
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
            when (state) {
                is ComposeState.Failed -> {
                    DurtButton(
                        text = stringResource(R.string.compose_retry),
                        onClick = onSend,
                        kind = DurtButtonKind.Accent,
                        size = DurtButtonSize.Small,
                        modifier = Modifier.weight(1f, fill = false),
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
                        targetState = text.isNotEmpty(),
                        animationSpec = tween(DurtMotion.SHORT_MS),
                        label = "micToSend",
                    ) { hasText ->
                        if (hasText) {
                            SendButton(onSend)
                        } else {
                            MicButton(onVoiceStart = onVoiceStart, onVoiceEnd = onVoiceEnd, onShortTap = {
                                showHoldHint =
                                    true
                            })
                        }
                    }
                }
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
                .semantics { contentDescription = description },
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

@Composable
private fun MicButton(
    onVoiceStart: () -> Unit,
    onVoiceEnd: (cancelled: Boolean) -> Unit,
    onShortTap: () -> Unit,
) {
    val description = stringResource(R.string.cd_mic)
    Box(
        modifier =
            Modifier
                .size(DurtTheme.dimens.composeMic)
                .clip(CircleShape)
                .background(DurtTheme.colors.accent)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onShortTap() },
                        onLongPress = { onVoiceStart() },
                        onPress = {
                            // TODO(open-questions#6): slide left to cancel arrives with voice input (screen 4h).
                            val released = tryAwaitRelease()
                            onVoiceEnd(!released)
                        },
                    )
                },
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
        }
    }
}
