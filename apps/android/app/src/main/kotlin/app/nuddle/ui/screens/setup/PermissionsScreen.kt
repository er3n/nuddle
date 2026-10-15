package app.nuddle.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import app.nuddle.R
import app.nuddle.domain.SetupStep
import app.nuddle.ui.TestTags
import app.nuddle.ui.components.PermissionStep
import app.nuddle.ui.components.PermissionStepState
import app.nuddle.ui.components.TextLink
import app.nuddle.ui.preview.PreviewScreens
import app.nuddle.ui.screens.ScreenLayout
import app.nuddle.ui.theme.DurtTheme

/**
 * 2e: the steps in order, the active one open with its button. Granted steps show "Tamam"; the alarm and full screen
 * steps can be skipped with the link at the bottom, notifications cannot.
 */
@Composable
fun PermissionsScreen(
    state: SetupUiState.Steps,
    onOpenActive: () -> Unit,
    onSkipActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    ScreenLayout(
        background = colors.background,
        modifier = modifier.testTag(TestTags.SETUP_STEPS),
        bottom = {
            Text(
                text = stringResource(R.string.setup_footer),
                style = DurtTheme.type.micro.copy(fontWeight = FontWeight.Normal),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
            skipText(state.active)?.let {
                TextLink(
                    text = it,
                    onClick = onSkipActive,
                    color = colors.textTertiary,
                    style = DurtTheme.type.caption,
                    modifier = Modifier.testTag(TestTags.SETUP_SKIP_STEP),
                )
            }
        },
    ) {
        SetupHeader(
            title = title(state),
            body = body(state),
            modifier = Modifier.padding(top = dimens.space32),
        )
        StepsProgress(done = state.steps.count { it.granted }, total = state.steps.size)
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space10)) {
            state.steps.forEachIndexed { index, step ->
                StepRow(index + 1, step, active = step.step == state.active, onOpenActive = onOpenActive)
            }
        }
    }
}

@Composable
private fun title(state: SetupUiState.Steps): String =
    if (state.settingsCount == 0) {
        stringResource(R.string.setup_steps_title_notifications)
    } else {
        pluralStringResource(R.plurals.setup_steps_title, state.settingsCount, state.settingsCount)
    }

// TODO(open-questions#30): the design has only the body for two settings left with notifications allowed.
@Composable
private fun body(state: SetupUiState.Steps): String? =
    when {
        !state.notificationsGranted && state.settingsCount > 0 -> stringResource(R.string.setup_steps_body_notifications)
        !state.notificationsGranted -> null
        state.settingsCount == 1 -> stringResource(R.string.setup_steps_body_one)
        state.settingsCount == 2 -> stringResource(R.string.setup_steps_body_two)
        else -> stringResource(R.string.setup_steps_body_three)
    }

@Composable
private fun skipText(active: SetupStep): String? =
    when (active) {
        SetupStep.NOTIFICATIONS -> null
        SetupStep.EXACT_ALARM -> stringResource(R.string.setup_skip_alarm)
        SetupStep.FULL_SCREEN -> stringResource(R.string.setup_skip_full_screen)
        SetupStep.BATTERY -> stringResource(R.string.setup_skip_battery)
    }

@Composable
private fun StepsProgress(
    done: Int,
    total: Int,
) {
    val colors = DurtTheme.colors
    val dimens = DurtTheme.dimens
    val fraction = done.toFloat() / total
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.progressBar)
            .clip(RoundedCornerShape(dimens.radiusPill))
            .background(colors.border)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.success))
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: PermissionStepUi,
    active: Boolean,
    onOpenActive: () -> Unit,
) {
    val state =
        when {
            step.granted -> PermissionStepState.Done
            active -> PermissionStepState.Active
            else -> PermissionStepState.Next
        }
    val badge =
        when {
            state == PermissionStepState.Done -> null
            step.skipped -> stringResource(R.string.setup_skipped)
            active && step.step == SetupStep.EXACT_ALARM -> stringResource(R.string.setup_required)
            active -> null
            else -> stringResource(R.string.setup_next)
        }
    PermissionStep(
        index = index,
        title = stringResource(step.step.title),
        state = state,
        badge = badge,
        description = stringResource(step.step.body),
        hint = step.step.hint?.let { hint(stringResource(it)) },
        action =
            when (step.step) {
                SetupStep.NOTIFICATIONS -> stringResource(R.string.setup_step_notifications_action)
                SetupStep.BATTERY -> stringResource(R.string.setup_step_battery_action)
                else -> null
            },
        onOpenSettings = onOpenActive,
        actionModifier = Modifier.testTag(TestTags.SETUP_OPEN_ACTIVE),
    )
}

