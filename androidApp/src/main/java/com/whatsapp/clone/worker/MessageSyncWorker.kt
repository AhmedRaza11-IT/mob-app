package com.whatsapp.clone.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.whatsapp.clone.db.MessageDao
import java.util.concurrent.TimeUnit

class MessageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val messageDao: MessageDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val pendingMessages = messageDao.getPendingMessages()
        if (pendingMessages.isEmpty()) return androidx.work.ListenableWorker.Result.success()

        for (msg in pendingMessages) {
            // Simulated WebSocket gateway dispatch logic
            val sentSuccessfully = true 
            if (sentSuccessfully) {
                messageDao.updateMessageStatus(msg.id, "SENT")
            } else {
                return androidx.work.ListenableWorker.Result.retry()
            }
        }
        return androidx.work.ListenableWorker.Result.success()
    }
}

fun enqueueMessageSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val syncRequest = OneTimeWorkRequestBuilder<MessageSyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "OfflineMessageSync",
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        syncRequest
    )
}
