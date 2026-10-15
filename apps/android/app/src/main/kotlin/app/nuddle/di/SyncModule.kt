package app.nuddle.di

import app.nuddle.data.sync.LocalPendingChanges
import app.nuddle.data.sync.PendingChanges
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What a sync keeps of this phone's own unsent changes (NUD-64). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun pendingChanges(impl: LocalPendingChanges): PendingChanges
}
