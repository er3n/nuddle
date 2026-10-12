package app.nuddle.ui.nav

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.nuddle.data.InviteLinks
import app.nuddle.ui.screens.LightSystemBarIcons
import app.nuddle.ui.screens.code.CodeScreen
import app.nuddle.ui.screens.code.CodeViewModel
import app.nuddle.ui.screens.code.SignedInDestination
import app.nuddle.ui.screens.home.HomeScreen
import app.nuddle.ui.screens.home.HomeViewModel
import app.nuddle.ui.screens.home.PendingInviteCallbacks
import app.nuddle.ui.screens.home.PendingInviteViewModel
import app.nuddle.ui.screens.home.PreviewCallbacks
import app.nuddle.ui.screens.home.UndoCallbacks
import app.nuddle.ui.screens.name.NameScreen
import app.nuddle.ui.screens.name.NameViewModel
import app.nuddle.ui.screens.phone.PhoneScreen
import app.nuddle.ui.screens.phone.PhoneViewModel
import app.nuddle.ui.screens.welcome.WelcomeScreen
import app.nuddle.ui.screens.welcome.WelcomeUiState

/** Route names from docs/ux/screens.md (Routes). */
object Routes {
    const val WELCOME = "welcome"
    const val PHONE = "auth/phone"
    const val CODE = "auth/code?${CodeViewModel.ARG_PHONE}={${CodeViewModel.ARG_PHONE}}"
    const val NAME = "onboarding/name"
    const val HOME = "home"
    const val SETUP = "setup"
    const val INVITE = "invite"

    fun code(e164: String): String = "auth/code?${CodeViewModel.ARG_PHONE}=${Uri.encode(e164)}"
}

/** Back never returns to sign-in steps; `home` is the root of the back stack (docs/ux/platform.md, Navigation). */
private fun NavHostController.replaceAll(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun NuddleNavHost(startRoute: String) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startRoute) {
        composable(Routes.WELCOME) {
            LightSystemBarIcons()
            // TODO(open-questions#13): the invited variant (0b) opens from invite App Links, built with invites (slice 2).
            WelcomeScreen(
                state = WelcomeUiState(),
                onStart = { nav.navigate(Routes.PHONE) },
                // Terms and privacy texts do not exist yet (docs/human-checklist.md, Before the beta).
                onTerms = {},
                onPrivacy = {},
            )
        }
        composable(Routes.PHONE) {
            val viewModel: PhoneViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) { viewModel.codeSent.collect { nav.navigate(Routes.code(it)) } }
            PhoneScreen(state, viewModel::onNumberChange, viewModel::onSend, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.CODE,
            arguments = listOf(navArgument(CodeViewModel.ARG_PHONE) { type = NavType.StringType }),
        ) {
            val viewModel: CodeViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.signedIn.collect { destination ->
                    nav.replaceAll(if (destination == SignedInDestination.Home) Routes.HOME else Routes.NAME)
                }
            }
            CodeScreen(
                state = state,
                onCodeChange = viewModel::onCodeChange,
                onComplete = viewModel::onComplete,
                onResend = viewModel::onResend,
                onChangeNumber = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.NAME) {
            val viewModel: NameViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) { viewModel.registered.collect { nav.replaceAll(Routes.HOME) } }
            NameScreen(state, viewModel::onNameChange, viewModel::onContinue)
        }
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val invites: PendingInviteViewModel = hiltViewModel()
            val activity = LocalActivity.current
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) { viewModel.openSetup.collect { nav.navigate(Routes.SETUP) { launchSingleTop = true } } }
            // Bell, profile and voice are routes of the feature slices (docs/bootstrap-plan.md).
            state?.let {
                HomeScreen(
                    state = it,
                    onBell = {},
                    onProfile = {},
                    onInvite = { nav.navigate(Routes.INVITE) { launchSingleTop = true } },
                    onComposeTextChange = viewModel::onComposeTextChange,
                    onComposeSend = viewModel::onComposeSend,
                    onVoiceStart = {},
                    onVoiceEnd = {},
                    onFinishNextUp = viewModel::onFinishNextUp,
                    preview =
                        PreviewCallbacks(
                            onChoice = viewModel::onPreviewChoice,
                            onTimePicked = viewModel::onPreviewTimePicked,
                            onConfirm = viewModel::onPreviewConfirm,
                            onDismiss = viewModel::onPreviewDismiss,
                        ),
                    undo =
                        UndoCallbacks(
                            onUndo = viewModel::onUndo,
                            onEdit = viewModel::onUndoEdit,
                            onTimeout = viewModel::onUndoTimeout,
                        ),
                    pendingInvite =
                        PendingInviteCallbacks(
                            onReshare = { activity?.shareInvite(InviteLinks.of(it.code)) },
                            onRevoke = invites::onRevoke,
                        ),
                )
            }
        }
        composable(Routes.INVITE) {
            InviteRoute(onBack = { nav.popBackStack() }, onShare = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.SETUP) {
            SetupRoute(onFinish = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
    }
}
