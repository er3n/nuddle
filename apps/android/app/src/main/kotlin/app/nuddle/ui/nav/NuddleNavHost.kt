package app.nuddle.ui.nav

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import app.nuddle.ui.screens.consent.ConsentViewModel
import app.nuddle.ui.screens.home.HomeInvitesViewModel
import app.nuddle.ui.screens.home.HomeScreen
import app.nuddle.ui.screens.home.HomeViewModel
import app.nuddle.ui.screens.home.NextUpCallbacks
import app.nuddle.ui.screens.home.PendingInviteCallbacks
import app.nuddle.ui.screens.home.PreviewCallbacks
import app.nuddle.ui.screens.home.UndoCallbacks
import app.nuddle.ui.screens.inbox.InboxScreen
import app.nuddle.ui.screens.inbox.InboxViewModel
import app.nuddle.ui.screens.name.NameScreen
import app.nuddle.ui.screens.name.NameViewModel
import app.nuddle.ui.screens.phone.PhoneScreen
import app.nuddle.ui.screens.phone.PhoneViewModel
import app.nuddle.ui.screens.task.TaskDetailViewModel
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
    const val INBOX = "inbox"
    const val CONSENT =
        "consent/{${ConsentViewModel.ARG_INVITE}}?${ConsentViewModel.ARG_FROM_BELL}={${ConsentViewModel.ARG_FROM_BELL}}"
    const val ARG_NAME = "name"
    const val ARG_SETUP = "setup"
    const val MATCHED = "matched/{$ARG_NAME}?$ARG_SETUP={$ARG_SETUP}"
    const val TASK = "task/{${TaskDetailViewModel.ARG_TASK}}"

    fun task(taskId: String): String = "task/$taskId"

    fun consent(
        inviteId: String,
        fromBell: Boolean,
    ): String = "consent/$inviteId?${ConsentViewModel.ARG_FROM_BELL}=$fromBell"

    fun matched(
        inviterName: String,
        setup: Boolean,
    ): String = "matched/${Uri.encode(inviterName)}?$ARG_SETUP=$setup"

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
fun NuddleNavHost(
    startRoute: String,
    inviteCode: String? = null,
    onInviteCodeTake: () -> Unit = {},
) {
    val nav = rememberNavController()
    val takeInviteCode by rememberUpdatedState(onInviteCodeTake)
    NavHost(navController = nav, startDestination = startRoute) {
        composable(Routes.WELCOME) {
            LightSystemBarIcons()
            // The invited variant (0b) needs an invite preview before sign-in: NUD-38.
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
            val invites: HomeInvitesViewModel = hiltViewModel()
            val activity = LocalActivity.current
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) { viewModel.openSetup.collect { nav.navigate(Routes.SETUP) { launchSingleTop = true } } }
            LaunchedEffect(invites) {
                invites.openConsent.collect { nav.navigate(Routes.consent(it, fromBell = false)) { launchSingleTop = true } }
            }
            // An invite link: its consent screen, or the bell when the invite is not (yet) mine. Signed out, the
            // link waits here until sign-in reaches home.
            LaunchedEffect(inviteCode) {
                val code = inviteCode ?: return@LaunchedEffect
                takeInviteCode()
                val inviteId = invites.inviteForLink(code)
                nav.navigate(if (inviteId != null) Routes.consent(inviteId, fromBell = true) else Routes.INBOX) {
                    launchSingleTop = true
                }
            }
            val unread by invites.unread.collectAsStateWithLifecycle()
            // Bell, profile and voice are routes of the feature slices (docs/bootstrap-plan.md).
            state?.let {
                HomeScreen(
                    state = it.copy(unread = unread),
                    onBell = { nav.navigate(Routes.INBOX) { launchSingleTop = true } },
                    onProfile = {},
                    onInvite = { nav.navigate(Routes.INVITE) { launchSingleTop = true } },
                    onComposeTextChange = viewModel::onComposeTextChange,
                    onComposeSend = viewModel::onComposeSend,
                    onVoiceStart = {},
                    onVoiceEnd = {},
                    nextUpActions =
                        NextUpCallbacks(
                            onDone = viewModel::onFinishNextUp,
                            onCall = { activity?.dial(it) },
                            onMessage = { activity?.message(it) },
                        ),
                    onOpenTask = { nav.navigate(Routes.task(it)) { launchSingleTop = true } },
                    preview =
                        PreviewCallbacks(
                            onChoice = viewModel.previewSheet::onChoice,
                            onRecipient = viewModel.previewSheet::onRecipient,
                            onPhoto = viewModel.previewSheet::onPhoto,
                            onTimePicked = viewModel.previewSheet::onTimePicked,
                            onConfirm = viewModel::onPreviewConfirm,
                            onDismiss = viewModel.previewSheet::onDismiss,
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
        composable(Routes.TASK, arguments = listOf(navArgument(TaskDetailViewModel.ARG_TASK) { type = NavType.StringType })) {
            TaskDetailRoute(onBack = { nav.popBackStack() })
        }
        composable(Routes.INVITE) {
            InviteRoute(onBack = { nav.popBackStack() }, onShare = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.INBOX) {
            val viewModel: InboxViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            InboxScreen(
                state = state,
                onBack = { nav.popBackStack() },
                onOpenInvite = { nav.navigate(Routes.consent(it.inviteId, fromBell = true)) },
            )
        }
        composable(
            Routes.CONSENT,
            arguments =
                listOf(
                    navArgument(ConsentViewModel.ARG_INVITE) { type = NavType.StringType },
                    navArgument(ConsentViewModel.ARG_FROM_BELL) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
        ) {
            ConsentRoute(
                onAccept = { name, setup ->
                    nav.navigate(Routes.matched(name, setup)) { popUpTo(Routes.HOME) }
                },
                onClose = { nav.popBackStack() },
            )
        }
        composable(
            Routes.MATCHED,
            arguments =
                listOf(
                    navArgument(Routes.ARG_NAME) { type = NavType.StringType },
                    navArgument(Routes.ARG_SETUP) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
        ) { entry ->
            val args = checkNotNull(entry.arguments)
            val setup = args.getBoolean(Routes.ARG_SETUP)
            MatchedRoute(
                inviterName = args.getString(Routes.ARG_NAME).orEmpty(),
                onStart = {
                    if (setup) {
                        nav.navigate(Routes.SETUP) { popUpTo(Routes.HOME) }
                    } else {
                        nav.popBackStack(Routes.HOME, inclusive = false)
                    }
                },
            )
        }
        composable(Routes.SETUP) {
            SetupRoute(onFinish = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
    }
}
