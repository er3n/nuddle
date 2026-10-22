package app.nuddle.di

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import app.nuddle.BuildConfig
import app.nuddle.alarm.AlarmCheckScheduling
import app.nuddle.alarm.AlarmManagerScheduler
import app.nuddle.alarm.AndroidLockedPokeNotification
import app.nuddle.alarm.AndroidPokeNotifications
import app.nuddle.alarm.ArmedPokes
import app.nuddle.alarm.ArmedPokesKeeper
import app.nuddle.alarm.DirectBootStore
import app.nuddle.alarm.FileDirectBootStore
import app.nuddle.alarm.FullScreenPermission
import app.nuddle.alarm.LockedPokeNotification
import app.nuddle.alarm.PokeAlarms
import app.nuddle.alarm.PokeNotifications
import app.nuddle.alarm.WorkManagerAlarmChecks
import app.nuddle.alarm.userUnlocked
import app.nuddle.data.ApiAuthRepository
import app.nuddle.data.AuthRepository
import app.nuddle.data.SessionStarter
import app.nuddle.data.outbox.OutboxDrainer
import app.nuddle.data.outbox.WorkManagerScheduling
import app.nuddle.data.session.DataStoreSessionStore
import app.nuddle.data.session.DataStoreSetupStore
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.SetupStore
import app.nuddle.data.sync.AlarmRearmer
import app.nuddle.domain.AlarmScheduler
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.PermissionReader
import app.nuddle.push.AppSessionStarter
import app.nuddle.push.DevicePermissions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import javax.inject.Singleton
import kotlin.time.Clock

/** The one place the real clock and the device time zone enter the app (ADR 0009). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * The `e2e` flavor runs on fake time the orchestrator moves (TestTime). Its offset is kept where it cannot be read
     * before the first unlock; the phone's own clock is used then (docs/ux/platform.md, Direct boot).
     */
    @Provides
    fun clock(
        @ApplicationContext context: Context,
    ): Clock = if (BuildConfig.FAKE_TIME && context.userUnlocked()) TestTime.clock(context) else Clock.System

    /** Not a singleton: the user can change the zone while the app runs. */
    @Provides
    fun timeZone(): TimeZone = TimeZone.currentSystemDefault()

    @Provides
    fun ids(clock: Clock): IdGenerator = IdGenerator(clock)

    /** Unknown fields and enum values from a newer server must not crash an older app. */
    @Provides
    @Singleton
    fun json(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = true
        }

    @Provides
    @Singleton
    fun dataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }

    @Provides
    @Singleton
    fun alarmScheduler(
        @ApplicationContext context: Context,
    ): AlarmScheduler =
        AlarmManagerScheduler(context, context.getSystemService(AlarmManager::class.java)) { at ->
            if (BuildConfig.FAKE_TIME && context.userUnlocked()) TestTime.shift(context, at) else at
        }

    /** Only built once the phone is unlocked: WorkManager cannot open its database before (Direct boot). */
    @Provides
    fun alarmChecks(
        @ApplicationContext context: Context,
    ): AlarmCheckScheduling = WorkManagerAlarmChecks(WorkManager.getInstance(context))

    @Provides
    @Singleton
    fun workScheduling(
        @ApplicationContext context: Context,
    ): WorkManagerScheduling = WorkManagerScheduling(WorkManager.getInstance(context))
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun sessionStore(impl: DataStoreSessionStore): SessionStore

    @Binds
    abstract fun authRepository(impl: ApiAuthRepository): AuthRepository

    @Binds
    abstract fun outboxDrainer(impl: WorkManagerScheduling): OutboxDrainer

    @Binds
    abstract fun alarmRearmer(impl: PokeAlarms): AlarmRearmer

    @Binds
    abstract fun pokeNotifications(impl: AndroidPokeNotifications): PokeNotifications

    @Binds
    abstract fun fullScreenPermission(impl: DevicePermissions): FullScreenPermission

    @Binds
    abstract fun permissionReader(impl: DevicePermissions): PermissionReader

    @Binds
    abstract fun setupStore(impl: DataStoreSetupStore): SetupStore

    @Binds
    abstract fun sessionStarter(impl: AppSessionStarter): SessionStarter
}

/** The direct boot copy of the armed pokes and the hourly check (docs/ux/platform.md, Alarms and pokes; NUD-125). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DirectBootModule {
    @Binds
    abstract fun armedPokes(impl: ArmedPokesKeeper): ArmedPokes

    @Binds
    abstract fun directBootStore(impl: FileDirectBootStore): DirectBootStore

    @Binds
    abstract fun lockedPokeNotification(impl: AndroidLockedPokeNotification): LockedPokeNotification
}
