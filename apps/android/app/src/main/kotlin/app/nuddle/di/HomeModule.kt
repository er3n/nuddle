package app.nuddle.di

import app.nuddle.data.session.DataStoreDismissedRows
import app.nuddle.data.session.DismissedRows
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What home keeps on this phone only (NUD-67). */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    abstract fun dismissedRows(impl: DataStoreDismissedRows): DismissedRows
}
