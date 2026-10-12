package app.nuddle.di

import android.content.Context
import androidx.room.Room
import app.nuddle.alarm.AndroidTaskAnnouncer
import app.nuddle.alarm.PokeActions
import app.nuddle.data.ApiInviteRepository
import app.nuddle.data.ApiTaskRepository
import app.nuddle.data.Approvals
import app.nuddle.data.InviteRepository
import app.nuddle.data.OutboxApprovals
import app.nuddle.data.TaskCloser
import app.nuddle.data.TaskRepository
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.OutboxDao
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.outbox.WorkManagerScheduling
import app.nuddle.data.photo.ApiPhotoLoader
import app.nuddle.data.photo.PhotoLoader
import app.nuddle.data.photo.PhotoUploads
import app.nuddle.data.session.ConsentPrompts
import app.nuddle.data.session.DataStoreConsentPrompts
import app.nuddle.data.sync.SyncRepository
import app.nuddle.data.sync.Syncer
import app.nuddle.data.sync.TaskAnnouncer
import app.nuddle.ui.screens.photo.CameraSource
import app.nuddle.ui.screens.photo.CameraXSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Room and its DAOs (docs/architecture.md, local store). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): NuddleDatabase = Room.databaseBuilder(context, NuddleDatabase::class.java, "nuddle.db").build()

    @Provides
    fun tasks(db: NuddleDatabase): TaskDao = db.tasks()

    @Provides
    fun pokeSteps(db: NuddleDatabase): PokeStepDao = db.pokeSteps()

    @Provides
    fun outbox(db: NuddleDatabase): OutboxDao = db.outbox()

    @Provides
    fun invites(db: NuddleDatabase): InviteDao = db.invites()

    @Provides
    fun connections(db: NuddleDatabase): ConnectionDao = db.connections()
}

/** What the screens write through: tasks and invites. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun taskRepository(impl: ApiTaskRepository): TaskRepository

    @Binds
    abstract fun taskCloser(impl: PokeActions): TaskCloser

    @Binds
    abstract fun inviteRepository(impl: ApiInviteRepository): InviteRepository

    @Binds
    abstract fun consentPrompts(impl: DataStoreConsentPrompts): ConsentPrompts

    @Binds
    abstract fun syncer(impl: SyncRepository): Syncer

    @Binds
    abstract fun taskAnnouncer(impl: AndroidTaskAnnouncer): TaskAnnouncer

    @Binds
    abstract fun photoUploads(impl: WorkManagerScheduling): PhotoUploads

    @Binds
    abstract fun cameraSource(impl: CameraXSource): CameraSource

    @Binds
    abstract fun photoLoader(impl: ApiPhotoLoader): PhotoLoader

    @Binds
    abstract fun approvals(impl: OutboxApprovals): Approvals
}
