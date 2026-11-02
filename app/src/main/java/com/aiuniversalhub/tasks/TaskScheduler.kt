package com.aiuniversalhub.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object TaskScheduler {
    /** Enqueues a user-requested AI task. It runs only with network access. */
    fun enqueue(
        context: Context,
        title: String,
        prompt: String,
        providerId: String,
        model: String
    ): HubTask {
        require(title.isNotBlank() && prompt.isNotBlank() && providerId.isNotBlank() && model.isNotBlank())
        val record = TaskRecordStore(context).create(title)
        val input = Data.Builder()
            .putString(TaskWorker.KEY_TASK_ID, record.id)
            .putString(TaskWorker.KEY_TASK_TITLE, title)
            .putString(TaskWorker.KEY_PROMPT, prompt)
            .putString(TaskWorker.KEY_PROVIDER_ID, providerId)
            .putString(TaskWorker.KEY_MODEL, model)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .addTag("hub-ai-task")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "hub-ai-task-${record.id}",
            ExistingWorkPolicy.KEEP,
            request
        )
        return record
    }
}
