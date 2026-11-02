package com.aiuniversalhub.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiuniversalhub.data.SecureStore
import com.aiuniversalhub.net.AiClient
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable, network-constrained AI task worker.
 * Credentials are read from encrypted storage by provider ID and never placed in WorkManager input.
 */
class TaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TASK_TITLE) ?: "Background AI task"
        val prompt = inputData.getString(KEY_PROMPT).orEmpty()
        val providerId = inputData.getString(KEY_PROVIDER_ID).orEmpty()
        val model = inputData.getString(KEY_MODEL).orEmpty()
        val records = TaskRecordStore(applicationContext)
        if (prompt.isBlank() || providerId.isBlank() || model.isBlank()) {
            records.update(id, "needs_attention", 0, "Missing prompt, provider, or model configuration.")
            return@withContext Result.failure()
        }
        if (isStopped) {
            records.update(id, "paused", 0, "Stopped by the system or user.")
            return@withContext Result.retry()
        }
        records.update(id, "running", 15, "Connecting to the selected AI provider…")
        try {
            val secure = SecureStore(applicationContext)
            val endpoint = secure.getProviderUrl(providerId)
            val key = secure.getApiKey(providerId).orEmpty()
            require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
                "The selected provider has no valid saved endpoint."
            }
            records.update(id, "running", 45, "Sending the approved task to $title")
            val response = AiClient().chat(endpoint, key, model, prompt)
            val answer = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                ?: response
            records.update(id, "completed", 100, answer.take(3500))
            Result.success()
        } catch (e: Exception) {
            records.update(id, "failed", 0, (e.message ?: "Background task failed").take(500))
            Result.failure()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_PROMPT = "task_prompt"
        const val KEY_PROVIDER_ID = "provider_id"
        const val KEY_MODEL = "model"
    }
}
