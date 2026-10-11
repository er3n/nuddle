package app.nuddle.data.outbox

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nuddle.data.sync.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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

class WorkManagerScheduling(
    private val workManager: WorkManager,
) : OutboxDrainer {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun drainSoon() {
        val request =
            OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork("outbox", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
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
