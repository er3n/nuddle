package app.nuddle.domain

/** What the device allows right now (docs/ux/platform.md, Permissions). */
data class GrantedPermissions(
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val fullScreen: Boolean,
) {
    fun has(step: SetupStep): Boolean =
        when (step) {
            SetupStep.NOTIFICATIONS -> notifications
            SetupStep.EXACT_ALARM -> exactAlarm
            SetupStep.FULL_SCREEN -> fullScreen
        }

    val all: Boolean get() = SetupStep.entries.all(::has)
}

/** Reads the permissions on every call; the user can change them in system settings at any time. */
fun interface PermissionReader {
    fun read(): GrantedPermissions
}

/** The setup steps in order (docs/product.md, Permissions and setup; screen 2e). */
enum class SetupStep {
    NOTIFICATIONS,
    EXACT_ALARM,
    FULL_SCREEN,
    ;

    /** Notifications cannot be skipped: without them nothing reaches the person. */
    val skippable: Boolean get() = this != NOTIFICATIONS
}

object SetupRules {
    /** The step to work on: the first one neither granted nor skipped. Null when setup is finished. */
    fun active(
        granted: GrantedPermissions,
        skipped: Set<SetupStep>,
    ): SetupStep? = SetupStep.entries.firstOrNull { !granted.has(it) && !(it.skippable && it in skipped) }

    /** The settings steps (after the notification dialog) still to do, the "2" of "2 ayar" on 2d and 2e. */
    fun settingsLeft(granted: GrantedPermissions): Int = SetupStep.entries.count { it != SetupStep.NOTIFICATIONS && !granted.has(it) }
}
