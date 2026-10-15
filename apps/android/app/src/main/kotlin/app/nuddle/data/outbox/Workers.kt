package app.nuddle.data.outbox

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nuddle.data.avatar.AvatarUploader
import app.nuddle.data.avatar.AvatarUploads
import app.nuddle.data.photo.PhotoUploader
import app.nuddle.data.photo.PhotoUploads
import app.nuddle.data.sync.SyncRepository
import app.nuddle.domain.SendOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private const val BACKOFF_SECONDS = 30L

/** Drains the outbox whenever there is a network, with exponential backoff (ADR 0006). */
@HiltWorker
class OutboxWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val sender: OutboxSender,
        private val sync: SyncRepository,
    ) : CoroutineWorker(context, params) {
        /**
         * Then a sync: the server's answer replaces what the phone set locally, also when the server refused it (a
         * Bitti for a task the sender had just cancelled), without waiting for a push (NUD-64).
         */
        override suspend fun doWork(): Result {
            if (!sender.drain()) return Result.retry()
            sync.sync()
            return Result.success()
        }
    }

/** Pulls `GET /api/v1/sync`; FCM data messages only say "sync now" (docs/architecture.md, Push). */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val sync: SyncRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = if (sync.sync()) Result.success() else Result.retry()
    }

/** Uploads a kept photo (4d, 6e); retried with backoff until the server has it or refuses it. */
@HiltWorker
class PhotoUploadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val uploader: PhotoUploader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val taskId = inputData.getString(KEY_TASK) ?: return Result.failure()
            return when (uploader.upload(taskId)) {
                SendOutcome.SENT -> Result.success()
                SendOutcome.RETRY -> Result.retry()
                SendOutcome.DROP -> Result.failure()
            }
        }

        companion object {
            const val KEY_TASK = "taskId"
        }
    }

/** Uploads the picked profile picture (5a, NUD-72); retried with backoff until the server has it or refuses it. */
@HiltWorker
class AvatarUploadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val uploader: AvatarUploader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            when (uploader.upload()) {
                SendOutcome.SENT -> Result.success()
                SendOutcome.RETRY -> Result.retry()
                SendOutcome.DROP -> Result.failure()
            }
    }

class WorkManagerScheduling(
    private val workManager: WorkManager,
) : OutboxDrainer,
    PhotoUploads,
    AvatarUploads {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun drainSoon() {
        val request =
            OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        // A new command starts a fresh drain at once. Appended, it waited behind a drain in its retry backoff, which
        // grows to hours, and the other phone never heard of the Bitti or İptal (NUD-64). A drain cut off midway sends
        // its command again with the same key, which the server answers like the first time (ADR 0006).
        workManager.enqueueUniqueWork("outbox", ExistingWorkPolicy.REPLACE, request)
    }

    override fun uploadSoon(taskId: String) {
        val request =
            OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(workDataOf(PhotoUploadWorker.KEY_TASK to taskId))
                .build()
        // A new photo for the same task (after Tekrar iste), or Şimdi tekrar dene, replaces the waiting try.
        workManager.enqueueUniqueWork(photoWork(taskId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun failing(taskId: String): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(photoWork(taskId)).map { infos ->
            infos.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }
        }

    private fun photoWork(taskId: String) = "photo-$taskId"

    override fun uploadSoon() {
        val request =
            OneTimeWorkRequestBuilder<AvatarUploadWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        // A newer pick replaces the waiting try; only the latest picture goes out.
        workManager.enqueueUniqueWork("avatar", ExistingWorkPolicy.REPLACE, request)
    }

    /** Signing out: nothing queued for this person runs any more. */
    fun cancelAll() {
        workManager.cancelAllWork()
    }

    fun syncSoon() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork("sync", ExistingWorkPolicy.REPLACE, request)
    }
}
