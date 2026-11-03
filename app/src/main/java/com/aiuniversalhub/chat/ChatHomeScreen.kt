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
import com.aiuniversalhub.net.AiClient
import com.aiuniversalhub.net.LiveSearchClient
import com.aiuniversalhub.tasks.TaskScheduler
import com.aiuniversalhub.tasks.TaskRecordStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

private data class ChatMessage(val role: String, val content: String)

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

    LaunchedEffect(openAiSettings) {
        if (openAiSettings) {
            showSettings = true
            onAiSettingsConsumed()
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
                val updatedIds = (importedIds + newlyImported).takeLast(100)
                val savedIds = JSONArray()
                updatedIds.forEach { savedIds.put(it) }
                chatPrefs.edit().putString(importKey, savedIds.toString()).apply()
            }
        } catch (_: Exception) { }
    }
    val selected = threads.firstOrNull { it.id == selectedId } ?: threads.first()
    val selectedModel = selected.model

    LaunchedEffect(selectedModel, modelOptions) { onTopModelOptionsChanged(selectedModel, modelOptions) }
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
    LaunchedEffect(topSelectedModel, selectedId, modelOptions) {
        if (topSelectedModel.isNotBlank() && topSelectedModel != selected.model && topSelectedModel in modelOptions) {
            threads = threads.map { if (it.id == selectedId) it.copy(model = topSelectedModel) else it }
            store.saveSelectedModel(providerStorageId(providerName, registeredProviders), topSelectedModel)
        }
    }

    fun updateSelectedModel(value: String) {
        threads = threads.map { if (it.id == selectedId) it.copy(model = value) else it }
        store.saveSelectedModel(providerStorageId(providerName, registeredProviders), value)
    }

    fun sendMessage() {
        val prompt = draft.trim()
        val imageUri = pendingImage
        if (prompt.isBlank() && imageUri == null) return
        if (selectedModel.isBlank()) {
            status = if (isArabic) "اختاري نموذج المحادثة من القائمة أولًا، ثم أرسلي الرسالة." else "Select a conversation model before sending."
            return
        }
        val userText = prompt + if (imageUri != null) (if (prompt.isBlank()) "" else "\n") + (if (isArabic) "📎 صورة مرفقة" else "📎 Image attached") else ""
        val thread = threads.first { it.id == selectedId }
        val updated = thread.copy(
            title = if (thread.messages.isEmpty()) (prompt.ifBlank { if (isArabic) "تحليل صورة" else "Image question" }).take(36) else thread.title,
            messages = thread.messages + ChatMessage("user", userText)
        )
        threads = threads.map { if (it.id == selectedId) updated else it }
        draft = ""
        pendingImage = null
        busy = true
        status = ""
        val targetThreadId = selectedId
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    if (imageUri != null) {
                        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                            ?: error(if (isArabic) "تعذر قراءة الصورة." else "Could not read the image.")
                        require(bytes.size <= 12 * 1024 * 1024) {
                            if (isArabic) "حجم الصورة أكبر من 12 ميجابايت." else "Image is larger than 12 MB."
                        }
                        val mime = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                        client.chatWithImageHistory(baseUrl, apiKey, selectedModel, updated.messages.dropLast(1).map { it.role to it.content }, prompt, mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
                    } else {
                        val history = updated.messages.map { it.role to it.content }
                        client.chatWithHistory(baseUrl, apiKey, selectedModel, history)
                    }
                }
                val usage = client.parseUsage(response)
                usageLabel = if (usage.totalTokens != null) {
                    (if (isArabic) "استهلاك آخر طلب: " else "Last request: ") + usage.totalTokens + (if (isArabic) " توكن" else " tokens")
                } else {
                    if (isArabic) "استهلاك التوكن من المزود غير متاح" else "Provider token usage unavailable"
                }
                val answer = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() } ?: response
                threads = threads.map { t ->
                    if (t.id == targetThreadId) t.copy(messages = t.messages + ChatMessage("assistant", answer)) else t
                }
                status = ""
            } catch (e: Exception) {
                status = e.message ?: if (isArabic) "فشل الاتصال بالمزود." else "Provider request failed."
            } finally {
                busy = false
            }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val messageListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selected.id, selected.messages.size, busy) {
        if (selected.messages.isNotEmpty()) messageListState.animateScrollToItem(selected.messages.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().widthIn(min = 280.dp, max = 340.dp),
                drawerContainerColor = androidx.compose.ui.graphics.Color(0xFF171717)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isArabic) "المحادثات السابقة" else "Chat history", color = androidx.compose.ui.graphics.Color(0xFFD6B56D), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val id = nextId++
                        threads = listOf(ChatThread(id, if (isArabic) "محادثة جديدة" else "New chat")) + threads
                        selectedId = id
                        pendingImage = null
                        status = ""
                        scope.launch { drawerState.close() }
                    }) { Text("＋", color = androidx.compose.ui.graphics.Color(0xFFD6B56D)) }
                }
                HorizontalDivider(color = androidx.compose.ui.graphics.Color.DarkGray)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(threads, key = { it.id }) { thread ->
                        NavigationDrawerItem(
                            label = {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(thread.title, maxLines = 1)
                                    Text(thread.messages.lastOrNull()?.content ?: if (isArabic) "محادثة جديدة" else "New conversation", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            selected = thread.id == selectedId,
                            onClick = {
                                selectedId = thread.id
                                pendingImage = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF3A3020),
                                selectedTextColor = androidx.compose.ui.graphics.Color(0xFFFFE3A0),
                                unselectedTextColor = androidx.compose.ui.graphics.Color.White
                            )
                        )
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val id = nextId++
                    threads = listOf(ChatThread(id, if (isArabic) "محادثة جديدة" else "New chat")) + threads
                    selectedId = id
                    pendingImage = null
                    status = ""
                }) { Text("＋", color = androidx.compose.ui.graphics.Color(0xFFD6B56D)) }
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Text("☰", color = androidx.compose.ui.graphics.Color(0xFFD6B56D), style = MaterialTheme.typography.headlineSmall)
                }
            }
            HorizontalDivider()
            LazyColumn(
                state = messageListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selected.messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("⚙", style = MaterialTheme.typography.displayMedium, color = androidx.compose.ui.graphics.Color(0xFFD6B56D))
                                Text(if (isArabic) "ابدئي محادثة جديدة" else "Start a new conversation", style = MaterialTheme.typography.titleLarge)
                                Text(if (isArabic) "اختاري نموذجًا واكتبي رسالتك بالأسفل" else "Choose a model and write below", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                items(selected.messages) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start) {
                        Card(
                            modifier = Modifier.fillMaxWidth(if (msg.role == "user") 0.88f else 0.98f),
                            colors = CardDefaults.cardColors(containerColor = if (msg.role == "user") androidx.compose.ui.graphics.Color(0xFFF2E8D5) else androidx.compose.ui.graphics.Color(0xFFF7F7F7)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (msg.role == "user") (if (isArabic) "أنتِ" else "You") else if (msg.role == "tool") "Termux" else "AI",
                                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                        color = androidx.compose.ui.graphics.Color(0xFF98722F))
                                    TextButton(onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Universal Hub", msg.content))
                                        Toast.makeText(context, if (isArabic) "تم نسخ النص" else "Text copied", Toast.LENGTH_SHORT).show()
                                    }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                        Text(if (isArabic) "نسخ" else "Copy", color = androidx.compose.ui.graphics.Color(0xFF98722F))
                                    }
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
            if (pendingImage != null) Text(if (isArabic) "الصورة جاهزة للإرفاق" else "Image ready to attach", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = androidx.compose.ui.graphics.Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFD8D1C5)),
                shadowElevation = 2.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    IconButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy) {
                        Text("＋", color = androidx.compose.ui.graphics.Color(0xFF98722F), style = MaterialTheme.typography.headlineMedium)
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isArabic) "اكتبي رسالتك..." else "Message AI...") },
                        enabled = !busy,
                        maxLines = 6,
                        minLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    IconButton(onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isArabic) "ar-EG" else "en-US")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, if (isArabic) "تحدثي الآن" else "Speak now")
                            }
                            speechInput.launch(intent)
                        } catch (_: Exception) { status = if (isArabic) "الإملاء الصوتي غير متاح." else "Speech input unavailable." }
                    }, enabled = !busy) { Text("🎙") }
                    IconButton(onClick = { sendMessage() }, enabled = !busy && (draft.isNotBlank() || pendingImage != null)) {
                        Text("➤", color = androidx.compose.ui.graphics.Color(0xFF98722F), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }

    if (showLiveSearch) {
        LiveSearchDialog(isArabic = isArabic, onDismiss = { showLiveSearch = false })
    }
    if (showTaskManager) {
        BackgroundTaskDialog(
            isArabic = isArabic,
            providerId = providerStorageId(providerName, registeredProviders),
            providerName = providerName,
            model = selectedModel,
            onDismiss = { showTaskManager = false }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(if (isArabic) "إعدادات مزود الذكاء الاصطناعي" else "AI provider settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var showProviderNetworks by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showProviderNetworks = !showProviderNetworks }) {
                        Text(if (isArabic) "◎ شبكات ومزوّدو الخدمات الخارجية" else "◎ External provider networks")
                    }
                    if (showProviderNetworks) {
                        Text(if (isArabic)
                            "اختاري مزودًا لفتح كتالوج موديلاته أو صفحة إنشاء مفتاح API. بعد إنشاء المفتاح، اختاري المزود من القائمة وأضيفي عنوانه ومفتاحه."
                            else "Open a provider's model catalog or API-key page. After creating a key, select the provider and enter its endpoint and key.",
                            style = MaterialTheme.typography.bodySmall)
                        val networkProviders = listOf(
                            Triple("OpenRouter", "https://openrouter.ai/api/v1", "https://openrouter.ai/models|https://openrouter.ai/settings/keys"),
                            Triple("OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/docs/models|https://platform.openai.com/api-keys"),
                            Triple("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "https://ai.google.dev/gemini-api/docs/models|https://aistudio.google.com/app/apikey"),
                            Triple("Groq", "https://api.groq.com/openai/v1", "https://console.groq.com/docs/models|https://console.groq.com/keys"),
                            Triple("DeepSeek", "https://api.deepseek.com/v1", "https://api-docs.deepseek.com/|https://platform.deepseek.com/api_keys"),
                            Triple("Mistral AI", "https://api.mistral.ai/v1", "https://docs.mistral.ai/getting-started/models/|https://console.mistral.ai/api-keys/"),
                            Triple("Together AI", "https://api.together.xyz/v1", "https://www.together.ai/models|https://api.together.ai/settings/api-keys"),
                            Triple("AIMLAPI", "https://api.aimlapi.com/v1", "https://docs.aimlapi.com/|https://aimlapi.com/app/onboarding?returnTo=/keys?from=get-api-key")
                        )
                        networkProviders.forEach { entry ->
                            val links = entry.third.split("|")
                            Card {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(entry.first, style = MaterialTheme.typography.titleSmall)
                                    Text((if (isArabic) "عنوان API: " else "API: ") + entry.second,
                                        style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(links[0])))
                                        }) { Text(if (isArabic) "الموديلات" else "Models") }
                                        TextButton(onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(links[1])))
                                        }) { Text(if (isArabic) "مفتاح API" else "API key") }
                                        TextButton(onClick = {
                                            providerName = entry.first
                                            baseUrl = entry.second
                                            apiKey = ""
                                        }) { Text(if (isArabic) "اختيار" else "Select") }
                                    }
                                }
                            }
                        }
                        Text(if (isArabic)
                            "تنبيه: وجود موديل في الكتالوج لا يعني أنه مجاني. تحققي من الأسعار والحدود قبل الاستخدام. مفتاح API مختلف عن اشتراك ChatGPT."
                            else "A model appearing in a catalog does not mean it is free. Check pricing and limits. API keys are separate from ChatGPT subscriptions.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    var providerExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = !providerExpanded }) {
                        OutlinedTextField(providerName, {}, readOnly = true,
                            label = { Text(if (isArabic) "المزود" else "Provider") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) })
                        ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                            allProviderOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.first) }, onClick = {
                                    providerName = option.first
                                    val id = providerStorageId(option.first, registeredProviders)
                                    baseUrl = store.getProviderUrl(id, option.second)
                                    apiKey = store.getApiKey(id) ?: ""
                                    modelOptions = store.getModels(id)
                                    store.saveSelectedProvider(id)
                                    providerExpanded = false
                                    if (option.second.isNotBlank()) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                val raw = withContext(Dispatchers.IO) {
                                                    client.listModels(option.second, store.getApiKey(id).orEmpty())
                                                }
                                                val discovered = client.parseModelIds(raw)
                                                store.saveModels(id, discovered)
                                                if (providerName == option.first) {
                                                    modelOptions = discovered
                                                    if (discovered.isNotEmpty() && selectedModel !in discovered) {
                                                        updateSelectedModel(discovered.first())
                                                    }
                                                }
                                                status = if (discovered.isEmpty())
                                                    (if (isArabic) "لم يرجع المزود نماذج متاحة." else "Provider returned no models.")
                                                else (if (isArabic) "تم اكتشاف ${discovered.size} نموذجًا من ${option.first}." else "Discovered ${discovered.size} models from ${option.first}.")
                                            } catch (e: Exception) {
                                                status = e.message ?: if (isArabic) "تعذر تحديث كتالوج المزود." else "Could not refresh provider catalog."
                                            } finally { busy = false }
                                        }
                                    }
                                })
                            }
                        }
                    }
                    if (registeredProviders.isNotEmpty()) {
                        Text(if (isArabic) "المزودات المسجلة على الجهاز" else "Registered providers on this device",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        registeredProviders.forEach { saved ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(saved.name, modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                                TextButton(onClick = {
                                    val replacement = store.deleteProvider(saved.id)
                                    registeredProviders = store.listProviders()
                                    if (providerName == saved.name) {
                                        val next = registeredProviders.firstOrNull()
                                        if (next != null) {
                                            providerName = next.name
                                            baseUrl = next.baseUrl
                                            apiKey = store.getApiKey(next.id) ?: ""
                                            store.saveSelectedProvider(next.id)
                                        } else {
                                            providerName = providerOptions.first().first
                                            baseUrl = providerOptions.first().second
                                            apiKey = ""
                                            store.saveSelectedProvider("openai-compatible")
                                        }
                                        modelOptions = emptyList()
                                    }
                                    status = if (isArabic) "تم حذف المزود ورابطه ومفتاحه ونماذجه فقط." else "Provider, URL, key and its models deleted."
                                }) { Text(if (isArabic) "حذف" else "Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    OutlinedButton(onClick = { showAddProvider = true }) {
                        Text(if (isArabic) "＋ إضافة مزود باسم جديد" else "＋ Add provider with custom name")
                    }
                    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key (leave blank if not required)") },
                        singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Text(if (isArabic) "لا يلزم إدخال اسم النموذج هنا. حمّلي النماذج ثم اختاري نموذجًا لكل محادثة." else "No model name is required here. Refresh models and select one per conversation.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val keyId = providerStorageId(providerName, registeredProviders)
                    store.saveProviderUrl(keyId, baseUrl.trim())
                    if (apiKey.isNotBlank()) store.saveApiKey(keyId, apiKey.trim()) else store.deleteApiKey(keyId)
                    store.saveSelectedProvider(keyId)
                    showSettings = false
                    status = if (isArabic) "تم حفظ رابط المزود والمفتاح على الجهاز. اضغطي تحديث النماذج لاختبار الاتصال." else "Provider settings saved. Refresh models to test the connection."
                }) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
        if (showAddProvider) {
            AlertDialog(
                onDismissRequest = { showAddProvider = false },
                title = { Text(if (isArabic) "إضافة مزود جديد" else "Add a new provider") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(newProviderName, { newProviderName = it },
                            label = { Text(if (isArabic) "اسم المزود من اختيارك" else "Your provider name") }, singleLine = true)
                        OutlinedTextField(newProviderUrl, { newProviderUrl = it },
                            label = { Text("Base URL") }, singleLine = true)
                        OutlinedTextField(newProviderKey, { newProviderKey = it },
                            label = { Text("API key") }, singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val cleanName = newProviderName.trim()
                        val cleanUrl = newProviderUrl.trim()
                        val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                        if (cleanName.isBlank() || cleanUrl.isBlank() || id.isBlank()) {
                            status = if (isArabic) "اكتبي اسم المزود ورابطه بشكل صحيح." else "Enter a valid provider name and URL."
                        } else if (registeredProviders.any { it.id == id }) {
                            status = if (isArabic) "الاسم ده مسجل بالفعل؛ اختاري اسمًا مختلفًا." else "That provider name is already registered; choose another."
                        } else {
                            store.saveProvider(id, cleanName, cleanUrl, newProviderKey)
                            registeredProviders = store.listProviders()
                            providerName = cleanName
                            baseUrl = cleanUrl
                            apiKey = newProviderKey
                            store.saveSelectedProvider(id)
                            modelOptions = emptyList()
                            newProviderName = ""
                            newProviderUrl = ""
                            newProviderKey = ""
                            showAddProvider = false
                            status = if (isArabic) "اتضاف المزود بشكل مستقل. حدّثي النماذج لاكتشاف نماذجه." else "Provider added independently. Refresh models to discover its models."
                        }
                    }) { Text(if (isArabic) "إضافة وحفظ" else "Add and save") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddProvider = false }) { Text(if (isArabic) "إلغاء" else "Cancel") }
                }
            )
        }
    }
}

