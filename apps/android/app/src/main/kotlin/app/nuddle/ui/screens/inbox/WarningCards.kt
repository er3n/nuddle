package app.nuddle.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nuddle.R
import app.nuddle.domain.SetupStep
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.DurtButtonSize
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewComponent
import app.nuddle.ui.preview.PreviewSurface
import app.nuddle.ui.theme.DurtTheme

@Composable
private fun BellWarning.title(): String =
    stringResource(
        when (this) {
            BellWarning.MissedRing -> {
                R.string.missed_ring_title
            }

            is BellWarning.PermissionOff -> {
                when (step) {
                    SetupStep.NOTIFICATIONS -> R.string.bell_notifications_off_title
                    SetupStep.EXACT_ALARM -> R.string.bell_alarms_off_title
                    SetupStep.FULL_SCREEN -> R.string.bell_full_screen_off_title
                    SetupStep.BATTERY -> R.string.bell_battery_on_title
                }
            }
        },
    )

@Composable
private fun BellWarning.body(): String =
    when (this) {
        BellWarning.MissedRing -> {
            stringResource(R.string.bell_missed_ring_body)
        }

        is BellWarning.PermissionOff -> {
            when (step) {
                SetupStep.NOTIFICATIONS -> {
                    stringResource(R.string.bell_notifications_off_body)
                }

                SetupStep.EXACT_ALARM -> {
                    stringResource(R.string.bell_alarms_off_body)
                }

                SetupStep.BATTERY -> {
                    stringResource(R.string.bell_battery_on_body)
                }

                SetupStep.FULL_SCREEN -> {
                    val next = next
                    when (next?.day) {
                        null -> stringResource(R.string.bell_full_screen_off_body)
                        PokeDay.Today -> stringResource(R.string.bell_full_screen_off_today, next.title)
                        PokeDay.Tomorrow -> stringResource(R.string.bell_full_screen_off_tomorrow, next.title)
                        PokeDay.Later -> stringResource(R.string.bell_full_screen_off_later, next.title)
                    }
                }
            }
        }
    }

private val BellWarning.tag: String
    get() =
        when (this) {
            BellWarning.MissedRing -> TestTags.BELL_MISSED_RING
            is BellWarning.PermissionOff -> TestTags.bellPermission(step.name)
        }

/** 2g: one card per warning, each with "Düzelt". */
@Composable
internal fun BellWarningCards(
    warnings: List<BellWarning>,
    onFix: (BellWarning) -> Unit,
) {
    warnings.forEach { WarningCard(it, onFix = { onFix(it) }) }
}

@Composable
private fun WarningCard(
    warning: BellWarning,
    onFix: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusXl)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(dimens.borderCard, colors.border, shape)
                .padding(dimens.space16)
                .testTag(warning.tag),
        verticalArrangement = Arrangement.spacedBy(dimens.space14),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.space12)) {
            Box(Modifier.size(dimens.avatarInbox).background(colors.warningSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(DurtIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(dimens.iconLarge))
            }
            Column(Modifier.weight(1f)) {
                Text(warning.title(), style = DurtTheme.type.bodyStrong, color = colors.ink)
                Text(warning.body(), style = DurtTheme.type.bodySmall, color = colors.textSecondary)
            }
            Box(Modifier.padding(top = dimens.space6).size(dimens.unreadDot).background(colors.poke, CircleShape))
        }
        DurtButton(
            text = stringResource(R.string.bell_fix),
            onClick = onFix,
            kind = DurtButtonKind.Primary,
            size = DurtButtonSize.Medium,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.bellFix(warning.tag)),
        )
    }
}

@PreviewComponent
@Composable
private fun BellWarningCardsPreview() {
    PreviewSurface {
        BellWarningCards(
            listOf(
                BellWarning.MissedRing,
                BellWarning.PermissionOff(SetupStep.FULL_SCREEN, NextPokeUi("Para çek", PokeDay.Tomorrow)),
                BellWarning.PermissionOff(SetupStep.EXACT_ALARM),
            ),
            onFix = {},
        )
    }
}
