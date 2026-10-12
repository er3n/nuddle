package app.nuddle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import app.nuddle.R
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

enum class PermissionStepState { Done, Active, Next }

private const val DASH = 8f

/**
 * One setup step (docs/ux/components.md, 9, screen 2e). Done: green tick and "Tamam". Active: 2 dp `ink` border,
 * description, an optional hint box ("Açılan sayfada ...") and "Ayarları aç" (or [action]). Next: dashed and gray.
 */
@Composable
fun PermissionStep(
    index: Int,
    title: String,
    state: PermissionStepState,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    badge: String? = null,
    description: String? = null,
    hint: AnnotatedString? = null,
    action: String? = null,
    onOpenSettings: () -> Unit = {},
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusM)
    val frame =
        when (state) {
            PermissionStepState.Done -> {
                Modifier.background(colors.surface, shape).border(dimens.borderCard, colors.border, shape)
            }

            PermissionStepState.Active -> {
                Modifier.background(colors.surface, shape).border(dimens.borderFocused, colors.ink, shape)
            }

            PermissionStepState.Next -> {
                Modifier.drawBehind {
                    val radius = dimens.radiusM.toPx()
                    drawRoundRect(
                        color = colors.borderStrong,
                        cornerRadius = CornerRadius(radius, radius),
                        style =
                            Stroke(
                                width = dimens.borderStrong.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
                            ),
                    )
                }
            }
        }
    Column(
        modifier = modifier.fillMaxWidth().then(frame).padding(dimens.space14),
        verticalArrangement = Arrangement.spacedBy(dimens.space12),
    ) {
        Row(
            modifier = Modifier.heightIn(min = dimens.avatarInline),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
        ) {
            StepMarker(index, state)
            Text(
                title,
                style = DurtTheme.type.bodyStrong,
                color = if (state == PermissionStepState.Next) colors.textTertiary else colors.ink,
                modifier = Modifier.weight(1f),
            )
            when {
                state == PermissionStepState.Done -> {
                    Text(
                        stringResource(R.string.action_ok),
                        style = DurtTheme.type.captionStrong,
                        color = colors.success,
                    )
                }

                badge != null && state == PermissionStepState.Active -> {
                    StatusBadge(badge)
                }

                badge != null -> {
                    Text(badge, style = DurtTheme.type.caption, color = colors.textTertiary)
                }
            }
        }
        if (state == PermissionStepState.Active) {
            ActiveDetails(description, hint, action, onOpenSettings, modifier = actionModifier)
        }
    }
}

/** The number in a circle, or a green tick once done. */
@Composable
private fun StepMarker(
    index: Int,
    state: PermissionStepState,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val circle = Modifier.size(dimens.avatarInline)
    when (state) {
        PermissionStepState.Done -> {
            Box(circle.background(colors.successSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    DurtIcons.Check,
                    contentDescription = null,
                    tint = colors.onSuccessSoft,
                    modifier = Modifier.size(dimens.iconSmall),
                )
            }
        }

        PermissionStepState.Active -> {
            Box(circle.background(colors.ink, CircleShape), contentAlignment = Alignment.Center) {
                Text(index.toString(), style = DurtTheme.type.micro, color = colors.surface)
            }
        }

        PermissionStepState.Next -> {
            Box(
                circle.border(dimens.borderStrong, colors.borderStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(index.toString(), style = DurtTheme.type.micro, color = colors.textTertiary)
            }
        }
    }
}

/** Description, the gray hint box and the button of the active step. */
@Composable
private fun ColumnScope.ActiveDetails(
    description: String?,
    hint: AnnotatedString?,
    action: String?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    if (description != null) Text(description, style = DurtTheme.type.bodySmall, color = colors.textBody)
    if (hint != null) {
        Text(
            hint,
            style = DurtTheme.type.caption,
            color = colors.textBody,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.background, RoundedCornerShape(dimens.radiusS))
                    .padding(horizontal = dimens.space12, vertical = dimens.space10),
        )
    }
    DurtButton(
        text = action ?: stringResource(R.string.action_open_settings),
        onClick = onOpenSettings,
        kind = DurtButtonKind.Accent,
        size = DurtButtonSize.Medium,
        modifier = modifier,
    )
}

@Composable
private fun StatusBadge(text: String) {
    val dimens = DurtTheme.dimens
    Text(
        text = text,
        style = DurtTheme.type.micro,
        color = DurtTheme.colors.onWarningPill,
        modifier =
            Modifier
                .background(DurtTheme.colors.warningPill, RoundedCornerShape(dimens.radiusPill))
                .padding(horizontal = dimens.pillPaddingHorizontal, vertical = dimens.space4),
    )
}

@PreviewComponent
@Composable
private fun PermissionStepPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(DurtTheme.dimens.space12)) {
            PermissionStep(1, "Bildirimler", PermissionStepState.Done)
            PermissionStep(
                2,
                "Alarmlar ve hatırlatıcılar",
                PermissionStepState.Active,
                badge = "Gerekli",
                description = "Dürtmelerin tam saatinde çalması için.",
                hint = AnnotatedString("Açılan sayfada izni aç, sonra geri dön."),
            )
            PermissionStep(3, "Tam ekran dürtme", PermissionStepState.Next, badge = "Sırada")
        }
    }
}