@Composable
private fun LiveSearchDialog(isArabic: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val secure = remember { SecureStore(context) }
    val searchClient = remember { LiveSearchClient() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(secure.getApiKey("live-search-brave").orEmpty()) }
    var results by remember { mutableStateOf(emptyList<com.aiuniversalhub.net.SearchHit>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "البحث الحي على الويب" else "Live web search") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "ابحثي في المواقع" else "Search the web") })
                OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Brave Search API key") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text(if (isArabic) "المفتاح يُحفظ مشفرًا على الجهاز. يلزم حساب Brave Search API؛ لا يوجد بحث مجاني مضمون." else "The key is encrypted on-device. A Brave Search API account is required; free availability is not guaranteed.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (key.isBlank()) secure.deleteApiKey("live-search-brave")
                        else secure.saveApiKey("live-search-brave", key.trim())
                        error = if (isArabic) "تم حفظ إعدادات البحث." else "Search settings saved."
                    }) { Text(if (isArabic) "حفظ المفتاح" else "Save key") }
                    Button(enabled = !loading && query.isNotBlank() && key.isNotBlank(), onClick = {
                        secure.saveApiKey("live-search-brave", key.trim())
                        loading = true
                        error = ""
                        scope.launch {
                            try {
                                results = withContext(Dispatchers.IO) { searchClient.search(query, key) }
                                if (results.isEmpty()) error = if (isArabic) "لم تظهر نتائج." else "No results found."
                            } catch (e: Exception) { error = e.message ?: "Search failed" }
                            loading = false
                        }
                    }) { Text(if (loading) (if (isArabic) "جاري البحث…" else "Searching…") else (if (isArabic) "بحث" else "Search")) }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                results.forEach { hit ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.url)))
                        }) { Text(hit.title, maxLines = 2) }
                        Text(hit.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        Text(hit.url, style = MaterialTheme.typography.labelSmall)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") } }
    )
}

