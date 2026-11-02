package com.aiuniversalhub.tasks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HubTask(
    val id: String,
    val title: String,
    val state: String,
    val progress: Int,
    val detail: String,
    val updatedAt: Long
)

class TaskRecordStore(context: Context) {
    private val prefs = context.getSharedPreferences("hub_task_records", Context.MODE_PRIVATE)
    @Synchronized fun all(): List<HubTask> {
        val a = JSONArray(prefs.getString("tasks", "[]") ?: "[]")
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            HubTask(o.optString("id"), o.optString("title"), o.optString("state"),
                o.optInt("progress"), o.optString("detail"), o.optLong("updatedAt"))
        }.sortedByDescending { it.updatedAt }
    }
    @Synchronized fun create(title: String): HubTask {
        require(title.isNotBlank())
        val task = HubTask(UUID.randomUUID().toString(), title.trim(), "queued", 0, "Waiting to start", System.currentTimeMillis())
        save(all() + task); return task
    }
    @Synchronized fun update(id: String, state: String, progress: Int, detail: String) {
        save(all().map { if (it.id == id) it.copy(state=state, progress=progress.coerceIn(0,100), detail=detail, updatedAt=System.currentTimeMillis()) else it })
    }
    private fun save(tasks: List<HubTask>) {
        val a = JSONArray()
        tasks.take(300).forEach { t -> a.put(JSONObject().put("id",t.id).put("title",t.title)
            .put("state",t.state).put("progress",t.progress).put("detail",t.detail).put("updatedAt",t.updatedAt)) }
        prefs.edit().putString("tasks", a.toString()).apply()
    }
}
