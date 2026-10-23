package app.nuddle.di

import app.nuddle.alarm.QuietHoursSource
import app.nuddle.alarm.SessionQuietHours
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What the answers to a poke need beyond the alarm itself. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PokeModule {
    /** The quiet hours a snooze must end before (NUD-163). */
    @Binds
    abstract fun quietHoursSource(impl: SessionQuietHours): QuietHoursSource
}