@Composable
private fun BackgroundTaskDialog(
    isArabic: Boolean,
    providerId: String,
    providerName: String,
    model: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf(TaskRecordStore(context).all()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "المهام الخلفية" else "Background tasks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isArabic) "المزوّد الحالي: $providerName • النموذج: $model" else "Provider: $providerName • Model: $model",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "عنوان المهمة" else "Task title") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "تعليمات المهمة" else "Task instructions") }, minLines = 3)
                Button(enabled = title.isNotBlank() && prompt.isNotBlank() && model.isNotBlank(), onClick = {
                    try {
                        TaskScheduler.enqueue(context, title, prompt, providerId, model)
                        tasks = TaskRecordStore(context).all()
                        title = ""; prompt = ""
                        error = if (isArabic) "تمت جدولة المهمة؛ ستعمل عند توفر الإنترنت." else "Task queued; it will run when network is available."
                    } catch (e: Exception) { error = e.message ?: "Could not queue task." }
                }) { Text(if (isArabic) "جدولة المهمة" else "Queue task") }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider()
                Text(if (isArabic) "سجل المهام" else "Task history", style = MaterialTheme.typography.titleSmall)
                tasks.take(8).forEach { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(task.title, style = MaterialTheme.typography.labelLarge)
                        Text("${task.state} • ${task.progress}%", style = MaterialTheme.typography.labelSmall)
                        Text(task.detail, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { tasks = TaskRecordStore(context).all() }) { Text(if (isArabic) "تحديث السجل" else "Refresh") }
            TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") }
        }
    )
}
