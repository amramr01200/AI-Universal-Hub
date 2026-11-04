package com.aiuniversalhub.chat

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiuniversalhub.data.SecureStore
import com.aiuniversalhub.data.ToolAnalyzer
import com.aiuniversalhub.net.AiClient
import com.aiuniversalhub.net.LiveSearchClient
import com.aiuniversalhub.runtime.TermuxBridgeAdapter
import com.aiuniversalhub.tasks.TaskScheduler
import com.aiuniversalhub.tasks.TaskRecordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

private data class ChatMessage(val role: String, val content: String)

private data class ModelOption(
    val displayName: String,
    val actualName: String,
    val providerId: String,
    val providerName: String,
    val baseUrl: String,
    val apiKey: String
)

private fun providerStorageId(name: String, providers: List<com.aiuniversalhub.data.SavedProvider>): String =
    providers.firstOrNull { it.name == name }?.id ?: name.lowercase().replace(" ", "-")
    
private data class ChatThread(
    val id: Int,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val model: String = ""
)

private fun encryptedChatPreferences(context: android.content.Context): android.content.SharedPreferences {
    val masterKey = androidx.security.crypto.MasterKey.Builder(context)
        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
        .build()
    return androidx.security.crypto.EncryptedSharedPreferences.create(
        context,
        "hub_chats_secure",
        masterKey,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun loadChatThreads(context: android.content.Context): List<ChatThread> {
    return try {
        val secure = encryptedChatPreferences(context)
        var raw = secure.getString("threads", null)
        if (raw == null) {
            val legacy = context.getSharedPreferences("hub_chats", android.content.Context.MODE_PRIVATE)
            raw = legacy.getString("threads", null)
            if (raw != null) {
                secure.edit().putString("threads", raw).commit()
                legacy.edit().remove("threads").commit()
            }
        }
        raw ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val messagesJson = obj.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesJson.length()).map { i ->
                val m = messagesJson.getJSONObject(i)
                ChatMessage(m.optString("role"), m.optString("content"))
            }
            ChatThread(obj.optInt("id"), obj.optString("title"), messages, obj.optString("model"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveChatThreads(context: android.content.Context, threads: List<ChatThread>) {
    val array = JSONArray()
    threads.forEach { thread ->
        val messages = JSONArray()
        thread.messages.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        array.put(JSONObject().put("id", thread.id).put("title", thread.title)
            .put("model", thread.model).put("messages", messages))
    }
    encryptedChatPreferences(context).edit().putString("threads", array.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHomeScreen(isArabic: Boolean, openAiSettings: Boolean = false, onAiSettingsConsumed: () -> Unit = {}, topSelectedModel: String = "", onTopModelSelected: (String) -> Unit = {}, onTopModelOptionsChanged: (String, List<String>) -> Unit = { _, _ -> }, onTopTokenCountChanged: (String) -> Unit = {}, onSelectedConversationChanged: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }
    val client = remember { AiClient() }
    val scope = rememberCoroutineScope()
    var threads by remember {
        mutableStateOf(loadChatThreads(context).ifEmpty {
            listOf(ChatThread(1, if (isArabic) "محادثة جديدة" else "New chat"))
        })
    }
    var selectedId by remember { mutableIntStateOf(threads.first().id) }
    var bridgeRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) bridgeRefresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(selectedId) { onSelectedConversationChanged(selectedId) }
    LaunchedEffect(selectedId) {
        while (true) {
            bridgeRefresh++
            kotlinx.coroutines.delay(1000)
        }
    }
    var nextId by remember { mutableIntStateOf((threads.maxOfOrNull { it.id } ?: 0) + 1) }
    var draft by remember { mutableStateOf("") }
    val providerOptions = listOf(
        Triple("OpenAI", "https://api.openai.com/v1", true),
        Triple("OpenAI-compatible", "https://api.openai.com/v1", true),
        Triple("OpenRouter", "https://openrouter.ai/api/v1", true),
        Triple("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", true),
        Triple("Groq", "https://api.groq.com/openai/v1", true),
        Triple("DeepSeek", "https://api.deepseek.com/v1", true),
        Triple("Mistral AI", "https://api.mistral.ai/v1", true),
        Triple("Together AI", "https://api.together.xyz/v1", true),
        Triple("FreeModel", "https://freemodel.in/v1", true),
        Triple("AIMLAPI", "https://api.aimlapi.com/v1", true),
        Triple("LiteLLM", "http://localhost:4000/v1", true),
        Triple("Ollama", "http://localhost:11434/v1", false),
        Triple("FreeLLMAPI", "", true)
    )
    var registeredProviders by remember { mutableStateOf(store.listProviders()) }
    val allProviderOptions = providerOptions + registeredProviders.map { Triple(it.name, it.baseUrl, true) }
    val initialProvider = allProviderOptions.firstOrNull {
        providerStorageId(it.first, registeredProviders) == store.getSelectedProvider()
    } ?: providerOptions.first()
    var providerName by remember { mutableStateOf(initialProvider.first) }
    var showAddProvider by remember { mutableStateOf(false) }
    var newProviderName by remember { mutableStateOf("") }
    var newProviderUrl by remember { mutableStateOf("") }
    var newProviderKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(store.getProviderUrl(providerStorageId(initialProvider.first, registeredProviders), initialProvider.second)) }
    var apiKey by remember { mutableStateOf(store.getApiKey(providerStorageId(initialProvider.first, registeredProviders)) ?: "") }
    var modelOptions by remember { mutableStateOf(store.getModels(providerStorageId(initialProvider.first, registeredProviders))) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLiveSearch by remember { mutableStateOf(false) }
    var showTaskManager by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var usageLabel by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    
    var pendingTextFileContent by remember { mutableStateOf<String?>(null) }
    var pendingTextFileName by remember { mutableStateOf<String?>(null) }

    val allGlobalModels = remember(registeredProviders, bridgeRefresh) {
        val list = mutableListOf<ModelOption>()
        registeredProviders.forEach { provider ->
            val models = store.getModels(provider.id)
            val key = store.getApiKey(provider.id) ?: ""
            models.forEach { modelName ->
                list.add(ModelOption(modelName, modelName, provider.id, provider.name, provider.baseUrl, key))
            }
        }
        
        val grouped = list.groupBy { it.actualName }
        val numbered = mutableListOf<ModelOption>()
        grouped.forEach { (name, options) ->
            if (options.size == 1) {
                numbered.add(options[0])
            } else {
                options.sortedBy { it.providerName }.forEachIndexed { index, opt ->
                    numbered.add(opt.copy(displayName = "$name (${index + 1})"))
                }
            }
        }
        numbered.sortedBy { it.displayName.lowercase() }
    }

    LaunchedEffect(openAiSettings) {
        if (openAiSettings) {
            showSettings = true
            onAiSettingsConsumed()
        }
    }

    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            status = if (isArabic) "جاري قراءة ملف JSON واستيراد المزودين..." else "Reading JSON file and importing providers..."
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val result = ToolAnalyzer.parseProvidersJson(content)
                        result.onSuccess { providers ->
                            providers.forEach { p ->
                                val id = p.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                                store.saveProvider(id, p.name, p.baseUrl, p.apiKey)
                                if (p.models.isNotEmpty()) {
                                    store.saveModels(id, p.models)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                registeredProviders = store.listProviders()
                                status = if (isArabic) "تم استيراد ${providers.size} مزود بنجاح!" else "Imported ${providers.size} providers successfully!"
                            }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) { status = "خطأ: ${e.message}" }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { status = "فشل قراءة الملف: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { busy = false }
                }
            }
        }
    }

    val textFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    val fileName = uri.lastPathSegment ?: "file.txt"
                    if (content != null) {
                        withContext(Dispatchers.Main) {
                            pendingTextFileContent = content
                            pendingTextFileName = fileName
                            status = if (isArabic) "تم إرفاق الملف: $fileName" else "File attached: $fileName"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        status = if (isArabic) "فشل قراءة الملف: ${e.message}" else "Failed to read file: ${e.message}"
                    }
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingImage = uri
        if (uri != null) status = if (isArabic) "تم إرفاق الصورة. اكتبي رسالتك ثم اضغطي إرسال." else "Image attached. Add a message and press Send."
    }
    val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) draft = listOf(draft, spoken).filter { it.isNotBlank() }.joinToString(" ")
    }

    LaunchedEffect(threads) { saveChatThreads(context, threads) }
    LaunchedEffect(threads) {
        if (threads.none { it.id == selectedId }) selectedId = threads.first().id
    }
    LaunchedEffect(bridgeRefresh, selectedId) {
        try {
            val key = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            val resultPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context, com.aiuniversalhub.runtime.TermuxResultReceiver.PREFS, key,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            val chatPrefs = encryptedChatPreferences(context)
            val importKey = "termux_imported_ids_$selectedId"
            val importedArray = try { JSONArray(chatPrefs.getString(importKey, "[]")) } catch (_: Exception) { JSONArray() }
            val importedIds = mutableSetOf<String>()
            for (index in 0 until importedArray.length()) importedIds.add(importedArray.optString(index))
            val history = try { JSONArray(resultPrefs.getString("results_history", "[]")) } catch (_: Exception) { JSONArray() }
            val newMessages = mutableListOf<ChatMessage>()
            val newlyImported = mutableListOf<String>()
            for (index in 0 until history.length()) {
                val record = history.optJSONObject(index) ?: continue
                val id = record.optString("id")
                if (id.isBlank() || id in importedIds || record.optInt("conversation_id", -1) != selectedId) continue
                val output = record.optString("stdout")
                val error = record.optString("stderr")
                val errorMessage = record.optString("error_message")
                if (output.isBlank() && error.isBlank() && errorMessage.isBlank()) continue
                val body = buildString {
                    append("Termux • "); append(record.optString("label", "result"))
                    if (output.isNotBlank()) { append("\n\n"); append(output) }
                    if (error.isNotBlank()) { append("\n\n[stderr]\n"); append(error) }
                    if (errorMessage.isNotBlank()) { append("\n\n"); append(errorMessage) }
                    append("\n\nExit code: "); append(record.optInt("exit_code", -1))
                }
                newMessages.add(ChatMessage("tool", body))
                newlyImported.add(id)
            }
            if (newMessages.isNotEmpty()) {
                threads = threads.map { thread ->
                    if (thread.id == selectedId) thread.copy(messages = thread.messages + newMessages) else thread
                }
                val updatedIds = (importedIds + newlyImported).toList().takeLast(100)
                val savedIds = JSONArray()
                updatedIds.forEach { savedIds.put(it) }
                chatPrefs.edit().putString(importKey, savedIds.toString()).apply()
            }
        } catch (_: Exception) { }
    }
    val selected = threads.firstOrNull { it.id == selectedId } ?: threads.first()

    val currentDisplayName = allGlobalModels.find { it.actualName == selected.model }?.displayName ?: selected.model
    LaunchedEffect(currentDisplayName, allGlobalModels) {
        onTopModelOptionsChanged(currentDisplayName, allGlobalModels.map { it.displayName })
    }

    LaunchedEffect(providerName, registeredProviders, apiKey) {
        val configured = (listOf(
            com.aiuniversalhub.data.SavedProvider(
                providerStorageId(providerName, registeredProviders), providerName, baseUrl
            )
        ) + registeredProviders).distinctBy { it.id }
        val values = withContext(Dispatchers.IO) {
            configured.map { item ->
                val key = if (item.name == providerName) apiKey
                    else store.getApiKey(item.id).orEmpty()
                val balance = try {
                    client.getProviderBalance(item.name, key)
                } catch (_: Exception) { null }
                balance ?: (if (isArabic) "غير متاح" else "N/A")
            }
        }
        onTopTokenCountChanged(values.joinToString(" / "))
    }

    LaunchedEffect(topSelectedModel, selectedId, allGlobalModels) {
        if (topSelectedModel.isNotBlank()) {
            val option = allGlobalModels.find { it.displayName == topSelectedModel }
            if (option != null && option.actualName != selected.model) {
                threads = threads.map { if (it.id == selectedId) it.copy(model = option.actualName) else it }
                store.saveSelectedModel(option.providerId, option.actualName)
                
                if (providerName != option.providerName) {
                    providerName = option.providerName
                    baseUrl = option.baseUrl
                    apiKey = option.apiKey
                    store.saveSelectedProvider(option.providerId)
                    modelOptions = store.getModels(option.providerId)
                }
            }
        }
    }

    fun updateSelectedModel(value: String) {
        threads = threads.map { if (it.id == selectedId) it.copy(model = value) else it }
        store.saveSelectedModel(providerStorageId(providerName, registeredProviders), value)
    }

    fun sendMessage() {
        var prompt = draft.trim()
        val imageUri = pendingImage
        val fileContent = pendingTextFileContent
        val fileName = pendingTextFileName
        
        if (prompt.isBlank() && imageUri == null && fileContent == null) return
        if (selected.model.isBlank()) {
            status = if (isArabic) "اختاري نموذج المحادثة من القائمة أولًا، ثم أرسلي الرسالة." else "Select a conversation model before sending."
            return
        }
        
        if (fileContent != null) {
            prompt = "محتوى الملف المرفق ($fileName):\n```\n$fileContent\n```\n\n" + prompt
        }
        
        val userText = prompt + if (imageUri != null) (if (prompt.isBlank()) "" else "\n") + (if (isArabic) "📎 صورة مرفقة" else "📎 Image attached") else ""
        val thread = threads.first { it.id == selectedId }
        val updated = thread.copy(
            title = if (thread.messages.isEmpty()) (prompt.ifBlank { if (isArabic) "تحليل ملف/صورة" else "File/Image question" }).take(36) else thread.title,
            messages = thread.messages + ChatMessage("user", userText)
        )
        threads = threads.map { if (it.id == selectedId) updated e
