package app.nuddle.di

import app.nuddle.BuildConfig
import app.nuddle.data.diagnostics.CrashlyticsDiagnostics
import app.nuddle.data.diagnostics.LogcatDiagnostics
import app.nuddle.domain.Diagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/** Breadcrumbs and non-fatals (ADR 0023): to Crashlytics, except in the e2e flavor. */
@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {
    @Provides
    @Singleton
    fun diagnostics(
        crashlytics: Provider<CrashlyticsDiagnostics>,
        logcat: Provider<LogcatDiagnostics>,
    ): Diagnostics = if (BuildConfig.CRASH_REPORTS) crashlytics.get() else logcat.get()
}
