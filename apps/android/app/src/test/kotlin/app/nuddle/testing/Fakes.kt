package app.nuddle.testing

import app.nuddle.alarm.FullScreenPermission
import app.nuddle.alarm.PokeNotifications
import app.nuddle.api.model.EdgeType
import app.nuddle.api.model.FinishMode
import app.nuddle.data.AuthRepository
import app.nuddle.data.InviteRepository
import app.nuddle.data.SessionStarter
import app.nuddle.data.TaskRepository
import app.nuddle.data.VerifyOutcome
import app.nuddle.data.WriteOutcome
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.outbox.OutboxDrainer
import app.nuddle.data.session.ConsentPrompts
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.SetupStore
import app.nuddle.data.session.StoredSession
import app.nuddle.data.sync.TaskAnnouncer
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.PermissionReader
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

class InMemorySetupStore(
    var done: Boolean = false,
) : SetupStore {
    override suspend fun done(): Boolean = done

    override suspend fun markDone() {
        done = true
    }
}

class InMemoryConsentPrompts : ConsentPrompts {
    val ids = mutableSetOf<String>()

    override suspend fun shown(inviteId: String): Boolean = inviteId in ids

    override suspend fun markShown(inviteId: String) {
        ids += inviteId
    }
}

/** Permissions the test can change between reads, like the user in system settings. */
class FakePermissions(
    var granted: GrantedPermissions = GrantedPermissions(notifications = true, exactAlarm = true, fullScreen = true),
) : PermissionReader {
    override fun read(): GrantedPermissions = granted
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
    var outcome: WriteOutcome = WriteOutcome.Created(Cast.TASK_ID)

    override suspend fun write(
        text: String,
        idempotencyKey: String,
    ): WriteOutcome {
        written += text to idempotencyKey
        return outcome
    }

    /** Title, deadline and key of every create from the preview. */
    val created = mutableListOf<Triple<String, Instant, String>>()
    var createOutcome: WriteOutcome = WriteOutcome.Created(Cast.TASK_ID)
    val cancelled = mutableListOf<String>()

    /** Task, deadline and key of every edit from Düzenle. */
    val edited = mutableListOf<Triple<String, Instant, String>>()
    var editOutcome: WriteOutcome = WriteOutcome.Created(Cast.TASK_ID)

    override suspend fun editDeadline(
        taskId: String,
        deadline: Instant,
        idempotencyKey: String,
    ): WriteOutcome {
        edited += Triple(taskId, deadline, idempotencyKey)
        return editOutcome
    }

    /** The note of each cancel, in order; null for Geri al or a cancel without a reason. */
    val cancelNotes = mutableListOf<String?>()

    override suspend fun cancel(
        taskId: String,
        note: String?,
    ) {
        cancelNotes += note
        cancelled += taskId
    }

    override suspend fun create(
        title: String,
        detail: String?,
        deadline: Instant,
        finishMode: FinishMode,
        rawText: String,
        idempotencyKey: String,
        recipientId: String?,
    ): WriteOutcome {
        created += Triple(title, deadline, idempotencyKey)
        createdFor += recipientId
        createdFinishModes += finishMode
        return createOutcome
    }

    /** The recipient of each create; null for a task to self. */
    val createdFor = mutableListOf<String?>()
    val createdFinishModes = mutableListOf<FinishMode>()

    /** What [done] answers: the poke number that caught the person. */
    var caughtAt: Int? = null

    override suspend fun done(taskId: String): Int? {
        done += taskId
        return caughtAt
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

    override suspend fun countSnooze(id: String) {
        tasks.value[id]?.let { tasks.value += id to it.copy(snoozesUsed = it.snoozesUsed + 1) }
    }
}

class InMemoryInviteDao : InviteDao {
    private val invites = MutableStateFlow<Map<String, InviteEntity>>(emptyMap())

    override suspend fun upsert(invites: List<InviteEntity>) {
        this.invites.value += invites.associateBy { it.id }
    }

    override fun observeAll(): Flow<List<InviteEntity>> = invites.map { all -> all.values.sortedByDescending { it.createdAt } }

    override fun observe(id: String): Flow<InviteEntity?> = invites.map { it[id] }
}

/** Records invites created and revoked; [code] null makes create fail. */
class FakeInviteRepository : InviteRepository {
    val created = mutableListOf<List<Any>>()
    val revoked = mutableListOf<String>()
    var code: String? = "abc"

    override suspend fun create(
        phone: String,
        name: String,
        relationship: EdgeType,
        idempotencyKey: String,
    ): String? {
        created += listOf(phone, name, relationship, idempotencyKey)
        return code
    }

    override suspend fun revoke(
        inviteId: String,
        idempotencyKey: String,
    ): Boolean {
        revoked += inviteId
        return true
    }

    val accepted = mutableListOf<Pair<String, EdgeType>>()
    val declined = mutableListOf<String>()
    var answerOk = true

    override suspend fun accept(
        code: String,
        relationship: EdgeType,
        idempotencyKey: String,
    ): Boolean {
        if (answerOk) accepted += code to relationship
        return answerOk
    }

    override suspend fun decline(
        code: String,
        idempotencyKey: String,
    ): Boolean {
        if (answerOk) declined += code
        return answerOk
    }
}

/** Records 4b announcements instead of posting notifications. */
class RecordingAnnouncer : TaskAnnouncer {
    val announced = mutableListOf<Pair<String, String>>()
    val withdrawn = mutableListOf<String>()

    override fun announce(
        task: TaskEntity,
        senderName: String,
    ) {
        announced += task.id to senderName
    }

    override fun withdraw(taskId: String) {
        withdrawn += taskId
    }
}

class InMemoryConnectionDao : ConnectionDao {
    private val connections = MutableStateFlow<Map<String, ConnectionEntity>>(emptyMap())

    override suspend fun upsert(connections: List<ConnectionEntity>) {
        this.connections.value += connections.associateBy { it.personId }
    }

    override suspend fun get(personId: String): ConnectionEntity? = connections.value[personId]

    override fun observeAll(): Flow<List<ConnectionEntity>> = connections.map { it.values.toList() }
}
