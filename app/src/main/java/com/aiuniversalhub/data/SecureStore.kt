package com.aiuniversalhub.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

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
}
