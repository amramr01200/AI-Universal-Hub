package com.aiuniversalhub.runtime

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class TermuxAgentProfile(val id: String, val name: String, val conversationId: Int, val runtimeId: String, val workingDirectory: String)

class TermuxAgentStore(context: Context) {
    private val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(context, "termux_agents_secure", key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    fun list(): List<TermuxAgentProfile> = try {
        val array = JSONArray(prefs.getString("profiles", "[]"))
        (0 until array.length()).map { i -> val o=array.getJSONObject(i); TermuxAgentProfile(o.optString("id"),o.optString("name"),o.optInt("conversationId"),o.optString("runtimeId"),o.optString("workingDirectory")) }
    } catch (_: Exception) { emptyList() }

    fun save(profile: TermuxAgentProfile) {
        require(profile.id.isNotBlank() && profile.name.isNotBlank() && profile.conversationId > 0)
        val items = list().filterNot { it.id == profile.id || (it.conversationId == profile.conversationId && it.runtimeId == profile.runtimeId) }.toMutableList()
        items.add(profile)
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("conversationId",it.conversationId).put("runtimeId",it.runtimeId).put("workingDirectory",it.workingDirectory)) }
        prefs.edit().putString("profiles",array.toString()).apply()
    }

    fun delete(id: String) {
        val array=JSONArray(); list().filterNot { it.id==id }.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("conversationId",it.conversationId).put("runtimeId",it.runtimeId).put("workingDirectory",it.workingDirectory)) }
        prefs.edit().putString("profiles",array.toString()).apply()
    }
}
