package app.nuddle.di

import app.nuddle.data.ClockMinuteTicks
import app.nuddle.data.MinuteTicks
import app.nuddle.data.session.DataStoreDismissedRows
import app.nuddle.data.session.DismissedRows
import app.nuddle.data.session.OutboxTaskAcks
import app.nuddle.data.session.TaskAcks
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What home keeps on this phone only (NUD-67), and what it has told the sender (NUD-68). */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    abstract fun dismissedRows(impl: DataStoreDismissedRows): DismissedRows

    @Binds
    abstract fun taskAcks(impl: OutboxTaskAcks): TaskAcks

    @Binds
    abstract fun minuteTicks(impl: ClockMinuteTicks): MinuteTicks
}
