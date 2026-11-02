package com.aiuniversalhub.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TokenUsage(val promptTokens: Int?, val completionTokens: Int?, val totalTokens: Int?)

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

    private fun requestBuilder(baseUrl: String, apiKey: String, path: String): Request.Builder {
        val builder = Request.Builder().url(endpoint(baseUrl, path))
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        return builder
    }

    fun listModels(baseUrl: String, apiKey: String): String {
        http.newCall(requestBuilder(baseUrl, apiKey, "/models").get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(providerError(response.code, body))
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

    fun parseUsage(raw: String): TokenUsage {
        val usage = JSONObject(raw).optJSONObject("usage") ?: return TokenUsage(null, null, null)
        fun value(vararg keys: String): Int? {
            keys.forEach { key ->
                if (usage.has(key) && !usage.isNull(key)) return usage.optInt(key).takeIf { it >= 0 }
            }
            return null
        }
        return TokenUsage(
            value("prompt_tokens", "input_tokens"),
            value("completion_tokens", "output_tokens"),
            value("total_tokens")
        )
    }

    fun chatWithHistory(baseUrl: String, apiKey: String, model: String, messages: List<Pair<String, String>>): String {
        require(model.isNotBlank()) { "Select a model before sending a message." }
        require(messages.isNotEmpty()) { "Add a message before sending." }
        val jsonMessages = JSONArray()
        messages.forEach { (role, content) ->
            if (role == "system" || role == "user" || role == "assistant") {
                jsonMessages.put(JSONObject().put("role", role).put("content", content))
            }
        }
        val payload = JSONObject().put("model", model).put("messages", jsonMessages)
        val request = requestBuilder(baseUrl, apiKey, "/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(providerError(response.code, body))
            return body.ifBlank { "{}" }
        }
    }

    fun chatWithImage(baseUrl: String, apiKey: String, model: String, message: String, mimeType: String, imageBase64: String): String {
        require(model.isNotBlank()) { "Select a model before sending a message." }
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", message.ifBlank { "Describe this image." }))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mimeType;base64,$imageBase64")))
        val payload = JSONObject().put("model", model).put(
            "messages", JSONArray().put(JSONObject().put("role", "user").put("content", content))
        )
        val request = requestBuilder(baseUrl, apiKey, "/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(providerError(response.code, body))
            return body.ifBlank { "{}" }
        }
    }

    fun chat(baseUrl: String, apiKey: String, model: String, message: String): String =
        chatWithHistory(baseUrl, apiKey, model, listOf("user" to message))

    private fun providerError(code: Int, body: String): String {
        val detail = try {
            val root = JSONObject(body)
            root.optString("message").ifBlank {
                val error = root.opt("error")
                if (error is JSONObject) error.optString("message") else error?.toString().orEmpty()
            }
        } catch (_: Exception) { "" }
        return "Provider HTTP $code" + if (detail.isNotBlank()) ": ${detail.take(240)}" else ""
    }
}
