package app.nuddle.di

import android.os.Build
import app.nuddle.alarm.AndroidForceStops
import app.nuddle.alarm.ForceStops
import app.nuddle.data.ApiBrandFixRepository
import app.nuddle.data.BatteryOptimization
import app.nuddle.data.BrandFixRepository
import app.nuddle.data.DeviceBrand
import app.nuddle.data.session.DataStoreMissedRingStore
import app.nuddle.data.session.MissedRingStore
import app.nuddle.push.DevicePermissions
import app.nuddle.ui.screens.inbox.BellWarningSource
import app.nuddle.ui.screens.inbox.BellWarnings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Missed-ring detection and the bell's warnings (2g, 2h). */
@Module
@InstallIn(SingletonComponent::class)
abstract class MissedRingModule {
    @Binds
    abstract fun missedRingStore(impl: DataStoreMissedRingStore): MissedRingStore

    @Binds
    abstract fun forceStops(impl: AndroidForceStops): ForceStops

    @Binds
    abstract fun batteryOptimization(impl: DevicePermissions): BatteryOptimization

    @Binds
    abstract fun bellWarningSource(impl: BellWarnings): BellWarningSource

    @Binds
    abstract fun brandFixRepository(impl: ApiBrandFixRepository): BrandFixRepository

    companion object {
        @Provides
        fun deviceBrand(): DeviceBrand = DeviceBrand { Build.MANUFACTURER }
    }
}
