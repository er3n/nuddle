package app.nuddle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nuddle.data.InviteLinks
import app.nuddle.ui.TestTagsRoot
import app.nuddle.ui.nav.NuddleNavHost
import app.nuddle.ui.nav.Routes
import app.nuddle.ui.nav.StartViewModel
import app.nuddle.ui.theme.DurtTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

/** The single activity (docs/ux/platform.md, Navigation). `PokeActivity` is the only other one. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val start: StartViewModel by viewModels()

    /** The code of an invite link that opened the app, until home has acted on it. */
    private val inviteCode = MutableStateFlow<String?>(null)

    /** A screen a poke or a notification asked for (the camera for a photo task, an approval), until home opens it. */
    private val openRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            inviteCode.value = InviteLinks.codeOf(intent?.dataString)
            openRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        }
        enableEdgeToEdge()
        setContent {
            val route by start.startRoute.collectAsStateWithLifecycle()
            val code by inviteCode.collectAsStateWithLifecycle()
            val open by openRoute.collectAsStateWithLifecycle()
            DurtTheme {
                TestTagsRoot {
                    route?.let {
                        NuddleNavHost(
                            startRoute = it,
                            inviteCode = code,
                            onInviteCodeTake = { inviteCode.value = null },
                            openRoute = open,
                            onOpenRouteTake = { openRoute.value = null },
                        )
                    }
                }
            }
        }
    }

    /** singleTop: a link opened while the app runs arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        InviteLinks.codeOf(intent.dataString)?.let { inviteCode.value = it }
        intent.getStringExtra(EXTRA_ROUTE)?.let { openRoute.value = it }
    }

    companion object {
        private const val EXTRA_ROUTE = "route"

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
