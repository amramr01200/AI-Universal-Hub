package com.aiuniversalhub.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ApprovalRecord(
    val id: String,
    val action: String,
    val detail: String,
    val status: String,
    val createdAt: Long
)

class ApprovalAuditStore(context: Context) {
    private val prefs = context.getSharedPreferences("hub_approval_audit", Context.MODE_PRIVATE)

    @Synchronized fun records(): List<ApprovalRecord> {
        val array = JSONArray(prefs.getString("records", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            ApprovalRecord(o.optString("id"), o.optString("action"), o.optString("detail"),
                o.optString("status"), o.optLong("createdAt"))
        }.sortedByDescending { it.createdAt }
    }

    @Synchronized fun record(action: String, detail: String, status: String): ApprovalRecord {
        val entry = ApprovalRecord(UUID.randomUUID().toString(), action, detail, status, System.currentTimeMillis())
        val next = (records() + entry).take(1000)
        val array = JSONArray()
        next.forEach { r ->
            array.put(JSONObject().put("id", r.id).put("action", r.action).put("detail", r.detail)
                .put("status", r.status).put("createdAt", r.createdAt))
        }
        prefs.edit().putString("records", array.toString()).apply()
        return entry
    }
}
