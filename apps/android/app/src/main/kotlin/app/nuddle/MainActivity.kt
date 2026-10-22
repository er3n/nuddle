package app.nuddle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.nuddle.alarm.AlarmVolume
import app.nuddle.alarm.PokeNotifications
import app.nuddle.data.InviteLinks
import app.nuddle.data.SharedImage
import app.nuddle.data.SharedImages
import app.nuddle.data.SharedText
import app.nuddle.data.avatar.AvatarFiles
import app.nuddle.data.sync.Syncer
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.routeShown
import app.nuddle.ui.TestTagsRoot
import app.nuddle.ui.components.LocalAvatars
import app.nuddle.ui.nav.NuddleNavHost
import app.nuddle.ui.nav.Routes
import app.nuddle.ui.nav.StartViewModel
import app.nuddle.ui.rememberAvatarImages
import app.nuddle.ui.theme.DurtTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The single activity (docs/ux/platform.md, Navigation). `PokeActivity` is the only other one. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val start: StartViewModel by viewModels()

    @Inject lateinit var syncer: Syncer

    @Inject lateinit var pokes: PokeNotifications

    @Inject lateinit var alarmVolume: AlarmVolume

    @Inject lateinit var avatarFiles: AvatarFiles

    @Inject lateinit var diagnostics: Diagnostics

    /** The code of an invite link that opened the app, until home has acted on it. */
    private val inviteCode = MutableStateFlow<String?>(null)

    /** Text shared from another app (NUD-127), until home has put it in the compose box. */
    private val sharedText = MutableStateFlow<String?>(null)

    /** An image shared from another app (NUD-138), until home has attached it in the compose bar. */
    private val sharedImage = MutableStateFlow<SharedImage?>(null)

    /** A screen a poke or a notification asked for (the camera for a photo task, an approval), until home opens it. */
    private val openRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics.log("main start ${opener(intent)}")
        if (savedInstanceState == null) {
            inviteCode.value = InviteLinks.codeOf(intent?.dataString)
            openRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
            sharedText.value = SharedText.of(intent)
            sharedImage.value = SharedImages.of(intent)
        } else {
            // Kept through a rotation on the sign-in screens, before home takes it.
            sharedText.value = savedInstanceState.getString(STATE_SHARED_TEXT)
            sharedImage.value =
                savedInstanceState.getString(STATE_SHARED_IMAGE)?.let {
                    SharedImage(it.toUri(), savedInstanceState.getString(STATE_SHARED_IMAGE_TEXT))
                }
        }
        enableEdgeToEdge()
        setContent {
            val route by start.startRoute.collectAsStateWithLifecycle()
            val code by inviteCode.collectAsStateWithLifecycle()
            val open by openRoute.collectAsStateWithLifecycle()
            val shared by sharedText.collectAsStateWithLifecycle()
            val sharedPicture by sharedImage.collectAsStateWithLifecycle()
            val avatars by rememberAvatarImages(avatarFiles)
            DurtTheme {
                TestTagsRoot {
                    CompositionLocalProvider(LocalAvatars provides avatars) {
                        route?.let {
                            NuddleNavHost(
                                startRoute = it,
                                inviteCode = code,
                                onInviteCodeTake = { inviteCode.value = null },
                                openRoute = open,
                                onOpenRouteTake = { openRoute.value = null },
                                sharedText = shared,
                                onSharedTextTake = { sharedText.value = null },
                                sharedImage = sharedPicture,
                                onSharedImageTake = { sharedImage.value = null },
                                onRouteChange = diagnostics::routeShown,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sharedText.value?.let { outState.putString(STATE_SHARED_TEXT, it) }
        sharedImage.value?.let {
            outState.putString(STATE_SHARED_IMAGE, it.uri.toString())
            outState.putString(STATE_SHARED_IMAGE_TEXT, it.text)
        }
    }

    /** Back in front: what the other phone changed meanwhile shows at once, even if its push was lost (NUD-59). */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { syncer.sync() }
        // A poke swiped away rang at a raised alarm volume; with none left, the person's own level comes back (NUD-68).
        if (pokes.showing().isEmpty()) alarmVolume.restore()
    }

    /** singleTop: a link opened while the app runs arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        diagnostics.log("main intent ${opener(intent)}")
        InviteLinks.codeOf(intent.dataString)?.let { inviteCode.value = it }
        intent.getStringExtra(EXTRA_ROUTE)?.let { openRoute.value = it }
        SharedText.of(intent)?.let { sharedText.value = it }
        SharedImages.of(intent)?.let { sharedImage.value = it }
    }

    companion object {
        private const val EXTRA_ROUTE = "route"
        private const val STATE_SHARED_TEXT = "sharedText"
        private const val STATE_SHARED_IMAGE = "sharedImage"
        private const val STATE_SHARED_IMAGE_TEXT = "sharedImageText"

        /**
         * What opened the activity, for the breadcrumbs (ADR 0023): a screen a poke asked for (its route holds only a
         * task id), an invite link (never the link, which carries the code), a share from another app (never its
         * text), or the launcher.
         */
        private fun opener(intent: Intent?): String {
            val route = intent?.getStringExtra(EXTRA_ROUTE)
            return when {
                route != null -> "route $route"
                intent?.data != null -> "link"
                intent?.action == Intent.ACTION_SEND -> "share"
                else -> "launcher"
            }
        }

        /** Opens the camera (4d) for [taskId]: Bitti on the poke or its notification for a photo task. */
        fun takePhoto(
            context: Context,
            taskId: String,
        ): Intent = open(context, Routes.photo(taskId))

        /** Opens the approval (4f) of [taskId]: Gör on "Eren fotoğraf gönderdi". */
        fun approve(
            context: Context,
            taskId: String,
        ): Intent = open(context, Routes.approve(taskId))

        /** Opens my task's detail (7c): Gör on "Eren cevap vermiyor" (7a). */
        fun task(
            context: Context,
            taskId: String,
        ): Intent = open(context, Routes.task(taskId))

        /** Opens a message from Nuddle (ADR 0025): its notification. */
        fun announcement(
            context: Context,
            announcementId: String,
        ): Intent = open(context, Routes.announcement(announcementId))

        // The route is also the data URI, which keeps PendingIntents for different screens apart.
        private fun open(
            context: Context,
            route: String,
        ): Intent =
            Intent(context, MainActivity::class.java)
                .setData("nuddle://$route".toUri())
                .putExtra(EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
