package app.nuddle.data.example

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
import androidx.work.workDataOf
import app.nuddle.data.outbox.reportingFailures
import app.nuddle.domain.Diagnostics
import app.nuddle.domain.SendOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val BACKOFF_SECONDS = 30L

/** Uploads the example photo of a task (NUD-138); retried with backoff until the server has it or refuses it. */
@HiltWorker
class ExampleUploadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val uploader: ExampleUploader,
        private val diagnostics: Diagnostics,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val taskId = inputData.getString(KEY_TASK) ?: return Result.failure()
            val outcome = diagnostics.reportingFailures("example") { uploader.upload(taskId) }
            diagnostics.log("example upload $taskId $outcome")
            return when (outcome) {
                SendOutcome.SENT -> Result.success()
                SendOutcome.RETRY -> Result.retry()
                SendOutcome.DROP -> Result.failure()
            }
        }

        companion object {
            const val KEY_TASK = "taskId"
        }
    }

/** The example photo uploads on WorkManager; signing out cancels them with all other work. */
class WorkManagerExampleUploads(
    private val workManager: WorkManager,
) : ExampleUploads {
    override fun uploadSoon(taskId: String) {
        val request =
            OneTimeWorkRequestBuilder<ExampleUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(workDataOf(ExampleUploadWorker.KEY_TASK to taskId))
                .build()
        workManager.enqueueUniqueWork("example-$taskId", ExistingWorkPolicy.REPLACE, request)
    }
}
