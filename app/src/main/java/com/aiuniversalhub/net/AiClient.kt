package com.aiuniversalhub.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    private fun endpoint(baseUrl: String, path: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) {
            "Enter a valid provider URL beginning with http:// or https://"
        }
        return normalized + path
    }

    fun listModels(baseUrl: String, apiKey: String): String {
        val requestBuilder = Request.Builder().url(endpoint(baseUrl, "/models")).get()
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
        http.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } } catch (_: Exception) { "" }
                error("Provider HTTP ${response.code}" + if (detail.isNotBlank()) ": ${detail.take(240)}" else "")
            }
            return body.ifBlank { "{}" }
        }
    }

    fun parseModelIds(raw: String): List<String> {
        val root = JSONObject(raw)
        val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index)
            (item?.optString("id") ?: item?.optString("name"))?.takeIf { it.isNotBlank() && it != "null" }
        }.distinct().sorted()
    }

    fun chatWithImage(baseUrl: String, apiKey: String, model: String, message: String, mimeType: String, imageBase64: String): String {
        require(model.isNotBlank()) { "Select a model before sending a message." }
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", message.ifBlank { "Describe this image." }))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mimeType;base64,$imageBase64")))
        val payload = JSONObject().put("model", model).put(
            "messages",
            JSONArray().put(JSONObject().put("role", "user").put("content", content))
        )
        val requestBuilder = Request.Builder().url(endpoint(baseUrl, "/chat/completions"))
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
        val request = requestBuilder.post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } } catch (_: Exception) { "" }
                error("Provider HTTP ${response.code}" + if (detail.isNotBlank()) ": ${detail.take(240)}" else "")
            }
            return body.ifBlank { "{}" }
        }
    }

    fun chat(baseUrl: String, apiKey: String, model: String, message: String): String {
        require(model.isNotBlank()) { "Select a model before sending a message." }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", message)))
        val requestBuilder = Request.Builder().url(endpoint(baseUrl, "/chat/completions"))
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
        val request = requestBuilder
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } } catch (_: Exception) { "" }
                error("Provider HTTP ${response.code}" + if (detail.isNotBlank()) ": ${detail.take(240)}" else "")
            }
            return body.ifBlank { "{}" }
        }
    }
}
