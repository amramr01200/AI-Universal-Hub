package com.aiuniversalhub.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Durable worker wrapper. Actual task handlers must be registered by feature modules;
 * this worker never invents a shell command or silently performs privileged actions.
 */
class TaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TASK_TITLE) ?: "Background task"
        val store = TaskRecordStore(applicationContext)
        if (isStopped) {
            store.update(id, "paused", 0, "Interrupted before handler started")
            return Result.retry()
        }
        store.update(id, "needs_attention", 0,
            "Task is saved. A feature-specific handler and any required user approval must be available before execution.")
        return Result.success()
    }
    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TITLE = "task_title"
    }
}
