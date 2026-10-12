package app.nuddle.ui.nav

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.api.model.BrandFixTarget
import app.nuddle.ui.screens.missedring.MissedRingCallbacks
import app.nuddle.ui.screens.missedring.MissedRingScreen
import app.nuddle.ui.screens.missedring.MissedRingViewModel

/** The `missed-ring` route (2h). "Ayarı aç" opens the first of the step's pages that exists on this phone. */
@Composable
fun MissedRingRoute(
    onBack: () -> Unit,
    viewModel: MissedRingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    MissedRingScreen(
        state = state,
        callbacks =
            MissedRingCallbacks(
                onBack = onBack,
                onOpen = { stepId -> if (activity?.openFirst(viewModel.targets(stepId)) == true) viewModel.onOpened(stepId) },
                onRetry = viewModel::onRetry,
            ),
    )
}

private fun BrandFixTarget.intent(packageName: String): Intent? {
    val component = component
    val action = action
    return when {
        component != null -> ComponentName.unflattenFromString(component)?.let { Intent().setComponent(it) }
        action != null -> Intent(action).apply { if (withPackage == true) data = Uri.fromParts("package", packageName, null) }
        else -> null
    }
}

/** Brand pages differ by model and version: one that is missing or not exported is skipped. */
private fun Activity.openFirst(targets: List<BrandFixTarget>): Boolean =
    targets.mapNotNull { it.intent(packageName) }.any { intent ->
        try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
