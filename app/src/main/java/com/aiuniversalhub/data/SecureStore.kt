package com.aiuniversalhub.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class SavedProvider(val id: String, val name: String, val baseUrl: String)

class SecureStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "hub_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun providerIds(): MutableSet<String> =
        prefs.getStringSet("provider_registry", emptySet())?.toMutableSet() ?: mutableSetOf()

    fun saveProvider(id: String, name: String, baseUrl: String, apiKey: String) {
        val safeId = id.trim()
        require(safeId.isNotBlank())
        val ids = providerIds().apply { add(safeId) }
        prefs.edit()
            .putStringSet("provider_registry", ids)
            .putString("provider_name_$safeId", name.trim())
            .putString("provider_url_$safeId", baseUrl.trim())
            .putString("api_key_$safeId", apiKey.trim().ifBlank { null })
            .apply()
    }

    fun listProviders(): List<SavedProvider> = providerIds().mapNotNull { id ->
        val name = prefs.getString("provider_name_$id", null) ?: return@mapNotNull null
        SavedProvider(id, name, getProviderUrl(id))
    }.sortedBy { it.name.lowercase() }

    fun deleteProvider(providerId: String): String? {
        val ids = providerIds()
        if (!ids.remove(providerId)) return null
        val wasSelected = getSelectedProvider() == providerId
        val editor = prefs.edit()
            .putStringSet("provider_registry", ids)
            .remove("provider_name_$providerId")
            .remove("provider_url_$providerId")
            .remove("api_key_$providerId")
            .remove("selected_model_$providerId")
            .remove("models_$providerId")
            .remove("provider_start_cmd_$providerId") // تنظيف أمر التشغيل عند الحذف
        if (wasSelected) {
            val replacement = ids.firstOrNull()
            if (replacement == null) editor.remove("selected_provider")
            else editor.putString("selected_provider", replacement)
        }
        editor.apply()
        return if (wasSelected) ids.firstOrNull() else getSelectedProvider()
    }

    fun saveModels(providerId: String, models: List<String>) {
        val json = JSONArray()
        models.distinct().forEach(json::put)
        prefs.edit().putString("models_$providerId", json.toString()).apply()
    }

    fun getModels(providerId: String): List<String> = try {
        val json = JSONArray(prefs.getString("models_$providerId", "[]"))
        (0 until json.length()).map { json.getString(it) }
    } catch (_: Exception) { emptyList() }

    fun saveApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString("api_key_$providerId", apiKey).apply()
    }

    fun getApiKey(providerId: String): String? = prefs.getString("api_key_$providerId", null)

    fun deleteApiKey(providerId: String) {
        prefs.edit().remove("api_key_$providerId").apply()
    }

    fun saveProviderUrl(providerId: String, baseUrl: String) {
        prefs.edit().putString("provider_url_$providerId", baseUrl.trim()).apply()
    }

    fun getProviderUrl(providerId: String, defaultValue: String = ""): String =
        prefs.getString("provider_url_$providerId", defaultValue) ?: defaultValue


    /** Persist named MCP endpoints and bearer tokens in EncryptedSharedPreferences. */
    fun saveMcpServer(id: String, name: String, endpoint: String, bearerToken: String = "") {
        val safeId = id.trim()
        require(safeId.isNotBlank()) { "MCP server id is required." }
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) { "MCP endpoint must use HTTP(S)." }
        val ids = prefs.getStringSet("mcp_server_registry", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(safeId)
        prefs.edit().putStringSet("mcp_server_registry", ids)
            .putString("mcp_server_name_$safeId", name.trim())
            .putString("mcp_server_endpoint_$safeId", endpoint.trim())
            .putString("mcp_server_token_$safeId", bearerToken.trim().ifBlank { null }).apply()
    }

    fun listMcpServers(): List<JSONObject> {
        val ids = prefs.getStringSet("mcp_server_registry", emptySet()) ?: emptySet()
        return ids.mapNotNull { id ->
            val endpoint = prefs.getString("mcp_server_endpoint_$id", null) ?: return@mapNotNull null
            JSONObject().put("id", id).put("name", prefs.getString("mcp_server_name_$id", id)).put("endpoint", endpoint)
        }.sortedBy { it.optString("name").lowercase() }
    }

    fun getMcpServerToken(id: String): String = prefs.getString("mcp_server_token_$id", "") ?: ""

    fun deleteMcpServer(id: String) {
        val ids = prefs.getStringSet("mcp_server_registry", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(id)
        prefs.edit().putStringSet("mcp_server_registry", ids).remove("mcp_server_name_$id")
            .remove("mcp_server_endpoint_$id").remove("mcp_server_token_$id").apply()
    }

    fun saveSelectedProvider(providerId: String) {
        prefs.edit().putString("selected_provider", providerId).apply()
    }

    fun getSelectedProvider(defaultValue: String = "openai-compatible"): String =
        prefs.getString("selected_provider", defaultValue) ?: defaultValue

    fun saveSelectedModel(providerId: String, model: String) {
        prefs.edit().putString("selected_model_$providerId", model).apply()
    }

    fun getSelectedModel(providerId: String): String =
        prefs.getString("selected_model_$providerId", "") ?: ""

    // --- الدوال الجديدة التي أضفناها للتشغيل التلقائي ---
    fun saveProviderStartCommand(providerId: String, command: String) {
        prefs.edit().putString("provider_start_cmd_$providerId", command.trim()).apply()
    }

    fun getProviderStartCommand(providerId: String): String =
        prefs.getString("provider_start_cmd_$providerId", "") ?: ""
}
