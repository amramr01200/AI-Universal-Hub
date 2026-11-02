package com.aiuniversalhub.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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