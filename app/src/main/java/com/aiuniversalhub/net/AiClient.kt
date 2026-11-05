package com.aiuniversalhub.net

import android.content.Context
import com.aiuniversalhub.runtime.TermuxBridgeAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    private val scanHttp = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private fun endpoint(baseUrl: String, path: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) {
            "Enter a valid provider URL beginning with http:// or https://"
        }
        val deepSeekBase = if (normalized.equals("https://api.deepseek.com", ignoreCase = true)) {
            "$normalized/v1"
        } else normalized
        return deepSeekBase + path
    }

    private fun requestBuilder(baseUrl: String, apiKey: String, path: String): Request.Builder {
        val builder = Request.Builder().url(endpoint(baseUrl, path))
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        return builder
    }

    // دالة جديدة: إرسال الطلب عبر Termux Bridge لتجاوز حظر الشبكة
    private suspend fun sendViaTermux(context: Context, endpointUrl: String, payload: String): String = withContext(Dispatchers.IO) {
        // نستخدم أمر curl لإرسال الطلب. نستخدم Base64 لتجنب مشاكل علامات التنصيص
        val base64Payload = android.util.Base64.encodeToString(payload.toByteArray(), android.util.Base64.NO_WRAP)
        val command = "echo '$base64Payload' | base64 -d | curl -s -X POST '$endpointUrl' -H 'Content-Type: application/json' -d @-"
        
        val result = TermuxBridgeAdapter.sendCustomApprovedCommand(context, command, "ollama-chat", -1)
        if (!result.submitted) throw Exception("فشل إرسال الأمر إلى Termux: ${result.message}")
        
        // انتظار بسيط ليقوم Termux بتنفيذ الأمر (سنعتمد على TermuxResultReceiver لاحقاً في التطبيق الكامل)
        delay(5000) 
        
        // في هذا الحل السريع، سنقرأ النتيجة من الذاكرة المشتركة (SharedPreferences)
        // ملاحظة: هذا يتطلب تعديل TermuxResultReceiver ليكون متزامناً، ولكن للتبسيط سنستخدم شبكة OkHttp إذا كان الرابط خارجياً
        throw Exception("تم إرسال الأمر لـ Termux، يرجى تحديث النتيجة من الشاشة")
    }

    fun listModels(baseUrl: String, apiKey: String): String {
        // إذا كان الرابط محلياً، نستخدم OkHttp (لأننا في AiClient لا نملك Context)
        // في التطبيق الحقيقي، يجب استخدام Termux Bridge هنا
        http.newCall(requestBuilder(baseUrl, apiKey, "/models").get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(providerError(response.code, body))
            return body.ifBlank { "{}" }
        }
    }

    fun quickScan(baseUrl: String): Boolean {
        return try {
            val req = requestBuilder(baseUrl, "", "/models").get().build()
            scanHttp.newCall(req).execute().use { response -> response.isSuccessful }
        } catch (_: Exception) { false }
    }

    fun parseModelIds(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val data = try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("data")
                        ?: root.optJSONArray("models")
                        ?: root.optJSONObject("data")?.optJSONArray("models")
                        ?: root.optJSONObject("result")?.optJSONArray("models")
                        ?: JSONArray()
                }
            }
        } catch (_: Exception) { return emptyList() }
        
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val rawId = listOf("id", "name", "model").firstNotNullOfOrNull { field ->
                item.optString(field).takeIf { it.isNotBlank() && it != "null" }
            } ?: return@mapNotNull null
            
            var cleanId = rawId
            if (cleanId.startsWith("models/")) cleanId = cleanId.removePrefix("models/")
            cleanId.takeIf { it.isNotBlank() && it != "/" }
        }.distinct().sorted()
    }

    fun getProviderBalance(providerName: String, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val endpointUrl = when (providerName.trim().lowercase()) {
            "openrouter" -> "https://openrouter.ai/api/v1/credits"
            "deepseek" -> "https://api.deepseek.com/user/balance"
            else -> return null
        }
        val request = Request.Builder().url(endpointUrl).header("Authorization", "Bearer $apiKey").get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(providerError(response.code, body))
            val root = JSONObject(body)
            return when (providerName.trim().lowercase()) {
                "openrouter" -> {
                    val data = root.optJSONObject("data") ?: return null
                    val remaining = data.optDouble("total_credits") - data.optDouble("total_usage")
                    "$" + String.format(java.util.Locale.US, "%.2f", remaining.coerceAtLeast(0.0))
                }
                "deepseek" -> {
                    val balances = root.optJSONArray("balance_infos") ?: return null
                    val item = (0 until balances.length()).mapNotNull { balances.optJSONObject(it) }.firstOrNull { it.optString("currency") == "USD" } ?: return null
                    "${item.optString("currency")} ${item.optString("total_balance")}"
                }
                else -> null
            }
        }
    }

    fun parseUsage(raw: String): TokenUsage {
        val usage = try { JSONObject(raw).optJSONObject("usage") } catch (_: Exception) { null } ?: return TokenUsage(null, null, null)
        fun value(vararg keys: String): Int? {
            keys.forEach { key -> if (usage.has(key) && !usage.isNull(key)) return usage.optInt(key).takeIf { it >= 0 } }
            return null
        }
        return TokenUsage(value("prompt_tokens", "input_tokens"), value("completion_tokens", "output_tokens"), value("total_tokens"))
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
        val request = requestBuilder(baseUrl, apiKey, "/chat/completions").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(providerError(response.code, body))
            return body.ifBlank { "{}" }
        }
    }

    fun chatWithImage(baseUrl: String, apiKey: String, model: String, message: String, mimeType: String, imageBase64: String): String =
        chatWithImageHistory(baseUrl, apiKey, model, emptyList(), message, mimeType, imageBase64)

    fun chatWithImageHistory(baseUrl: String, apiKey: String, model: String, history: List<Pair<String, String>>, message: String, mimeType: String, imageBase64: String): String {
        require(model.isNotBlank()) { "Select a model before sending a message." }
        val jsonMessages = JSONArray()
        history.forEach { (role, content) ->
            if (role == "system" || role == "user" || role == "assistant") jsonMessages.put(JSONObject().put("role", role).put("content", content))
        }
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", message.ifBlank { "Describe this image." }))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mimeType;base64,$imageBase64")))
        jsonMessages.put(JSONObject().put("role", "user").put("content", content))
        val payload = JSONObject().put("model", model).put("messages", jsonMessages)
        val request = requestBuilder(baseUrl, apiKey, "/chat/completions").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(providerError(response.code, body))
            return body.ifBlank { "{}" }
        }
    }

    fun chat(baseUrl: String, apiKey: String, model: String, message: String): String = chatWithHistory(baseUrl, apiKey, model, listOf("user" to message))

    private fun providerError(code: Int, body: String): String {
        val detail = try {
            val root = JSONObject(body)
            root.optString("message").ifBlank { val error = root.opt("error"); if (error is JSONObject) error.optString("message") else error?.toString().orEmpty() }
        } catch (_: Exception) { "" }
        return "Provider HTTP $code" + if (detail.isNotBlank()) ": ${detail.take(240)}" else ""
    }
}
