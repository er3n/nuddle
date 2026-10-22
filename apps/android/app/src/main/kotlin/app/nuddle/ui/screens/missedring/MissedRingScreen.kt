package app.nuddle.ui.screens.missedring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.nuddle.R
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.BackBar
import app.nuddle.ui.components.DurtButton
import app.nuddle.ui.components.DurtButtonKind
import app.nuddle.ui.components.InlineAlert
import app.nuddle.ui.components.PermissionStep
import app.nuddle.ui.components.PermissionStepState
import app.nuddle.ui.icons.DurtIcons
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.theme.DurtTheme

/** "dün 08:15", in the middle of a sentence. */
@Composable
private fun WrittenText.inSentence(): String =
    when (this) {
        is WrittenText.Today -> {
            stringResource(R.string.missed_ring_today, time)
        }

        is WrittenText.Yesterday -> {
            stringResource(R.string.missed_ring_yesterday, time)
        }

        is WrittenText.OnDate -> {
            stringResource(R.string.missed_ring_on_date, day, stringArrayResource(R.array.month_names)[month - 1], time)
        }
    }

/** "Seni dün 08:15 için dürtecektim, telefonun beni kapatmış. Xiaomi telefonlar ... İki ayarla düzelir." */
@Composable
private fun MissedRingUiState.body(): String {
    val words = stringArrayResource(R.array.missed_ring_count_words)
    val count =
        when (steps.size) {
            0 -> null
            1 -> stringResource(R.string.missed_ring_one_step)
            else -> stringResource(R.string.missed_ring_steps, words.getOrNull(steps.size - 2) ?: steps.size.toString())
        }
    val lost =
        when {
            reported -> stringResource(R.string.missed_ring_reported_body)
            lostAt != null -> stringResource(R.string.missed_ring_body, lostAt.inSentence())
            else -> stringResource(R.string.missed_ring_body_unknown)
        }
    return listOfNotNull(lost, reason, count).joinToString(" ")
}

/**
 * 2h: the poke that did not ring and the brand steps from the server, one at a time. A step counts as done once its
 * page was opened (or the phone can tell it is done). Opened from profile without a loss it asks instead of telling
 * (NUD-126). It ends with "Dürtmelerimi yeniden kur", which says so once this phone's alarms are set again.
 */
@Composable
fun MissedRingScreen(
    state: MissedRingUiState,
    callbacks: MissedRingCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.MISSED_RING_SCREEN),
        bottom = {
            Rearm(state.rearm, callbacks.onRearm)
            Text(
                stringResource(R.string.missed_ring_footer),
                style = DurtTheme.type.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        BackBar(title = null, onBack = callbacks.onBack)
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
            Text(
                stringResource(if (state.reported) R.string.missed_ring_reported_title else R.string.missed_ring_title),
                style = DurtTheme.type.onboardingTitle,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(state.body(), style = DurtTheme.type.bodyLarge, color = colors.textSecondary)
        }
        when {
            state.loading -> {
                CircularProgressIndicator(
                    color = colors.ink,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(dimens.space16),
                )
            }

            state.failed -> {
                InlineAlert(stringResource(R.string.profile_failed_title), stringResource(R.string.missed_ring_failed_body))
                DurtButton(
                    text = stringResource(R.string.missed_ring_retry),
                    onClick = callbacks.onRetry,
                    kind = DurtButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                state.steps.forEachIndexed { index, step ->
                    PermissionStep(
                        index = index + 1,
                        title = step.title,
                        state =
                            when {
                                step.done -> PermissionStepState.Done
                                step.id == state.active -> PermissionStepState.Active
                                else -> PermissionStepState.Next
                            },
                        description = step.hint.takeIf { step.id == state.active },
                        action = stringResource(R.string.missed_ring_open),
                        onOpenSettings = { callbacks.onOpen(step.id) },
                        modifier = Modifier.testTag(TestTags.brandStep(step.id)),
                        actionModifier = Modifier.testTag(TestTags.MISSED_RING_OPEN),
                    )
                }
            }
        }
    }
}

/** The last action, and in its place what it did. */
@Composable
private fun Rearm(
    rearm: RearmUi,
    onRearm: () -> Unit,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    if (rearm == RearmUi.Done) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space8),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(DurtIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(dimens.iconSmall))
            Text(stringResource(R.string.missed_ring_rearmed), style = DurtTheme.type.bodySmallStrong, color = colors.success)
        }
    } else {
        DurtButton(
            text = stringResource(R.string.missed_ring_rearm),
            onClick = onRearm,
            kind = DurtButtonKind.Secondary,
            loading = rearm == RearmUi.Busy,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.MISSED_RING_REARM),
        )
    }
}

internal val previewMissedRing =
    MissedRingUiState(
        lostAt = WrittenText.Yesterday("08:15"),
        reason = "Xiaomi telefonlar uygulamaları arka planda durdurabiliyor.",
        steps =
            listOf(
                BrandStepUi("autostart", "Otomatik başlatma", "Açılan sayfada \"dürt\"ün yanındaki düğmeyi aç.", done = true),
                BrandStepUi(
                    "battery",
                    "Pil tasarrufu: Kısıtlama yok",
                    "Açılan sayfada \"dürt\"ü bul, \"Kısıtlama yok\"u seç.",
                    done = false,
                ),
            ),
        loading = false,
    )

/** OemFix.png */
@PreviewScreens
@Composable
private fun MissedRingScreenPreview() {
    DurtTheme { MissedRingScreen(previewMissedRing, MissedRingCallbacks()) }
}

/** A phone without brand steps, before anything was done. */
@PreviewScreens
@Composable
private fun MissedRingScreenDefaultPreview() {
    DurtTheme {
        MissedRingScreen(
            MissedRingUiState(
                lostAt = WrittenText.Today("09:40"),
                steps =
                    listOf(
                        BrandStepUi(
                            id = "battery",
                            title = "Pil kullanımı: Kısıtlanmamış",
                            hint = "Açılan sayfada \"Pil\"e dokun, \"Kısıtlanmamış\"ı seç.",
                            done = false,
                        ),
                    ),
                loading = false,
            ),
            MissedRingCallbacks(),
        )
    }
}

/** Opened from profile without a loss (NUD-126), after "Dürtmelerimi yeniden kur". */
@PreviewScreens
@Composable
private fun MissedRingScreenReportedPreview() {
    DurtTheme {
        MissedRingScreen(
            previewMissedRing.copy(
                lostAt = null,
                reported = true,
                steps = previewMissedRing.steps.map { it.copy(done = false) },
                rearm = RearmUi.Done,
            ),
            MissedRingCallbacks(),
        )
    }
}

@PreviewScreens
@Composable
private fun MissedRingScreenLoadingPreview() {
    DurtTheme { MissedRingScreen(MissedRingUiState(lostAt = WrittenText.Yesterday("08:15")), MissedRingCallbacks()) }
}

@PreviewScreens
@Composable
private fun MissedRingScreenFailedPreview() {
    DurtTheme {
        MissedRingScreen(
            MissedRingUiState(lostAt = WrittenText.Yesterday("08:15"), loading = false, failed = true),
            MissedRingCallbacks(),
        )
    }
}
