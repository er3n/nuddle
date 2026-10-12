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
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = if (sender.drain()) Result.success() else Result.retry()
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

class WorkManagerScheduling(
    private val workManager: WorkManager,
) : OutboxDrainer,
    PhotoUploads {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun drainSoon() {
        val request =
            OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork("outbox", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
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

    fun syncSoon() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork("sync", ExistingWorkPolicy.REPLACE, request)
    }
}
