package app.nuddle.testing

import app.nuddle.alarm.FullScreenPermission
import app.nuddle.alarm.PokeNotifications
import app.nuddle.api.model.FinishMode
import app.nuddle.data.AuthRepository
import app.nuddle.data.SessionStarter
import app.nuddle.data.TaskRepository
import app.nuddle.data.VerifyOutcome
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.outbox.OutboxDrainer
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.StoredSession
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The only clock in tests (ADR 0009). Starts at the glossary's sample morning. */
class FakeClock(
    var now: Instant = Instant.parse("2026-10-12T08:00:00+03:00"),
) : Clock {
    override fun now(): Instant = now

    fun advance(by: Duration) {
        now += by
    }
}

class InMemorySessionStore(
    initial: StoredSession? = null,
) : SessionStore {
    private val state = MutableStateFlow(initial)
    private val name = MutableStateFlow<String?>(null)
    var quiet: QuietHours? = null
    var device: String? = null
    var cursor: String? = null
    var cleared = false

    override val session: Flow<StoredSession?> = state
    override val displayName: Flow<String?> = name.map { it }

    override suspend fun current(): StoredSession? = state.value

    override suspend fun save(session: StoredSession) {
        state.value = session
    }

    override suspend fun markRegistered(displayName: String) {
        state.value = state.value?.copy(registered = true)
        name.value = displayName
    }

    override suspend fun saveMe(
        displayName: String,
        quietHours: QuietHours,
    ) {
        name.value = displayName
        quiet = quietHours
    }

    override suspend fun quietHours(): QuietHours? = quiet

    override suspend fun deviceId(): String? = device

    override suspend fun saveDeviceId(deviceId: String) {
        device = deviceId
    }

    override suspend fun syncCursor(): String? = cursor

    override suspend fun saveSyncCursor(cursor: String) {
        this.cursor = cursor
    }

    override suspend fun clear() {
        state.value = null
        cleared = true
    }
}

/** Records what the screens asked for; answers are set per test. */
class FakeAuthRepository : AuthRepository {
    val sentTo = mutableListOf<String>()
    val verified = mutableListOf<Pair<String, String>>()
    val registeredNames = mutableListOf<String>()
    var sendOk = true
    var verifyOutcome: VerifyOutcome = VerifyOutcome.SignedIn(registered = true)
    var registerOk = true

    override suspend fun sendOtp(e164: String): Boolean {
        sentTo += e164
        return sendOk
    }

    override suspend fun verify(
        e164: String,
        code: String,
    ): VerifyOutcome {
        verified += e164 to code
        return verifyOutcome
    }

    override suspend fun register(displayName: String): Boolean {
        registeredNames += displayName
        return registerOk
    }
}

class RecordingStarter : SessionStarter {
    var starts = 0

    override suspend fun start() {
        starts++
    }
}

class RecordingScheduler : AlarmScheduler {
    val scheduled = mutableMapOf<AlarmKey, PokeStep>()
    val cancelled = mutableListOf<AlarmKey>()

    override fun schedule(step: PokeStep) {
        scheduled[step.key] = step
    }

    override fun cancel(key: AlarmKey) {
        cancelled += key
        scheduled.remove(key)
    }
}

class RecordingNotifications : PokeNotifications {
    val shown = mutableListOf<Pair<String, PokeLevel>>()
    val cancelled = mutableListOf<String>()

    override fun show(
        task: TaskEntity,
        step: PokeStep,
        level: PokeLevel,
    ) {
        shown += task.id to level
    }

    override fun cancel(taskId: String) {
        cancelled += taskId
    }
}

class RecordingDrainer : OutboxDrainer {
    var kicks = 0

    override fun drainSoon() {
        kicks++
    }
}

class FakeFullScreen(
    var allowed: Boolean = true,
) : FullScreenPermission {
    override fun granted(): Boolean = allowed
}

class FakeTaskRepository : TaskRepository {
    /** Text and idempotency key of every write. */
    val written = mutableListOf<Pair<String, String>>()
    val done = mutableListOf<String>()
    var outcome: WriteOutcome = WriteOutcome.Created

    override suspend fun write(
        text: String,
        idempotencyKey: String,
    ): WriteOutcome {
        written += text to idempotencyKey
        return outcome
    }

    /** Title, deadline and key of every create from the preview. */
    val created = mutableListOf<Triple<String, Instant, String>>()
    var createOutcome: WriteOutcome = WriteOutcome.Created

    override suspend fun createForSelf(
        title: String,
        detail: String?,
        deadline: Instant,
        finishMode: FinishMode,
        rawText: String,
        idempotencyKey: String,
    ): WriteOutcome {
        created += Triple(title, deadline, idempotencyKey)
        return createOutcome
    }

    override suspend fun done(taskId: String) {
        done += taskId
    }
}

/** Tasks in memory; [observeAll] emits at once, unlike Room's flow on its own executor. */
class InMemoryTaskDao : TaskDao {
    private val tasks = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

    override suspend fun upsert(tasks: List<TaskEntity>) {
        this.tasks.value += tasks.associateBy { it.id }
    }

    override suspend fun get(id: String): TaskEntity? = tasks.value[id]

    override suspend fun all(): List<TaskEntity> = tasks.value.values.toList()

    override fun observeAll(): Flow<List<TaskEntity>> = tasks.map { it.values.toList() }

    override suspend fun setStatus(
        id: String,
        status: String,
    ) {
        tasks.value[id]?.let { tasks.value += id to it.copy(status = status) }
    }
}
