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
private data class ModelOption(val displayName: String, val actualName: String, val providerId: String, val providerName: String, val baseUrl: String, val apiKey: String)
private fun providerStorageId(name: String, providers: List<com.aiuniversalhub.data.SavedProvider>): String = providers.firstOrNull { it.name == name }?.id ?: name.lowercase().replace(" ", "-")
private data class ChatThread(val id: Int, val title: String, val messages: List<ChatMessage> = emptyList(), val model: String = "")

private fun encryptedChatPreferences(context: android.content.Context): android.content.SharedPreferences {
    val masterKey = androidx.security.crypto.MasterKey.Builder(context).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
    return androidx.security.crypto.EncryptedSharedPreferences.create(
        context, "hub_chats_secure", masterKey,
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
            if (raw != null) { secure.edit().putString("threads", raw).commit(); legacy.edit().remove("threads").commit() }
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
        array.put(JSONObject().put("id", thread.id).put("title", thread.title).put("model", thread.model).put("messages", messages))
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
    var threads by remember { mutableStateOf(loadChatThreads(context).ifEmpty { listOf(ChatThread(1, if (isArabic) "محادثة جديدة" else "New chat")) }) }
    var selectedId by remember { mutableIntStateOf(threads.first().id) }
    var bridgeRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) bridgeRefresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(selectedId) { onSelectedConversationChanged(selectedId) }
    LaunchedEffect(selectedId) { while (true) { bridgeRefresh++; delay(1000) } }
    var nextId by remember { mutableIntStateOf((threads.maxOfOrNull { it.id } ?: 0) + 1) }
    var draft by remember { mutableStateOf("") }
    val providerOptions = listOf(
        Triple("OpenAI", "https://api.openai.com/v1", true), Triple("OpenAI-compatible", "https://api.openai.com/v1", true),
        Triple("OpenRouter", "https://openrouter.ai/api/v1", true), Triple("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", true),
        Triple("Groq", "https://api.groq.com/openai/v1", true), Triple("DeepSeek", "https://api.deepseek.com/v1", true),
        Triple("Mistral AI", "https://api.mistral.ai/v1", true), Triple("Together AI", "https://api.together.xyz/v1", true),
        Triple("FreeModel", "https://freemodel.in/v1", true), Triple("AIMLAPI", "https://api.aimlapi.com/v1", true),
        Triple("LiteLLM", "http://localhost:4000/v1", true), Triple("Ollama", "http://localhost:11434/v1", false), Triple("FreeLLMAPI", "", true)
    )
    var registeredProviders by remember { mutableStateOf(store.listProviders()) }
    val allProviderOptions = providerOptions + registeredProviders.map { Triple(it.name, it.baseUrl, true) }
    val initialProvider = allProviderOptions.firstOrNull { providerStorageId(it.first, registeredProviders) == store.getSelectedProvider() } ?: providerOptions.first()
    var providerName by remember { mutableStateOf(initialProvider.first) }
    var showAddProvider by remember { mutableStateOf(false) }
    var newProviderName by remember { mutableStateOf("") }
    var newProviderUrl by remember { mutableStateOf("") }
    var newProviderKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(store.getProviderUrl(providerStorageId(initialProvider.first, registeredProviders), initialProvider.second)) }
    var apiKey by remember { mutableStateOf(store.getApiKey(providerStorageId(initialProvider.first, registeredProviders)) ?: "") }
    var modelOptions by remember { mutableStateOf(store.getModels(providerStorageId(initialProvider.first, registeredProviders))) }
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
            models.forEach { modelName -> list.add(ModelOption(modelName, modelName, provider.id, provider.name, provider.baseUrl, key)) }
        }
        val grouped = list.groupBy { it.actualName }
        val numbered = mutableListOf<ModelOption>()
        grouped.forEach { (name, options) ->
            if (options.size == 1) numbered.add(options[0])
            else options.sortedBy { it.providerName }.forEachIndexed { index, opt -> numbered.add(opt.copy(displayName = "$name (${index + 1})")) }
        }
        numbered.sortedBy { it.displayName.lowercase() }
    }

    LaunchedEffect(openAiSettings) { if (openAiSettings) { showSettings = true; onAiSettingsConsumed() } }

    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true; status = if (isArabic) "جاري قراءة ملف JSON واستيراد المزودين..." else "Reading JSON file..."
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        ToolAnalyzer.parseProvidersJson(content).onSuccess { providers ->
                            providers.forEach { p ->
                                val id = p.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                                store.saveProvider(id, p.name, p.baseUrl, p.apiKey)
                                if (p.models.isNotEmpty()) store.saveModels(id, p.models)
                            }
                            withContext(Dispatchers.Main) { registeredProviders = store.listProviders(); status = if (isArabic) "تم استيراد ${providers.size} مزود بنجاح!" else "Imported successfully!" }
                        }.onFailure { ex -> withContext(Dispatchers.Main) { status = "خطأ: ${ex.message}" } }
                    }
                } catch (ex: Exception) { withContext(Dispatchers.Main) { status = "فشل: ${ex.message}" } } finally { withContext(Dispatchers.Main) { busy = false } }
            }
        }
    }

    val textFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    val fileName = uri.lastPathSegment ?: "file.txt"
                    if (content != null) withContext(Dispatchers.Main) { pendingTextFileContent = content; pendingTextFileName = fileName; status = if (isArabic) "تم إرفاق الملف: $fileName" else "File attached" }
                } catch (ex: Exception) { withContext(Dispatchers.Main) { status = "فشل قراءة الملف: ${ex.message}" } }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingImage = uri; if (uri != null) status = if (isArabic) "تم إرفاق الصورة." else "Image attached."
    }
    val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) draft = listOf(draft, spoken).filter { it.isNotBlank() }.joinToString(" ")
    }

    LaunchedEffect(threads) { saveChatThreads(context, threads); if (threads.none { it.id == selectedId }) selectedId = threads.first().id }
    LaunchedEffect(bridgeRefresh, selectedId) {
        try {
            val key = androidx.security.crypto.MasterKey.Builder(context).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            val resultPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(context, com.aiuniversalhub.runtime.TermuxResultReceiver.PREFS, key, androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            val chatPrefs = encryptedChatPreferences(context)
            val importKey = "termux_imported_ids_$selectedId"
            val importedArray = try { JSONArray(chatPrefs.getString(importKey, "[]")) } catch (_: Exception) { JSONArray() }
            val importedIds = mutableSetOf<String>(); for (index in 0 until importedArray.length()) importedIds.add(importedArray.optString(index))
            val history = try { JSONArray(resultPrefs.getString("results_history", "[]")) } catch (_: Exception) { JSONArray() }
            val newMessages = mutableListOf<ChatMessage>(); val newlyImported = mutableListOf<String>()
            for (index in 0 until history.length()) {
                val record = history.optJSONObject(index) ?: continue
                val id = record.optString("id")
                if (id.isBlank() || id in importedIds || record.optInt("conversation_id", -1) != selectedId) continue
                val output = record.optString("stdout"); val error = record.optString("stderr"); val errorMessage = record.optString("error_message")
                if (output.isBlank() && error.isBlank() && errorMessage.isBlank()) continue
                val body = buildString { append("Termux • "); append(record.optString("label", "result")); if (output.isNotBlank()) { append("\n\n"); append(output) }; if (error.isNotBlank()) { append("\n\n[stderr]\n"); append(error) }; if (errorMessage.isNotBlank()) { append("\n\n"); append(errorMessage) }; append("\n\nExit code: "); append(record.optInt("exit_code", -1)) }
                newMessages.add(ChatMessage("tool", body)); newlyImported.add(id)
            }
            if (newMessages.isNotEmpty()) {
                threads = threads.map { thread -> if (thread.id == selectedId) thread.copy(messages = thread.messages + newMessages) else thread }
                val updatedIds = (importedIds + newlyImported).toList().takeLast(100); val savedIds = JSONArray(); updatedIds.forEach { savedIds.put(it) }
                chatPrefs.edit().putString(importKey, savedIds.toString()).apply()
            }
        } catch (_: Exception) { }
    }

    val selected = threads.firstOrNull { it.id == selectedId } ?: threads.first()
    val currentDisplayName = allGlobalModels.find { it.actualName == selected.model }?.displayName ?: selected.model
    LaunchedEffect(currentDisplayName, allGlobalModels) { onTopModelOptionsChanged(currentDisplayName, allGlobalModels.map { it.displayName }) }

    LaunchedEffect(providerName, registeredProviders, apiKey) {
        val configured = (listOf(com.aiuniversalhub.data.SavedProvider(providerStorageId(providerName, registeredProviders), providerName, baseUrl)) + registeredProviders).distinctBy { it.id }
        val values = withContext(Dispatchers.IO) {
            configured.map { item ->
                val key = if (item.name == providerName) apiKey else store.getApiKey(item.id).orEmpty()
                try { client.getProviderBalance(item.name, key) } catch (_: Exception) { null } ?: (if (isArabic) "غير متاح" else "N/A")
            }
        }
        onTopTokenCountChanged(values.joinToString(" / "))
    }

    LaunchedEffect(topSelectedModel, selectedId, allGlobalModels) {
        if (topSelectedModel.isNotBlank()) {
            val option = allGlobalModels.find { it.displayName == topSelectedModel }
            if (option != null && option.actualName != selected.model) {
                threads = threads.map { t -> if (t.id == selectedId) t.copy(model = option.actualName) else t }
                store.saveSelectedModel(option.providerId, option.actualName)
                if (providerName != option.providerName) { providerName = option.providerName; baseUrl = option.baseUrl; apiKey = option.apiKey; store.saveSelectedProvider(option.providerId); modelOptions = store.getModels(option.providerId) }
            }
        }
    }

    fun updateSelectedModel(value: String) { threads = threads.map { t -> if (t.id == selectedId) t.copy(model = value) else t }; store.saveSelectedModel(providerStorageId(providerName, registeredProviders), value) }

    fun sendMessage() {
        var prompt = draft.trim(); val imageUri = pendingImage; val fileContent = pendingTextFileContent; val fileName = pendingTextFileName
        if (prompt.isBlank() && imageUri == null && fileContent == null) return
        if (selected.model.isBlank()) { status = if (isArabic) "اختاري نموذج المحادثة أولاً." else "Select a model first."; return }
        if (fileContent != null) prompt = "الملف المرفق ($fileName):\n$fileContent\n\n" + prompt
        var userText = prompt
        if (imageUri != null) { val prefix = if (prompt.isBlank()) "" else "\n"; val imgLabel = if (isArabic) "📎 صورة" else "📎 Image"; userText = prompt + prefix + imgLabel }
        val thread = threads.first { it.id == selectedId }
        var finalTitle = thread.title
        if (thread.messages.isEmpty()) {
            var baseTitle = if (isArabic) "تحليل ملف/صورة" else "File/Image question"
            if (prompt.isNotBlank()) baseTitle = prompt
            finalTitle = if (baseTitle.length > 36) baseTitle.substring(0, 36) else baseTitle
        }
        val updated = thread.copy(title = finalTitle, messages = thread.messages + ChatMessage("user", userText))
        threads = threads.map { t -> if (t.id == selectedId) updated else t }
        draft = ""; pendingImage = null; pendingTextFileContent = null; pendingTextFileName = null; busy = true; status = ""; val targetThreadId = selectedId
        scope.launch {
            try {
                val option = allGlobalModels.find { it.actualName == selected.model }
                val sendBaseUrl = option?.baseUrl ?: baseUrl; val sendApiKey = option?.apiKey ?: apiKey
                val startCommand = option?.providerId?.let { store.getProviderStartCommand(it) } ?: ""
                if (startCommand.isNotBlank()) {
                    status = if (isArabic) "جاري تشغيل الأداة المحلية..." else "Starting local tool..."
                    TermuxBridgeAdapter.sendCustomApprovedCommand(context, startCommand, "auto-start", selectedId); delay(3000); status = ""
                }
                val response = withContext(Dispatchers.IO) {
                    if (imageUri != null) {
                        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() } ?: error("Image error")
                        require(bytes.size <= 12 * 1024 * 1024) { "Image too large" }
                        val mime = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                        client.chatWithImageHistory(sendBaseUrl, sendApiKey, selected.model, updated.messages.dropLast(1).map { it.role to it.content }, prompt, mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
                    } else {
                        val history = updated.messages.map { it.role to it.content }
                        client.chatWithHistory(sendBaseUrl, sendApiKey, selected.model, history)
                    }
                }
                val usage = client.parseUsage(response)
                usageLabel = if (usage.totalTokens != null) (if (isArabic) "استهلاك: " else "Usage: ") + usage.totalTokens + (if (isArabic) " توكن" else " tokens") else if (isArabic) "الاستهلاك غير متاح" else "Usage unavailable"
                
                // استخراج الإجابة وتنظيفها من وسوم <thought>
                val rawAnswer = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() } ?: response
                val answer = rawAnswer.replace(Regex("(?s)<thought>.*?</thought>"), "").trim().ifBlank { rawAnswer }
                
                // إضافة الرد إلى المحادثة (هذا هو السطر الذي كان مفقوداً)
                threads = threads.map { t ->
                    if (t.id == targetThreadId) t.copy(messages = t.messages + ChatMessage("assistant", answer)) else t
                }
                status = ""
            } catch (ex: Exception) { status = ex.message ?: "Error" } finally { busy = false }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val messageListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selected.id, selected.messages.size, busy) { if (selected.messages.isNotEmpty()) messageListState.animateScrollToItem(selected.messages.size - 1) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxHeight().widthIn(min = 280.dp, max = 340.dp), drawerContainerColor = androidx.compose.ui.graphics.Color(0xFF171717)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isArabic) "المحادثات السابقة" else "Chat history", color = androidx.compose.ui.graphics.Color(0xFFD6B56D), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { val id = nextId++; threads = listOf(ChatThread(id, if (isArabic) "محادثة جديدة" else "New chat")) + threads; selectedId = id; pendingImage = null; pendingTextFileContent = null; status = ""; scope.launch { drawerState.close() } }) { Text("＋", color = androidx.compose.ui.graphics.Color(0xFFD6B56D)) }
                }
                HorizontalDivider(color = androidx.compose.ui.graphics.Color.DarkGray)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(threads, key = { it.id }) { thread ->
                        NavigationDrawerItem(label = { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(thread.title, maxLines = 1); Text(thread.messages.lastOrNull()?.content ?: if (isArabic) "محادثة جديدة" else "New conversation", maxLines = 1, style = MaterialTheme.typography.bodySmall) } }, selected = thread.id == selectedId, onClick = { selectedId = thread.id; pendingImage = null; pendingTextFileContent = null; scope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF3A3020), selectedTextColor = androidx.compose.ui.graphics.Color(0xFFFFE3A0), unselectedTextColor = androidx.compose.ui.graphics.Color.White))
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { val id = nextId++; threads = listOf(ChatThread(id, if (isArabic) "محادثة جديدة" else "New chat")) + threads; selectedId = id; pendingImage = null; pendingTextFileContent = null; status = "" }) { Text("＋", color = androidx.compose.ui.graphics.Color(0xFFD6B56D)) }
                IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰", color = androidx.compose.ui.graphics.Color(0xFFD6B56D), style = MaterialTheme.typography.headlineSmall) }
            }
            HorizontalDivider()
            LazyColumn(state = messageListState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selected.messages.isEmpty()) item { Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("⚙", style = MaterialTheme.typography.displayMedium, color = androidx.compose.ui.graphics.Color(0xFFD6B56D)); Text(if (isArabic) "ابدئي محادثة جديدة" else "Start a new conversation", style = MaterialTheme.typography.titleLarge); Text(if (isArabic) "اختاري نموذجًا واكتبي رسالتك بالأسفل" else "Choose a model and write below", style = MaterialTheme.typography.bodyMedium) } } }
                items(selected.messages) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start) {
                        Card(modifier = Modifier.fillMaxWidth(if (msg.role == "user") 0.88f else 0.98f), colors = CardDefaults.cardColors(containerColor = if (msg.role == "user") androidx.compose.ui.graphics.Color(0xFFF2E8D5) else androidx.compose.ui.graphics.Color(0xFFF7F7F7)), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (msg.role == "user") (if (isArabic) "أنتِ" else "You") else if (msg.role == "tool") "Termux" else "AI", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFF98722F))
                                    TextButton(onClick = { val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("Hub", msg.content)); Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text(if (isArabic) "نسخ" else "Copy", color = androidx.compose.ui.graphics.Color(0xFF98722F)) }
                                }
                                Text(msg.content, style = MaterialTheme.typography.bodyLarge, color = androidx.compose.ui.graphics.Color(0xFF211A1E))
                            }
                        }
                    }
                }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (usageLabel.isNotBlank()) Text(usageLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            if (pendingImage != null) Text(if (isArabic) "صورة جاهزة للإرفاق" else "Image ready", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            if (pendingTextFileName != null) Text(if (isArabic) "ملف جاهز: $pendingTextFileName" else "File ready: $pendingTextFileName", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = androidx.compose.ui.graphics.Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFD8D1C5)), shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    IconButton(onClick = { textFilePicker.launch("*/*") }, enabled = !busy) { Text("📄", style = MaterialTheme.typography.headlineMedium) }
                    IconButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy) { Text("＋", color = androidx.compose.ui.graphics.Color(0xFF98722F), style = MaterialTheme.typography.headlineMedium) }
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text(if (isArabic) "اكتبي رسالتك..." else "Message AI...") }, enabled = !busy, maxLines = 6, minLines = 1, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent))
                    IconButton(onClick = { try { speechInput.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isArabic) "ar-EG" else "en-US"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak") }) } catch (_: Exception) {} }, enabled = !busy) { Text("🎙") }
                    IconButton(onClick = { sendMessage() }, enabled = !busy && (draft.isNotBlank() || pendingImage != null || pendingTextFileContent != null)) { Text("➤", color = androidx.compose.ui.graphics.Color(0xFF98722F), style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }

    if (showLiveSearch) LiveSearchDialog(isArabic = isArabic, onDismiss = { showLiveSearch = false })
    if (showTaskManager) BackgroundTaskDialog(isArabic = isArabic, providerId = providerStorageId(providerName, registeredProviders), providerName = providerName, model = selected.model, onDismiss = { showTaskManager = false })
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(if (isArabic) "إعدادات مزود الذكاء الاصطناعي" else "AI provider settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var showProviderNetworks by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showProviderNetworks = !showProviderNetworks }) { Text(if (isArabic) "◎ شبكات ومزوّدو الخدمات" else "◎ Provider networks") }
                    if (showProviderNetworks) {
                        val networkProviders = listOf(Triple("OpenRouter", "https://openrouter.ai/api/v1", "https://openrouter.ai/models|https://openrouter.ai/settings/keys"), Triple("OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/docs/models|https://platform.openai.com/api-keys"), Triple("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "https://ai.google.dev/gemini-api/docs/models|https://aistudio.google.com/app/apikey"), Triple("Groq", "https://api.groq.com/openai/v1", "https://console.groq.com/docs/models|https://console.groq.com/keys"), Triple("DeepSeek", "https://api.deepseek.com/v1", "https://api-docs.deepseek.com/|https://platform.deepseek.com/api_keys"), Triple("Mistral AI", "https://api.mistral.ai/v1", "https://docs.mistral.ai/getting-started/models/|https://console.mistral.ai/api-keys/"), Triple("Together AI", "https://api.together.xyz/v1", "https://www.together.ai/models|https://api.together.ai/settings/api-keys"), Triple("AIMLAPI", "https://api.aimlapi.com/v1", "https://docs.aimlapi.com/|https://aimlapi.com/app/onboarding?returnTo=/keys?from=get-api-key"))
                        networkProviders.forEach { entry ->
                            val links = entry.third.split("|")
                            Card { Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.first, style = MaterialTheme.typography.titleSmall); Text((if (isArabic) "عنوان API: " else "API: ") + entry.second, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(links[0]))) }) { Text(if (isArabic) "الموديلات" else "Models") }
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(links[1]))) }) { Text(if (isArabic) "مفتاح API" else "API key") }
                                    TextButton(onClick = { providerName = entry.first; baseUrl = entry.second; apiKey = "" }) { Text(if (isArabic) "اختيار" else "Select") }
                                }
                            } }
                        }
                    }
                    var providerExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = !providerExpanded }) {
                        OutlinedTextField(providerName, {}, readOnly = true, label = { Text(if (isArabic) "المزود" else "Provider") }, modifier = Modifier.menuAnchor().fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) })
                        ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                            allProviderOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.first) }, onClick = {
                                    providerName = option.first; val id = providerStorageId(option.first, registeredProviders); baseUrl = store.getProviderUrl(id, option.second); apiKey = store.getApiKey(id) ?: ""; modelOptions = store.getModels(id); store.saveSelectedProvider(id); providerExpanded = false
                                    if (option.second.isNotBlank()) { busy = true; scope.launch { try { val raw = withContext(Dispatchers.IO) { client.listModels(option.second, store.getApiKey(id).orEmpty()) }; val discovered = client.parseModelIds(raw); store.saveModels(id, discovered); if (providerName == option.first) { modelOptions = discovered; if (discovered.isNotEmpty() && selected.model !in discovered) updateSelectedModel(discovered.first()) }; status = if (discovered.isEmpty()) (if (isArabic) "لا نماذج" else "No models") else (if (isArabic) "تم اكتشاف ${discovered.size} نموذج" else "Discovered ${discovered.size} models") } catch (ex: Exception) { status = ex.message ?: "Error" } finally { busy = false } } }
                                })
                            }
                        }
                    }
                    if (registeredProviders.isNotEmpty()) {
                        Text(if (isArabic) "المزودات المسجلة" else "Registered providers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        registeredProviders.forEach { saved ->
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(saved.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)); TextButton(onClick = { store.deleteProvider(saved.id); registeredProviders = store.listProviders(); if (providerName == saved.name) { val next = registeredProviders.firstOrNull(); if (next != null) { providerName = next.name; baseUrl = next.baseUrl; apiKey = store.getApiKey(next.id) ?: ""; store.saveSelectedProvider(next.id) } else { providerName = providerOptions.first().first; baseUrl = providerOptions.first().second; apiKey = ""; store.saveSelectedProvider("openai-compatible") }; modelOptions = emptyList() }; status = "Deleted" }) { Text(if (isArabic) "حذف" else "Delete", color = MaterialTheme.colorScheme.error) } }
                        }
                    }
                    OutlinedButton(onClick = { showAddProvider = true }) { Text(if (isArabic) "＋ إضافة مزود" else "＋ Add provider") }
                    OutlinedButton(onClick = { jsonPicker.launch("application/json") }) { Text(if (isArabic) "＋ استيراد من JSON" else "＋ Import from JSON") }
                    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    OutlinedButton(
                        onClick = {
                            busy = true; status = if (isArabic) "جاري الاتصال..." else "Connecting..."
                            scope.launch {
                                try {
                                    val keyId = providerStorageId(providerName, registeredProviders); store.saveProviderUrl(keyId, baseUrl.trim()); if (apiKey.isNotBlank()) store.saveApiKey(keyId, apiKey.trim())
                                    val raw = withContext(Dispatchers.IO) { client.listModels(baseUrl.trim(), apiKey.trim()) }; val discovered = client.parseModelIds(raw); store.saveModels(keyId, discovered)
                                    registeredProviders = store.listProviders(); modelOptions = discovered
                                    if (discovered.isNotEmpty() && selected.model !in discovered) updateSelectedModel(discovered.first())
                                    status = if (discovered.isEmpty()) (if (isArabic) "تم الاتصال لكن لا نماذج." else "Connected, no models.") else (if (isArabic) "تم جلب ${discovered.size} نموذج!" else "Fetched ${discovered.size} models!")
                                } catch (ex: Exception) { status = ex.message ?: "Failed" } finally { busy = false }
                            }
                        }, enabled = !busy && baseUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isArabic) "🔄 حفظ وتحديث النماذج" else "🔄 Save & Fetch Models") }
                }
            },
            confirmButton = { TextButton(onClick = { val keyId = providerStorageId(providerName, registeredProviders); store.saveProviderUrl(keyId, baseUrl.trim()); if (apiKey.isNotBlank()) store.saveApiKey(keyId, apiKey.trim()) else store.deleteApiKey(keyId); store.saveSelectedProvider(keyId); showSettings = false; status = if (isArabic) "تم الحفظ." else "Saved." }) { Text(if (isArabic) "حفظ وإغلاق" else "Save & Close") } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text(if (isArabic) "إغلاق" else "Close") } }
        )
    }

    if (showAddProvider) {
        AlertDialog(
            onDismissRequest = { showAddProvider = false },
            title = { Text(if (isArabic) "إضافة مزود جديد" else "Add new provider") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(newProviderName, { newProviderName = it }, label = { Text("Name") }, singleLine = true); OutlinedTextField(newProviderUrl, { newProviderUrl = it }, label = { Text("Base URL") }, singleLine = true); OutlinedTextField(newProviderKey, { newProviderKey = it }, label = { Text("API key") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) } },
            confirmButton = {
                TextButton(onClick = {
                    val cleanName = newProviderName.trim(); val cleanUrl = newProviderUrl.trim(); val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    if (cleanName.isBlank() || cleanUrl.isBlank() || id.isBlank()) { status = "Invalid input." }
                    else if (registeredProviders.any { it.id == id }) { status = "Name exists." }
                    else {
                        store.saveProvider(id, cleanName, cleanUrl, newProviderKey); registeredProviders = store.listProviders(); providerName = cleanName; baseUrl = cleanUrl; apiKey = newProviderKey; store.saveSelectedProvider(id); modelOptions = emptyList(); newProviderName = ""; newProviderUrl = ""; newProviderKey = ""; showAddProvider = false; busy = true; status = "Testing connection..."
                        scope.launch {
                            try {
                                val raw = withContext(Dispatchers.IO) { client.listModels(cleanUrl, apiKey) }; val discovered = client.parseModelIds(raw); store.saveModels(id, discovered)
                                if (providerStorageId(providerName, registeredProviders) == id) { modelOptions = discovered; if (discovered.isNotEmpty()) updateSelectedModel(discovered.first()) }
                                status = if (discovered.isEmpty()) "No models." else "Discovered ${discovered.size} models."
                            } catch (ex: Exception) { status = ex.message ?: "Failed" } finally { busy = false }
                        }
                    }
                }) { Text(if (isArabic) "إضافة وحفظ" else "Add and save") }
            },
            dismissButton = { TextButton(onClick = { showAddProvider = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }
}

@Composable
private fun LiveSearchDialog(isArabic: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current; val secure = remember { SecureStore(context) }; val searchClient = remember { LiveSearchClient() }; val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }; var key by remember { mutableStateOf(secure.getApiKey("live-search-brave").orEmpty()) }; var results by remember { mutableStateOf(emptyList<com.aiuniversalhub.net.SearchHit>()) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (isArabic) "البحث الحي" else "Live Search") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search") })
                OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Brave API key") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (key.isBlank()) secure.deleteApiKey("live-search-brave") else secure.saveApiKey("live-search-brave", key.trim()); error = "Saved" }) { Text("Save key") }
                    Button(enabled = !loading && query.isNotBlank() && key.isNotBlank(), onClick = {
                        secure.saveApiKey("live-search-brave", key.trim()); loading = true; error = ""
                        scope.launch { try { results = withContext(Dispatchers.IO) { searchClient.search(query, key) }; if (results.isEmpty()) error = "No results" } catch (ex: Exception) { error = ex.message ?: "Failed" }; loading = false }
                    }) { Text(if (loading) "Searching..." else "Search") }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                results.forEach { hit -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.url))) }) { Text(hit.title, maxLines = 2) }; Text(hit.description, style = MaterialTheme.typography.bodySmall, maxLines = 3); Text(hit.url, style = MaterialTheme.typography.labelSmall) }; HorizontalDivider() }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun BackgroundTaskDialog(isArabic: Boolean, providerId: String, providerName: String, model: String, onDismiss: () -> Unit) {
    val context = LocalContext.current; var title by remember { mutableStateOf("") }; var prompt by remember { mutableStateOf("") }; var tasks by remember { mutableStateOf(TaskRecordStore(context).all()) }; var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (isArabic) "المهام الخلفية" else "Background tasks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isArabic) "المزوّد: $providerName • $model" else "Provider: $providerName • $model", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Task title") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Instructions") }, minLines = 3)
                Button(enabled = title.isNotBlank() && prompt.isNotBlank() && model.isNotBlank(), onClick = {
                    try { TaskScheduler.enqueue(context, title, prompt, providerId, model); tasks = TaskRecordStore(context).all(); title = ""; prompt = ""; error = "Queued" } catch (ex: Exception) { error = ex.message ?: "Failed" }
                }) { Text(if (isArabic) "جدولة" else "Queue") }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider(); Text(if (isArabic) "السجل" else "History", style = MaterialTheme.typography.titleSmall)
                tasks.take(8).forEach { task -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(task.title, style = MaterialTheme.typography.labelLarge); Text("${task.state} • ${task.progress}%", style = MaterialTheme.typography.labelSmall); Text(task.detail, style = MaterialTheme.typography.bodySmall, maxLines = 4) }; HorizontalDivider() }
            }
        }, confirmButton = { TextButton(onClick = { tasks = TaskRecordStore(context).all() }) { Text("Refresh") }; TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
// END OF FILE