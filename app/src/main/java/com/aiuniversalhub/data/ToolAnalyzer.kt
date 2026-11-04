package com.aiuniversalhub.data

import org.json.JSONArray
import org.json.JSONObject

data class ImportedProvider(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<String> = emptyList()
)

object ToolAnalyzer {

    /**
     * يقرأ ملف JSON ويستخرج منه المزودين والمفاتيح.
     * الصيغة المدعومة:
     * {
     *   "providers": [
     *     { "name": "OpenRouter", "url": "https://openrouter.ai/api/v1", "apiKey": "sk-...", "models": ["gpt-4", "claude-3"] }
     *   ]
     * }
     */
    fun parseProvidersJson(jsonString: String): Result<List<ImportedProvider>> {
        return try {
            val root = JSONObject(jsonString)
            val providersArray = root.optJSONArray("providers") ?: JSONArray()
            val result = mutableListOf<ImportedProvider>()

            for (i in 0 until providersArray.length()) {
                val obj = providersArray.getJSONObject(i)
                val name = obj.optString("name").trim()
                val url = obj.optString("url").trim()
                val key = obj.optString("apiKey").trim()
                val modelsArray = obj.optJSONArray("models")
                val models = mutableListOf<String>()

                if (modelsArray != null) {
                    for (j in 0 until modelsArray.length()) {
                        models.add(modelsArray.getString(j))
                    }
                }

                if (name.isNotBlank() && url.isNotBlank()) {
                    result.add(ImportedProvider(name, url, key, models))
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(Exception("ملف JSON غير صالح: ${e.message}"))
        }
    }

    /**
     * يقرأ كود أداة (مثل سكربت Python) ويبحث عن أسماء النماذج المحتملة داخلها.
     * يعتمد على البحث عن الكلمات المفتاحية الشهيرة للنماذج.
     */
    fun extractModelsFromCode(codeString: String): List<String> {
        val foundModels = mutableSetOf<String>()
        
        // قائمة بالكلمات المفتاحية التي تشير إلى أسماء نماذج الذكاء الاصطناعي الشهيرة
        val modelKeywords = listOf("gpt-", "claude-", "deepseek", "llama", "mistral", "gemini", "qwen", "yi-", "mixtral", "phi-")
        
        // بحث بسيط عن النصوص المحصورة بين علامات التنصيص
        val regex = Regex("""["']([^"']+)["']""")
        val matches = regex.findAll(codeString)
        
        for (match in matches) {
            val text = match.groupValues[1]
            // إذا كان النص يحتوي على كلمة مفتاحية لنموذج، نضيفه للقائمة
            if (modelKeywords.any { text.contains(it, ignoreCase = true) }) {
                // نتحقق من أن النص ليس طويلاً جداً (لأنه مجرد اسم نموذج)
                if (text.length < 50 && !text.contains(" ")) {
                    foundModels.add(text)
                }
            }
        }
        return foundModels.toList().sorted()
    }
}
