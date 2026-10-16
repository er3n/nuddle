package app.nuddle.domain

/**
 * What the app leaves for a crash report (ADR 0023): breadcrumbs, non-fatal errors and keys. Lines are built from code
 * constants and opaque ids only: never task text, notes, names, phone numbers, photos, invite codes, URLs or tokens.
 */
interface Diagnostics {
    /** A breadcrumb, kept with the next crash or non-fatal. */
    fun log(line: String)

    /** An error the app caught and turned into UI state, or swallowed. */
    fun record(error: Throwable)

    fun setKey(
        key: String,
        value: String,
    )

    /** The opaque person id (JWT `sub`), null after sign-out. Never the phone number. */
    fun setPerson(personId: String?)

    /** Records nothing: the default where tests build a class without caring what it reports. */
    object None : Diagnostics {
        override fun log(line: String) = Unit

        override fun record(error: Throwable) = Unit

        override fun setKey(
            key: String,
            value: String,
        ) = Unit

        override fun setPerson(personId: String?) = Unit
    }

    companion object {
        const val KEY_ROUTE = "route"
        const val KEY_VERSION = "version"
        const val KEY_FLAVOR = "flavor"
    }
}

/** A route of the nav host came to the front. [route] is the pattern (`task/{taskId}`), never its arguments. */
fun Diagnostics.routeShown(route: String) {
    log("route $route")
    setKey(Diagnostics.KEY_ROUTE, route)
}
