package app.nuddle.ui.nav

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.domain.SetupStep
import app.nuddle.ui.screens.LightSystemBarIcons
import app.nuddle.ui.screens.poke.PokeDemoScreen
import app.nuddle.ui.screens.setup.PermissionsScreen
import app.nuddle.ui.screens.setup.SetupDemoScreen
import app.nuddle.ui.screens.setup.SetupEffect
import app.nuddle.ui.screens.setup.SetupUiState
import app.nuddle.ui.screens.setup.SetupViewModel

/**
 * The `setup` route (2c, 2d, 2e). The system parts live here: the notification dialog, and the settings pages for
 * exact alarms and full screen (docs/ux/platform.md, Permissions). Coming back to the app re-reads the permissions.
 */
@Composable
fun SetupRoute(
    onFinish: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val finish by rememberUpdatedState(onFinish)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // The dialog answers false at once after the second "Don't allow"; from then on only settings can turn it on.
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val notificationDialog =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onResume() }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SetupEffect.RequestNotifications -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        activity != null &&
                        canAskForNotifications(activity, askedOnce)
                    ) {
                        askedOnce = true
                        notificationDialog.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        activity?.startActivity(settingsIntent(activity, SetupStep.NOTIFICATIONS))
                    }
                }

                is SetupEffect.OpenSettings -> {
                    activity?.startActivity(settingsIntent(activity, effect.step))
                }

                SetupEffect.Finished -> {
                    finish()
                }
            }
        }
    }

    when (val current = state) {
        is SetupUiState.Intro -> {
            SetupDemoScreen(current, onTry = viewModel::onTry, onSkipDemo = viewModel::onStartSteps)
        }

        is SetupUiState.Demo -> {
            LightSystemBarIcons()
            PokeDemoScreen(current.settingsLeft, onContinue = viewModel::onStartSteps)
        }

        is SetupUiState.Steps -> {
            PermissionsScreen(current, onOpenActive = viewModel::onOpenActive, onSkipActive = viewModel::onSkipActive)
        }
    }
}

/**
 * API 33+ asks with a dialog until Android stops showing it (checked by the caller, so lint sees it); older versions
 * only have the settings page.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun canAskForNotifications(
    activity: Activity,
    askedOnce: Boolean,
): Boolean = !askedOnce || activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)

// Reason: an alarm app whose core function is ringing on time is the use case Play allows this for
// (docs/ux/platform.md, Permissions; open-questions#49). The rule flags the policy, not a defect.
@SuppressLint("BatteryLife")
internal fun settingsIntent(
    activity: Activity,
    step: SetupStep,
): Intent {
    val app = Uri.fromParts("package", activity.packageName, null)
    return when {
        step == SetupStep.EXACT_ALARM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app)
        }

        step == SetupStep.FULL_SCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app)
        }

        // The one-tap system dialog "always run in the background" (NUD-75, open-questions#49).
        step == SetupStep.BATTERY -> {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app)
        }

        // Notifications, and the steps on versions where they have no page of their own (granted there anyway).
        else -> {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        }
    }
}
