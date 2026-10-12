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

    /** A photo task whose Bitti was tapped on the poke or its notification: the camera (4d) opens for it. */
    private val photoTask = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            inviteCode.value = InviteLinks.codeOf(intent?.dataString)
            photoTask.value = intent?.getStringExtra(EXTRA_PHOTO_TASK)
        }
        enableEdgeToEdge()
        setContent {
            val route by start.startRoute.collectAsStateWithLifecycle()
            val code by inviteCode.collectAsStateWithLifecycle()
            val photo by photoTask.collectAsStateWithLifecycle()
            DurtTheme {
                TestTagsRoot {
                    route?.let {
                        NuddleNavHost(
                            startRoute = it,
                            inviteCode = code,
                            onInviteCodeTake = { inviteCode.value = null },
                            photoTask = photo,
                            onPhotoTaskTake = { photoTask.value = null },
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
        intent.getStringExtra(EXTRA_PHOTO_TASK)?.let { photoTask.value = it }
    }

    companion object {
        private const val EXTRA_PHOTO_TASK = "photoTask"

        /** Opens the camera (4d) for [taskId]: Bitti on the poke or its notification for a photo task. */
        fun takePhoto(
            context: Context,
            taskId: String,
        ): Intent =
            Intent(context, MainActivity::class.java)
                .setData("nuddle://photo/$taskId".toUri())
                .putExtra(EXTRA_PHOTO_TASK, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
