package app.nuddle.data

import app.nuddle.data.session.SetupStore
import app.nuddle.domain.PermissionReader
import javax.inject.Inject

/**
 * Whether setup should open now (docs/product.md, Permissions and setup): it never ran on this install and a permission
 * is missing. Granted steps are skipped, so with all of them there is nothing to show.
 */
class SetupGate
    @Inject
    constructor(
        private val setup: SetupStore,
        private val permissions: PermissionReader,
    ) {
        suspend fun needed(): Boolean = !setup.done() && !permissions.read().all
    }
