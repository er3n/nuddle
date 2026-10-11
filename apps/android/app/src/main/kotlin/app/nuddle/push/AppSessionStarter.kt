package app.nuddle.push

import app.nuddle.data.SessionStarter
import app.nuddle.data.outbox.WorkManagerScheduling
import javax.inject.Inject

class AppSessionStarter
    @Inject
    constructor(
        private val registrar: DeviceRegistrar,
        private val work: WorkManagerScheduling,
    ) : SessionStarter {
        override suspend fun start() {
            registrar.register()
            work.syncSoon()
            work.drainSoon()
        }
    }
