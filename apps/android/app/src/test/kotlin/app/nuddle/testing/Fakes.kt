package app.nuddle.testing

import android.graphics.Bitmap
import android.net.Uri
import app.nuddle.alarm.FullScreenPermission
import app.nuddle.alarm.PokeNotifications
import app.nuddle.api.model.EdgeType
import app.nuddle.api.model.FinishMode
import app.nuddle.api.model.RepeatRule
import app.nuddle.data.AccountRepository
import app.nuddle.data.Approvals
import app.nuddle.data.AuthRepository
import app.nuddle.data.InvitePreview
import app.nuddle.data.InvitePreviews
import app.nuddle.data.InviteRepository
import app.nuddle.data.SendOtpOutcome
import app.nuddle.data.SessionStarter
import app.nuddle.data.TaskCloser
import app.nuddle.data.TaskRepository
import app.nuddle.data.VerifyOutcome
import app.nuddle.data.WriteOutcome
import app.nuddle.data.avatar.AvatarRepository
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.InviteEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.outbox.OutboxDrainer
import app.nuddle.data.photo.PhotoLoader
import app.nuddle.data.photo.PhotoUploads
import app.nuddle.data.session.ConsentPrompts
import app.nuddle.data.session.MissedRingState
import app.nuddle.data.session.MissedRingStore
import app.nuddle.data.session.Profile
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.SetupStore
import app.nuddle.data.session.StoredSession
import app.nuddle.data.sync.TaskAnnouncer
import app.nuddle.data.sync.TaskClose
import app.nuddle.domain.AlarmKey
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.PermissionReader
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.PokeStep
import app.nuddle.domain.QuietHours
import app.nuddle.ui.screens.inbox.BellWarning
import app.nuddle.ui.screens.inbox.BellWarningSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalTime
import java.io.ByteArrayOutputStream
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

    private val me = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = me

    override suspend fun saveMe(profile: Profile) {
        me.value = profile
        name.value = profile.displayName
        quiet = profile.quietHours
    }

    /** `me` with the defaults a new person gets. */
    suspend fun saveMe(
        displayName: String,
        quietHours: QuietHours,
    ) = saveMe(Profile(displayName, "+905321234567", quietHours, useConnectionsForMyTasks = true, notifyMeForOthersTasks = true))

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
    var sendOutcome: SendOtpOutcome = SendOtpOutcome.Sent
    var verifyOutcome: VerifyOutcome = VerifyOutcome.SignedIn(registered = true)
    var registerOk = true

    override suspend fun sendOtp(e164: String): SendOtpOutcome {
        sentTo += e164
        return sendOutcome
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
    private val up = mutableSetOf<String>()

    override fun show(
        task: TaskEntity,
        step: PokeStep,
        level: PokeLevel,
    ) {
        shown += task.id to level
        up += task.id
    }

    override fun cancel(taskId: String) {
        cancelled += taskId
        up -= taskId
    }

    override fun showing(): Set<String> = up.toSet()
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

/** Records what closes a task on the device; the task detail sends "Bugün yapamayacağım" here (7f). */
class FakeTaskCloser : TaskCloser {
    val cantToday = mutableListOf<Pair<String, String?>>()

    override suspend fun done(
        taskId: String,
        cameraUnavailable: Boolean,
    ) = Unit

    override suspend fun photoTaken(taskId: String) = Unit

    override suspend fun cancel(
        taskId: String,
        note: String?,
    ) = Unit

    override suspend fun cantToday(
        taskId: String,
        note: String?,
    ) {
        cantToday += taskId to note
    }
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

    /** Deadline and finish mode of every row edit in the task detail (7c); null is "unchanged". */
    val rowEdits = mutableListOf<Pair<Instant?, FinishMode?>>()

    override suspend fun edit(
        taskId: String,
        deadline: Instant?,
        finishMode: FinishMode?,
        idempotencyKey: String,
    ): WriteOutcome {
        rowEdits += deadline to finishMode
        return editOutcome
    }

    /** Every Kime change in the task detail (NUD-114): the task and its new recipient. */
    val reassigns = mutableListOf<Pair<String, String>>()

    /** A [app.nuddle.data.TaskReassigner]; the key only matters to the server. */
    val reassigner =
        app.nuddle.data.TaskReassigner { taskId, recipientId, _ ->
            reassigns += taskId to recipientId
            editOutcome
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
        moment: Boolean,
        missAfterMinutes: Int?,
        repeat: RepeatRule?,
    ): WriteOutcome {
        created += Triple(title, deadline, idempotencyKey)
        createdFor += recipientId
        createdFinishModes += finishMode
        createdMoments += moment to missAfterMinutes
        return createOutcome
    }

    /** The recipient of each create; null for a task to self. */
    val createdFor = mutableListOf<String?>()

    /** Whether each create was a moment (ADR 0017), with its grace. */
    val createdMoments = mutableListOf<Pair<Boolean, Int?>>()
    val createdFinishModes = mutableListOf<FinishMode>()

    val didItLater = mutableListOf<String>()
    val senderHandled = mutableListOf<String>()

    /** Task and new deadline of every "Yeni zaman ver" or "Süreyi uzat". */
    val newDeadlines = mutableListOf<Pair<String, Instant>>()
    var newDeadlineOutcome: WriteOutcome = WriteOutcome.Created(Cast.TASK_ID)

    override suspend fun didItLater(taskId: String) {
        didItLater += taskId
    }

    /** Every "Kaldır" on a missed card (NUD-49). */
    val removedMissed = mutableListOf<String>()

    override suspend fun removeMissed(taskId: String) {
        removedMissed += taskId
    }

    override suspend fun senderHandles(taskId: String) {
        senderHandled += taskId
    }

    override suspend fun newDeadline(
        taskId: String,
        deadline: Instant,
        idempotencyKey: String,
    ): WriteOutcome {
        newDeadlines += taskId to deadline
        return newDeadlineOutcome
    }

    /** Task and start of every moved start (NUD-78). */
    val starts = mutableListOf<Pair<String, Instant>>()
    var startOutcome: WriteOutcome = WriteOutcome.Created(Cast.TASK_ID)

    override suspend fun setStart(
        taskId: String,
        startAt: Instant,
        idempotencyKey: String,
    ): WriteOutcome {
        starts += taskId to startAt
        return startOutcome
    }

    /** What [done] answers: the poke number that caught the person. */
    var caughtAt: Int? = null

    /** Tasks closed without a camera (photo fallback). */
    val doneWithoutCamera = mutableListOf<String>()

    override suspend fun done(
        taskId: String,
        cameraUnavailable: Boolean,
    ): Int? {
        if (cameraUnavailable) doneWithoutCamera += taskId
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

    override fun observe(id: String): Flow<TaskEntity?> = tasks.map { it[id] }

    override suspend fun hideFromMyList(id: String) {
        tasks.value[id]?.let { tasks.value += id to it.copy(inRecipientList = false) }
    }

    override suspend fun setStatus(
        id: String,
        status: String,
    ) {
        tasks.value[id]?.let { tasks.value += id to it.copy(status = status) }
    }

    override suspend fun countSnooze(
        id: String,
        until: Long,
    ) {
        tasks.value[id]?.let { tasks.value += id to it.copy(snoozesUsed = it.snoozesUsed + 1, snoozedUntil = until) }
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
    val photos = mutableListOf<Pair<String, String>>()

    override fun photoArrived(
        task: TaskEntity,
        recipientName: String,
    ) {
        photos += task.id to recipientName
    }

    override fun announce(
        task: TaskEntity,
        senderName: String,
    ) {
        announced += task.id to senderName
    }

    override fun withdraw(taskId: String) {
        withdrawn += taskId
    }

    /** Task, recipient name, pokes and full screen of every 7a. */
    val unreached = mutableListOf<Pair<String, Triple<String, Int, Boolean>>>()
    val reachWithdrawn = mutableListOf<String>()

    override fun cantReach(
        task: TaskEntity,
        recipient: ConnectionEntity,
        pokes: Int,
        fullScreen: Boolean,
    ) {
        unreached += task.id to Triple(recipient.displayName, pokes, fullScreen)
    }

    override fun withdrawCantReach(taskId: String) {
        reachWithdrawn += taskId
    }

    /** Task, the other side's name and how they closed it (NUD-115). */
    val closes = mutableListOf<Triple<String, String, TaskClose>>()

    override fun closed(
        task: TaskEntity,
        otherName: String,
        close: TaskClose,
    ) {
        closes += Triple(task.id, otherName, close)
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

/** A JPEG for every task asked for; records which ones. */
class FakePhotoLoader : PhotoLoader {
    val loaded = mutableListOf<String>()

    override suspend fun load(taskId: String): ByteArray {
        loaded += taskId
        return ByteArrayOutputStream().use {
            Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
            it.toByteArray()
        }
    }
}

/** Records the sender's answers to a photo (4f). */
class RecordingApprovals : Approvals {
    val approved = mutableListOf<String>()
    val reRequested = mutableListOf<Pair<String, String?>>()

    override suspend fun approve(taskId: String) {
        approved += taskId
    }

    override suspend fun reRequest(
        taskId: String,
        note: String?,
    ) {
        reRequested += taskId to note
    }
}

/** Records upload starts; [failing] is what the upload status reports for every task. */
class FakePhotoUploads : PhotoUploads {
    val started = mutableListOf<String>()
    val failing = MutableStateFlow(false)

    override fun uploadSoon(taskId: String) {
        started += taskId
    }

    override fun failing(taskId: String): Flow<Boolean> = failing
}

/** Records profile and settings commands; [ok] answers them, [gate] holds them until completed. */
class FakeAccountRepository : AccountRepository {
    val calls = mutableListOf<String>()
    var ok = true
    var gate: CompletableDeferred<Unit>? = null

    private suspend fun answer(call: String): Boolean {
        calls += call
        gate?.await()
        return ok
    }

    override suspend fun setQuietHours(
        start: LocalTime,
        end: LocalTime,
    ) = answer("quiet $start-$end")

    override suspend fun setUseConnectionsForMyTasks(on: Boolean) = answer("useConnections $on")

    override suspend fun setNotifyMeForOthersTasks(on: Boolean) = answer("notifyMeForOthers $on")

    override suspend fun setRelationship(
        personId: String,
        relationship: EdgeType,
    ) = answer("relationship $personId ${relationship.value}")

    override suspend fun setNotifyMe(
        personId: String,
        on: Boolean,
    ) = answer("notifyMe $personId $on")

    override suspend fun removeConnection(
        personId: String,
        idempotencyKey: String,
    ) = answer("remove $personId $idempotencyKey")

    override suspend fun logOut(idempotencyKey: String) = answer("logOut $idempotencyKey")

    override suspend fun deleteAccount(idempotencyKey: String) = answer("delete $idempotencyKey")
}

class FakeAvatarRepository : AvatarRepository {
    val picked = mutableListOf<Uri>()
    var removed = 0
    var ok = true

    override suspend fun pick(uri: Uri): Boolean {
        picked += uri
        return ok
    }

    override suspend fun remove(): Boolean {
        removed++
        return ok
    }
}

class InMemoryMissedRingStore : MissedRingStore {
    private val current = MutableStateFlow(MissedRingState())
    var handled: Instant? = null

    override val state: Flow<MissedRingState> = current

    val value: MissedRingState get() = current.value

    override suspend fun lost(plannedAt: Instant) = current.update { s -> s.copy(lostAt = listOfNotNull(s.lostAt, plannedAt).maxOrNull()) }

    override suspend fun rang(at: Instant) = current.update { it.copy(rangAt = at) }

    override suspend fun fixed(at: Instant) = current.update { it.copy(fixedAt = at) }

    override suspend fun opened(
        stepId: String,
        at: Instant,
    ) = current.update { it.copy(opened = it.opened + (stepId to at)) }

    override suspend fun handledStop(): Instant? = handled

    override suspend fun markStopHandled(at: Instant) {
        handled = at
    }
}

class FakeBellWarnings : BellWarningSource {
    val current = MutableStateFlow<List<BellWarning>>(emptyList())
    var refreshes = 0

    override val warnings: Flow<List<BellWarning>> = current

    override fun refresh() {
        refreshes++
    }
}

/** Answers [previews] by code; any other code has no open invite. */
class FakeInvitePreviews(
    private val previews: Map<String, InvitePreview> = emptyMap(),
) : InvitePreviews {
    override val current = MutableStateFlow<InvitePreview?>(null)
    val loaded = mutableListOf<String>()

    override suspend fun load(code: String) {
        loaded += code
        current.value = previews[code]
    }
}
