package app.nuddle.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.nuddle.MainActivity
import app.nuddle.api.model.FinishMode
import app.nuddle.data.db.TaskDao
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.PokeRules
import app.nuddle.domain.SnoozeRules
import app.nuddle.ui.TestTagsRoot
import app.nuddle.ui.screens.LightSystemBarIcons
import app.nuddle.ui.screens.poke.PokeScreen
import app.nuddle.ui.screens.poke.PokeUiState
import app.nuddle.ui.theme.DurtTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

private val HOUR_MINUTE =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }

/**
 * The full screen poke over the lock screen (3b). Opened by the `FULL_SCREEN` notification's full screen intent;
 * `showWhenLocked` and `turnScreenOn` are set in the manifest.
 */
@AndroidEntryPoint
class PokeActivity : ComponentActivity() {
    @Inject lateinit var tasks: TaskDao

    @Inject lateinit var alarms: PokeAlarms

    @Inject lateinit var actions: PokeActions

    @Inject lateinit var clock: Clock

    @Inject lateinit var timeZone: TimeZone

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        val key = PokeIntents.key(intent)
        if (key == null) {
            finish()
            return
        }
        setContent { DurtTheme { TestTagsRoot { PokeRoute(key) } } }
    }

    /** The manifest attributes cover API 27+; API 26 needs the window flags. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // Reason: the only way to show over the lock screen on API 26 (min SDK); deprecated from 27.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    @Composable
    private fun PokeRoute(key: AlarmKey) {
        var state by remember { mutableStateOf<PokeUiState?>(null) }
        var photo by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(key) {
            val task = tasks.get(key.taskId)
            val step = alarms.step(key)
            if (task == null || step == null) {
                finish()
                return@LaunchedEffect
            }
            photo = task.finishMode == FinishMode.PHOTO.value
            val time = step.plannedAt.toLocalDateTime(timeZone).time
            val deadline = Instant.fromEpochMilliseconds(task.deadline)
            state =
                PokeUiState(
                    pokeNumber = step.number,
                    time = time.format(HOUR_MINUTE),
                    title = task.title,
                    detail = task.detail,
                    timeLeft = PokeRules.timeLeft(clock.now(), deadline),
                    snooze = SnoozeRules.next(task.snoozesUsed, clock.now(), deadline),
                )
        }
        LightSystemBarIcons()
        val current = state ?: return

        fun answer(block: suspend (AlarmKey) -> Unit) {
            scope.launch {
                block(key)
                finish()
            }
        }
        PokeScreen(
            state = current,
            onDone = {
                if (photo) {
                    // A photo task ends with the photo (4d); the pokes go on until it is sent.
                    startActivity(MainActivity.takePhoto(this, key.taskId))
                    finish()
                } else {
                    answer(actions::done)
                }
            },
            onSnooze = { answer(actions::snooze) },
            onCantToday = { answer(actions::cantToday) },
        )
    }
}