/** "**Açılan sayfada** ..." in the gray box of the active step. */
@Composable
private fun hint(rest: String): AnnotatedString {
    val lead = stringResource(R.string.setup_hint_lead)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
        append(' ')
        append(rest)
    }
}

private val SetupStep.title: Int
    get() =
        when (this) {
            SetupStep.NOTIFICATIONS -> R.string.setup_step_notifications
            SetupStep.EXACT_ALARM -> R.string.setup_step_alarm
            SetupStep.FULL_SCREEN -> R.string.setup_step_full_screen
            SetupStep.BATTERY -> R.string.setup_step_battery
        }

private val SetupStep.body: Int
    get() =
        when (this) {
            SetupStep.NOTIFICATIONS -> R.string.setup_step_notifications_body
            SetupStep.EXACT_ALARM -> R.string.setup_step_alarm_body
            SetupStep.FULL_SCREEN -> R.string.setup_step_full_screen_body
            SetupStep.BATTERY -> R.string.setup_step_battery_body
        }

/** The system dialog needs no instructions; the settings pages do. */
private val SetupStep.hint: Int?
    get() =
        when (this) {
            SetupStep.NOTIFICATIONS -> null
            SetupStep.EXACT_ALARM -> R.string.setup_step_alarm_hint
            SetupStep.FULL_SCREEN -> R.string.setup_step_full_screen_hint
            SetupStep.BATTERY -> null
        }

private fun previewSteps(
    notifications: Boolean,
    alarm: Boolean,
    skipped: Set<SetupStep> = emptySet(),
): List<PermissionStepUi> =
    listOf(
        PermissionStepUi(SetupStep.NOTIFICATIONS, notifications, skipped = false),
        PermissionStepUi(SetupStep.EXACT_ALARM, alarm, skipped = SetupStep.EXACT_ALARM in skipped),
        PermissionStepUi(SetupStep.FULL_SCREEN, granted = false, skipped = false),
    )

@PreviewScreens
@Composable
private fun PermissionsScreenAlarmPreview() {
    DurtTheme {
        PermissionsScreen(
            SetupUiState.Steps(
                settingsCount = 2,
                notificationsGranted = true,
                steps = previewSteps(notifications = true, alarm = false),
                active = SetupStep.EXACT_ALARM,
            ),
            onOpenActive = {},
            onSkipActive = {},
        )
    }
}

@PreviewScreens
@Composable
private fun PermissionsScreenFullScreenPreview() {
    DurtTheme {
        PermissionsScreen(
            SetupUiState.Steps(
                settingsCount = 2,
                notificationsGranted = true,
                steps = previewSteps(notifications = true, alarm = false, skipped = setOf(SetupStep.EXACT_ALARM)),
                active = SetupStep.FULL_SCREEN,
            ),
            onOpenActive = {},
            onSkipActive = {},
        )
    }
}

@PreviewScreens
@Composable
private fun PermissionsScreenNotificationsPreview() {
    DurtTheme {
        PermissionsScreen(
            SetupUiState.Steps(
                settingsCount = 1,
                notificationsGranted = false,
                steps = previewSteps(notifications = false, alarm = true),
                active = SetupStep.NOTIFICATIONS,
            ),
            onOpenActive = {},
            onSkipActive = {},
        )
    }
}
