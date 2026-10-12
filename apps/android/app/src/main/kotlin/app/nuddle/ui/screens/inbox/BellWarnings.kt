package app.nuddle.ui.screens.inbox

import androidx.compose.runtime.Immutable
import app.nuddle.alarm.toLocalState
import app.nuddle.alarm.toStep
import app.nuddle.data.MissedRings
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.PokeStepEntity
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEntity
import app.nuddle.data.session.SessionStore
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.PermissionReader
import app.nuddle.domain.PokeLevel
import app.nuddle.domain.SetupStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

/** The day of the next poke, for "Yarınki "Para çek" dürtmen". */
enum class PokeDay { Today, Tomorrow, Later }

@Immutable
data class NextPokeUi(
    val title: String,
    val day: PokeDay,
)

/** A warning at the top of the bell (2g): a poke that did not ring, or a permission that is off. */
@Immutable
sealed interface BellWarning {
    data object MissedRing : BellWarning

    /** [next] is the next poke that needs the permission, when there is one (only for full screen). */
    data class PermissionOff(
        val step: SetupStep,
        val next: NextPokeUi? = null,
    ) : BellWarning
}

/** The missed ring first, then each permission that is off, in setup order (open-questions#38). */
fun bellWarnings(
    missedRing: Instant?,
    granted: GrantedPermissions,
    nextFullScreen: NextPokeUi?,
): List<BellWarning> =
    listOfNotNull(missedRing?.let { BellWarning.MissedRing }) +
        SetupStep.entries
            .filterNot(granted::has)
            .map { step -> BellWarning.PermissionOff(step, if (step == SetupStep.FULL_SCREEN) nextFullScreen else null) }

/** My next full screen poke still to ring: its task and day. */
fun nextFullScreen(
    steps: List<PokeStepEntity>,
    tasks: List<TaskEntity>,
    me: String,
    now: Instant,
    timeZone: TimeZone,
): NextPokeUi? {
    val mine = tasks.filter { it.recipientId == me }.associateBy { it.id }
    val next =
        steps
            .filter { step ->
                val task = mine[step.taskId]?.toLocalState()
                step.level == PokeLevel.FULL_SCREEN.name && step.firedAt == null && step.plannedAt > now.toEpochMilliseconds() &&
                    task != null && task.open && task.currentPlanVersion == step.planVersion
            }.minByOrNull { it.plannedAt }
            ?.toStep() ?: return null
    val title = mine[next.taskId]?.title ?: return null
    val days = now.toLocalDateTime(timeZone).date.daysUntil(next.plannedAt.toLocalDateTime(timeZone).date)
    val day =
        when (days) {
            0 -> PokeDay.Today
            1 -> PokeDay.Tomorrow
            else -> PokeDay.Later
        }
    return NextPokeUi(title, day)
}

/** The bell's warnings (2g), read again by [refresh] when the user may have changed a permission. */
interface BellWarningSource {
    val warnings: Flow<List<BellWarning>>

    fun refresh()
}

/**
 * The bell's warnings and the part of its badge they make. Permissions are read again by [refresh]: the user can
 * change them in system settings at any time (docs/product.md, a silent check on every launch).
 */
@Singleton
class BellWarnings
    @Inject
    constructor(
        sessions: SessionStore,
        tasks: TaskDao,
        steps: PokeStepDao,
        missedRings: MissedRings,
        private val permissions: PermissionReader,
        private val clock: Clock,
        private val timeZone: TimeZone,
    ) : BellWarningSource {
        private val granted = MutableStateFlow(permissions.read())

        override fun refresh() {
            granted.value = permissions.read()
        }

        private val next: Flow<NextPokeUi?> =
            combine(sessions.session.map { it?.personId }, tasks.observeAll(), steps.observeAll()) { me, all, allSteps ->
                me?.let { nextFullScreen(allSteps, all, it, clock.now(), timeZone) }
            }

        override val warnings: Flow<List<BellWarning>> = combine(missedRings.warning, granted, next, ::bellWarnings)
    }
