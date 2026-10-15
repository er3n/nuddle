package app.nuddle.domain

/**
 * What the device allows right now (docs/ux/platform.md, Permissions). [battery] is null on a phone that does not
 * put apps to sleep (stock Android): the step is then not shown anywhere (NUD-75, open-questions#49).
 */
data class GrantedPermissions(
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val fullScreen: Boolean,
    val battery: Boolean? = null,
) {
    fun has(step: SetupStep): Boolean =
        when (step) {
            SetupStep.NOTIFICATIONS -> notifications
            SetupStep.EXACT_ALARM -> exactAlarm
            SetupStep.FULL_SCREEN -> fullScreen
            SetupStep.BATTERY -> battery != false
        }

    /** The steps this phone has: the battery step only where the phone puts apps to sleep. */
    val steps: List<SetupStep> get() = SetupStep.entries.filter { it != SetupStep.BATTERY || battery != null }

    val all: Boolean get() = steps.all(::has)
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

    /** Battery optimization off, one tap, only on phones that put apps to sleep (NUD-75). */
    BATTERY,
    ;

    /** Notifications cannot be skipped: without them nothing reaches the person. */
    val skippable: Boolean get() = this != NOTIFICATIONS
}

object SetupRules {
    /**
     * Manufacturers whose phones stop a sleeping app's alarms (dontkillmyapp.com; the brand-fix list on the server
     * has the same names). `Build.MANUFACTURER`, compared without case.
     */
    private val sleepingBrands =
        setOf("samsung", "xiaomi", "huawei", "oppo", "vivo", "realme", "oneplus", "honor", "redmi", "poco", "iqoo", "tecno", "infinix")

    /** Whether the battery step is shown on this phone. */
    fun needsBatteryStep(manufacturer: String): Boolean = manufacturer.trim().lowercase() in sleepingBrands

    /** The step to work on: the first one neither granted nor skipped. Null when setup is finished. */
    fun active(
        granted: GrantedPermissions,
        skipped: Set<SetupStep>,
    ): SetupStep? = granted.steps.firstOrNull { !granted.has(it) && !(it.skippable && it in skipped) }

    /** The settings steps (after the notification dialog) still to do, the "2" of "2 ayar" on 2d and 2e. */
    fun settingsLeft(granted: GrantedPermissions): Int = granted.steps.count { it != SetupStep.NOTIFICATIONS && !granted.has(it) }
}
