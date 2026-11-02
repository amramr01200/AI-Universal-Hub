package com.aiuniversalhub.registry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HubTool(
    val id: String,
    val name: String,
    val kind: String,
    val launchUrl: String,
    val notes: String,
    val status: String,
    val updatedAt: Long
)

class ToolRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("hub_tool_registry", Context.MODE_PRIVATE)

    fun all(): List<HubTool> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            HubTool(
                id = o.optString("id"), name = o.optString("name"),
                kind = o.optString("kind"), launchUrl = o.optString("launchUrl"),
                notes = o.optString("notes"), status = o.optString("status", "saved"),
                updatedAt = o.optLong("updatedAt")
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun save(tool: HubTool) {
        val items = all().filterNot { it.id == tool.id }.toMutableList()
        items.add(tool.copy(updatedAt = System.currentTimeMillis()))
        persist(items)
    }

    fun remove(id: String) = persist(all().filterNot { it.id == id })

    private fun persist(items: List<HubTool>) {
        val array = JSONArray()
        items.forEach { t ->
            array.put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("kind", t.kind)
                put("launchUrl", t.launchUrl); put("notes", t.notes)
                put("status", t.status); put("updatedAt", t.updatedAt)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    companion object {
        fun newTool(name: String, kind: String, url: String, notes: String) =
            HubTool(UUID.randomUUID().toString(), name.trim(), kind, url.trim(), notes.trim(), "saved", System.currentTimeMillis())
    }
}