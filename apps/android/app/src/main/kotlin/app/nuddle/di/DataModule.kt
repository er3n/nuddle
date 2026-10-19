package app.nuddle.di

import android.content.Context
import androidx.room.Room
import app.nuddle.BuildConfig
import app.nuddle.alarm.AndroidLocalWipe
import app.nuddle.alarm.AndroidNewsAnnouncer
import app.nuddle.alarm.AndroidTaskAnnouncer
import app.nuddle.alarm.PokeActions
import app.nuddle.data.AccountRepository
import app.nuddle.data.ApiAccountRepository
import app.nuddle.data.ApiInviteRepository
import app.nuddle.data.ApiTaskRepository
import app.nuddle.data.Approvals
import app.nuddle.data.InviteRepository
import app.nuddle.data.LocalWipe
import app.nuddle.data.OutboxApprovals
import app.nuddle.data.TaskCloser
import app.nuddle.data.TaskRepository
import app.nuddle.data.announcement.AnnouncementInbox
import app.nuddle.data.announcement.NewsAnnouncer
import app.nuddle.data.announcement.RoomAnnouncementInbox
import app.nuddle.data.avatar.ApiAvatarRepository
import app.nuddle.data.avatar.AvatarRefresher
import app.nuddle.data.avatar.AvatarRepository
import app.nuddle.data.avatar.AvatarSync
import app.nuddle.data.avatar.AvatarUploads
import app.nuddle.data.db.AnnouncementDao
import app.nuddle.data.db.ConnectionDao
import app.nuddle.data.db.InviteDao
import app.nuddle.data.db.NuddleDatabase
import app.nuddle.data.db.OutboxDao
import app.nuddle.data.db.PokeStepDao
import app.nuddle.data.db.QueuedWriteDao
import app.nuddle.data.db.TaskDao
import app.nuddle.data.db.TaskEventDao
import app.nuddle.data.outbox.WorkManagerScheduling
import app.nuddle.data.photo.ApiPhotoLoader
import app.nuddle.data.photo.ContentGalleryImages
import app.nuddle.data.photo.GalleryImages
import app.nuddle.data.photo.PhotoLoader
import app.nuddle.data.photo.PhotoUploads
import app.nuddle.data.session.ConsentPrompts
import app.nuddle.data.session.DataStoreConsentPrompts
import app.nuddle.data.sync.SyncRepository
import app.nuddle.data.sync.Syncer
import app.nuddle.data.sync.TaskAnnouncer
import app.nuddle.data.voice.AndroidSpeechInput
import app.nuddle.data.voice.InjectedSpeechInput
import app.nuddle.data.voice.SpeechInput
import app.nuddle.ui.screens.photo.CameraSource
import app.nuddle.ui.screens.photo.CameraXSource
import app.nuddle.ui.screens.photo.InjectedPhotoSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
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
    fun queuedWrites(db: NuddleDatabase): QueuedWriteDao = db.queuedWrites()

    @Provides
    fun invites(db: NuddleDatabase): InviteDao = db.invites()

    @Provides
    fun connections(db: NuddleDatabase): ConnectionDao = db.connections()

    @Provides
    fun taskEvents(db: NuddleDatabase): TaskEventDao = db.taskEvents()

    @Provides
    fun announcements(db: NuddleDatabase): AnnouncementDao = db.announcements()
}

/** The in-app camera, or in the `e2e` flavor the photo the orchestrator put on the phone (NUD-29). */
@Module
@InstallIn(SingletonComponent::class)
object CameraModule {
    @Provides
    fun cameraSource(
        camera: Provider<CameraXSource>,
        injected: Provider<InjectedPhotoSource>,
    ): CameraSource = if (BuildConfig.INJECTED_PHOTO) injected.get() else camera.get()

    /** "Galeriden seç" (4d): the system photo picker's result read as JPEG (NUD-73). */
    @Provides
    fun galleryImages(impl: ContentGalleryImages): GalleryImages = impl
}

/** Speech recognition on the phone, or in the `e2e` flavor the text the orchestrator put on the phone (4h). */
@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {
    @Provides
    fun speechInput(
        android: Provider<AndroidSpeechInput>,
        injected: Provider<InjectedSpeechInput>,
    ): SpeechInput = if (BuildConfig.INJECTED_SPEECH) injected.get() else android.get()
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
    abstract fun accountRepository(impl: ApiAccountRepository): AccountRepository

    @Binds
    abstract fun localWipe(impl: AndroidLocalWipe): LocalWipe

    @Binds
    abstract fun consentPrompts(impl: DataStoreConsentPrompts): ConsentPrompts

    @Binds
    abstract fun syncer(impl: SyncRepository): Syncer

    @Binds
    abstract fun taskAnnouncer(impl: AndroidTaskAnnouncer): TaskAnnouncer

    @Binds
    abstract fun photoUploads(impl: WorkManagerScheduling): PhotoUploads

    @Binds
    abstract fun photoLoader(impl: ApiPhotoLoader): PhotoLoader

    @Binds
    abstract fun approvals(impl: OutboxApprovals): Approvals
}

/** Profile pictures (NUD-72): picked on Profile, uploaded through WorkManager, fetched after a sync. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AvatarModule {
    @Binds
    abstract fun avatarRepository(impl: ApiAvatarRepository): AvatarRepository

    @Binds
    abstract fun avatarRefresher(impl: AvatarSync): AvatarRefresher

    @Binds
    abstract fun avatarUploads(impl: WorkManagerScheduling): AvatarUploads
}

/** Messages from Nuddle (ADR 0025): stored from sync, shown on the `news` channel. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnnouncementModule {
    @Binds
    abstract fun announcementInbox(impl: RoomAnnouncementInbox): AnnouncementInbox

    @Binds
    abstract fun newsAnnouncer(impl: AndroidNewsAnnouncer): NewsAnnouncer
}
