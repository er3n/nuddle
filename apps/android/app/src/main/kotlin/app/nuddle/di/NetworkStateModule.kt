package app.nuddle.di

import app.nuddle.data.network.AndroidConnectivity
import app.nuddle.data.network.Connectivity
import app.nuddle.data.network.CrashlyticsErrorReporter
import app.nuddle.data.network.ErrorReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Whether the phone is online (6a), and where 6f's codes go. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkStateModule {
    @Binds
    abstract fun connectivity(impl: AndroidConnectivity): Connectivity

    @Binds
    abstract fun errorReporter(impl: CrashlyticsErrorReporter): ErrorReporter
}
