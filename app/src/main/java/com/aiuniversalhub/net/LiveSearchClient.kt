package com.aiuniversalhub.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchHit(
    val title: String,
    val url: String,
    val description: String,
    val age: String?
)

class LiveSearchClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    /** Brave Search API. The subscription/key is owned by the user. */
    fun search(query: String, apiKey: String, count: Int = 8): List<SearchHit> {
        require(query.isNotBlank()) { "اكتبي عبارة البحث أولًا." }
        require(apiKey.isNotBlank()) { "أضيفي مفتاح Brave Search API في إعدادات البحث." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://api.search.brave.com/res/v1/web/search?q=$encoded&count=${count.coerceIn(1,20)}"
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", apiKey.trim())
            .get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try { JSONObject(body).optString("message") } catch (_: Exception) { "" }
                error("Search API HTTP ${response.code}" + if (message.isNotBlank()) ": ${message.take(180)}" else "")
            }
            val results = JSONObject(body).optJSONObject("web")?.optJSONArray("results")
                ?: return emptyList()
            return (0 until results.length()).mapNotNull { i ->
                val item = results.optJSONObject(i) ?: return@mapNotNull null
                val link = item.optString("url")
                if (!link.startsWith("https://")) return@mapNotNull null
                SearchHit(
                    item.optString("title").ifBlank { link },
                    link,
                    item.optString("description"),
                    item.optString("age").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
    }
}
