package app.nuddle.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtDimens
import app.nuddle.ui.theme.DurtMotion
import app.nuddle.ui.theme.DurtTheme
import kotlin.math.roundToInt

/** How far the thumb can travel on a track [width] px wide. */
private fun DurtDimens.slideTravel(
    width: Int,
    density: Density,
): Float = with(density) { (width - slideThumb.toPx() - 2 * slideInset.toPx()).coerceAtLeast(0f) }

/**
 * A deliberate action (docs/ux/components.md, 10): only a slide past [DurtDimens.slideConfirm] of the way calls
 * [onConfirm], once; a tap or a short drag springs back. Accessibility services see one button named
 * [accessibilityLabel] whose click confirms, so nobody has to drag. Used for Bitti over the lock screen (3b, NUD-131).
 */
@Composable
fun SlideToConfirm(
    text: String,
    accessibilityLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = DurtTheme.dimens
    val density = LocalDensity.current
    val confirm by rememberUpdatedState(onConfirm)
    var travel by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    fun finish() {
        if (done) return
        done = true
        confirm()
    }
    val drag = rememberDraggableState { delta -> position = (position + delta).coerceIn(0f, travel) }
    SlideTrack(
        text = text,
        fraction =
            when {
                done -> 1f
                travel == 0f -> 0f
                else -> position / travel
            },
        modifier =
            modifier
                .onSizeChanged { travel = dimens.slideTravel(it.width, density) }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                    onClick(label = accessibilityLabel) {
                        finish()
                        true
                    }
                }.draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    enabled = !done,
                    onDragStopped = {
                        if (travel > 0f && position >= travel * dimens.slideConfirm) {
                            finish()
                        } else {
                            animate(position, 0f, animationSpec = tween(DurtMotion.MEDIUM_MS)) { value, _ -> position = value }
                        }
                    },
                ),
    )
}

/** The track at [fraction] of the way (0 idle, 1 done). */
@Composable
private fun SlideTrack(
    text: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = dimens.slideHeight)
            .clip(RoundedCornerShape(dimens.radiusPill))
            .background(colors.pokeDeep),
    ) {
        val travel = dimens.slideTravel(constraints.maxWidth, density)
        Text(
            text = text,
            style = DurtTheme.type.buttonPoke,
            color = colors.surface.copy(alpha = 1f - fraction),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(
                        start = dimens.slideThumb + dimens.slideInset * 2,
                        end = dimens.space16,
                        top = dimens.space8,
                        bottom = dimens.space8,
                    ),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((travel * fraction).roundToInt(), 0) }
                .padding(dimens.slideInset)
                .size(dimens.slideThumb)
                .background(colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DurtIcons.Check, contentDescription = null, tint = colors.pokeDeep, modifier = Modifier.size(dimens.iconLarge))
        }
    }
}

/** Idle: the thumb on the left, the label in full. */
@PreviewComponent
@Composable
private fun SlideToConfirmPreview() {
    SlidePreview { SlideToConfirm("Kaydırarak bitir", accessibilityLabel = "Bitti", onConfirm = {}) }
}

/** Dragging: half way, the label fading. */
@PreviewComponent
@Composable
private fun SlideToConfirmDraggingPreview() {
    SlidePreview { SlideTrack("Kaydırarak bitir", fraction = 0.5f) }
}

/** Done: the thumb at the end. */
@PreviewComponent
@Composable
private fun SlideToConfirmDonePreview() {
    SlidePreview { SlideTrack("Kaydırarak bitir", fraction = 1f) }
}

@Composable
private fun SlidePreview(content: @Composable () -> Unit) {
    PreviewSurface {
        Box(
            Modifier
                .background(DurtTheme.colors.poke)
                .padding(DurtTheme.dimens.space16),
        ) { content() }
    }
}
