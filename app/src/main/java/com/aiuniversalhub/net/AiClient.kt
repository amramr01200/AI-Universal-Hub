package com.aiuniversalhub.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AiClient {
    private val http = OkHttpClient()
    fun listModels(baseUrl: String, apiKey: String): String {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/models")
            .header("Authorization", "Bearer $apiKey").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Provider returned HTTP ${response.code}")
            return response.body?.string() ?: "{}"
        }
    }

    fun chat(baseUrl: String, apiKey: String, model: String, message: String): String {
        val payload = JSONObject()
            .put("model", model)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", message)))
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Provider returned HTTP ${response.code}")
            return response.body?.string() ?: "{}"
        }
    }
}