package app.nuddle.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.nuddle.MainActivity
import app.nuddle.api.model.FinishMode
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.isMoment
import app.nuddle.data.example.ExampleFiles
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeRules
import app.nuddle.domain.SnoozeRules
import app.nuddle.ui.TestTagsRoot
import app.nuddle.ui.nav.dial
import app.nuddle.ui.nav.message
import app.nuddle.ui.screens.LightSystemBarIcons
import app.nuddle.ui.screens.hourMinute
import app.nuddle.ui.screens.poke.DropCallbacks
import app.nuddle.ui.screens.poke.DropSheet
import app.nuddle.ui.screens.poke.DropUi
import app.nuddle.ui.screens.poke.PokeContactUi
import app.nuddle.ui.screens.poke.PokeScreen
import app.nuddle.ui.screens.poke.PokeUiState
import app.nuddle.ui.screens.poke.SnoozeUi
import app.nuddle.ui.theme.DurtTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** The sender's picture the phone keeps for the task (NUD-138); none when it did not arrive or cannot be read. */
private suspend fun ExampleFiles.photoOf(taskId: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching { current(taskId) }.getOrNull()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

/**
 * Closed on the other phone or on the server while it is up (cancelled, missed): the poke goes (NUD-64). So does a
 * snooze from the notification (NUD-157), or a second Ertele here would use up the last one.
 */
@Composable
private fun CloseWhenAnsweredElsewhere(
    tasks: TaskDao,
    key: AlarmKey,
    close: () -> Unit,
) {
    val onClose by rememberUpdatedState(close)
    LaunchedEffect(key) {
        val snoozes = tasks.get(key.taskId)?.snoozesUsed ?: 0
        tasks.observe(key.taskId).first { it == null || !it.toLocalState().open || it.snoozesUsed > snoozes }
        onClose()
    }
}

/**
 * The full screen poke over the lock screen (3b). Opened by the `FULL_SCREEN` notification's full screen intent;
 * `showWhenLocked` and `turnScreenOn` are set in the manifest.
 */
@AndroidEntryPoint
class PokeActivity : ComponentActivity() {
    @Inject lateinit var tasks: TaskDao

    @Inject lateinit var diagnostics: Diagnostics

    @Inject lateinit var connections: ConnectionDao

    @Inject lateinit var alarms: PokeAlarms

    @Inject lateinit var actions: PokeActions

    @Inject lateinit var quiet: QuietPokes

    @Inject lateinit var clock: Clock

    @Inject lateinit var timeZone: TimeZone

    @Inject lateinit var examples: ExampleFiles

    /** The first volume key silenced the ring (NUD-155); later ones change the volume as usual. */
    private var silenced = false

    /** The poke on the screen; a newer one replaces it (NUD-157). */
    internal var shown by mutableStateOf(AlarmKey("", 0, 0))
        private set

    /** Over a locked phone Bitti is a slide (NUD-131, screens.md 3b). */
    private var locked by mutableStateOf(false)

    // The keyguard listener needs a system permission; the unlock broadcast is open to apps.
    private val unlocked =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = readLock()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        readLock()
        enableEdgeToEdge()
        val opened = PokeIntents.key(intent)
        if (opened == null) {
            finish()
            return
        }
        diagnostics.log("poke screen ${opened.taskId}")
        shown = opened
        setContent {
            DurtTheme {
                TestTagsRoot {
                    // A new poke starts its screen afresh: nothing of the one before (its sheet, its photo) stays.
                    key(shown) { PokeRoute(shown) }
                }
            }
        }
    }

    /**
     * singleTop: while 3b is up, the next full screen poke (the task's next step, or another task ringing at the same
     * time) arrives here and takes the screen over, with its own key for the answers and the volume key (NUD-157).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val key = PokeIntents.key(intent) ?: return
        diagnostics.log("poke screen ${key.taskId}")
        setIntent(intent)
        silenced = false
        shown = key
    }

    override fun onResume() {
        super.onResume()
        readLock()
    }

    override fun onStart() {
        super.onStart()
        // Unlocked while the poke is up (fingerprint, face): Bitti turns back into a button.
        ContextCompat.registerReceiver(this, unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(unlocked)
        super.onStop()
    }

    /** A volume key silences the ring, as on an alarm clock (NUD-155, screens.md 3b). */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val key = PokeIntents.key(intent)
        if (!volumeKey || silenced || key == null) {
            return super.onKeyDown(keyCode, event)
        }
        silenced = true
        lifecycleScope.launch { quiet.silence(key) }
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) readLock()
    }

    private fun readLock() {
        locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
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

    /** The dialer or the message chooser, once the lock screen is out of the way (docs/ux/platform.md, Intents). */
    private fun openContact(contact: PokeContactUi) {
        val open = {
            if (contact.message) message(contact.phone) else dial(contact.phone)
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = open()
                },
            )
        } else {
            open()
        }
    }

    @Composable
    private fun PokeRoute(key: AlarmKey) {
        var state by remember { mutableStateOf<PokeUiState?>(null) }
        var photo by remember { mutableStateOf(false) }
        // Who a drop note would go to; null for a task to self, which closes without the sheet (7f).
        var senderName by remember { mutableStateOf<String?>(null) }
        var drop by remember { mutableStateOf<DropUi?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(key) {
            val task = tasks.get(key.taskId)
            val step = alarms.step(key)
            if (task == null || step == null) {
                finish()
                return@LaunchedEffect
            }
            photo = task.finishMode == FinishMode.PHOTO.value
            val notice = task.finishMode == FinishMode.NOTICE.value
            if (task.senderId != task.recipientId) senderName = connections.get(task.senderId)?.displayName
            val time = step.plannedAt.toLocalDateTime(timeZone).time
            val deadline = Instant.fromEpochMilliseconds(task.deadline)
            state =
                PokeUiState(
                    pokeNumber = step.number,
                    time = time.format(HOUR_MINUTE),
                    title = task.title,
                    detail = task.detail,
                    timeLeft = PokeRules.timeLeft(clock.now(), deadline).takeUnless { task.isMoment },
                    snooze =
                        SnoozeRules.next(task.snoozesUsed, clock.now(), deadline)?.let {
                            SnoozeUi(it.until.hourMinute(timeZone), it.last)
                        },
                    notice = notice,
                    contact = task.contactPersonId?.takeUnless { notice }?.let { pokeContact(task, connections.get(it)) },
                    // The line the notification shows too (NUD-130); this screen only opens for a full screen ring.
                    pokeTitle = pokeTitle(step, PokeLevel.FULL_SCREEN).takeUnless { notice },
                    examplePhoto = examples.photoOf(task.id),
                )
        }
        CloseWhenAnsweredElsewhere(tasks, key, ::finish)
        LightSystemBarIcons()
        val current = state ?: return

        fun answer(block: suspend (AlarmKey) -> Unit) {
            scope.launch {
                block(key)
                finish()
            }
        }
        PokeScreen(
            state = current.copy(locked = locked),
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
            onContact = {
                current.contact?.let { contact ->
                    // Not an answer: the task stays open and the next pokes ring as planned (NUD-128).
                    actions.contact(key)
                    openContact(contact)
                }
            },
            onCantToday = {
                val name = senderName
                if (name == null) answer { actions.cantToday(it) } else drop = DropUi(name)
            },
        )
        drop?.let { sheet ->
            DropSheet(
                state = sheet,
                callbacks =
                    DropCallbacks(
                        onReason = { reason -> drop = sheet.copy(reason = reason.takeIf { it != sheet.reason }) },
                        onNoteChange = { note -> drop = sheet.copy(note = note) },
                        onDrop = { note -> answer { actions.cantToday(it, note) } },
                        onDismiss = { drop = null },
                    ),
            )
        }
    }
}
