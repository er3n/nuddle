package app.nuddle.di

import app.nuddle.data.ApiSessionsRepository
import app.nuddle.data.SessionsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Cihazlarım (5e, ADR 0027). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionsModule {
    @Binds
    abstract fun sessionsRepository(impl: ApiSessionsRepository): SessionsRepository
}
