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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiuniversalhub.data.SecureStore
import com.aiuniversalhub.net.AiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

private data class ChatMessage(val role: String, val content: String)
private data class ChatThread(
    val id: Int,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val model: String = ""
)

private fun loadChatThreads(context: android.content.Context): List<ChatThread> {
    return try {
        val raw = context.getSharedPreferences("hub_chats", android.content.Context.MODE_PRIVATE)
            .getString("threads", null) ?: return emptyList()
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
    context.getSharedPreferences("hub_chats", android.content.Context.MODE_PRIVATE)
        .edit().putString("threads", array.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHomeScreen(isArabic: Boolean) {
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
    var nextId by remember { mutableIntStateOf((threads.maxOfOrNull { it.id } ?: 0) + 1) }
    var draft by remember { mutableStateOf("") }
    val providerOptions = listOf(
        Triple("OpenAI-compatible", "https://api.openai.com/v1", true),
        Triple("OpenRouter", "https://openrouter.ai/api/v1", true),
        Triple("FreeModel", "https://freemodel.in/v1", true),
        Triple("LiteLLM", "http://localhost:4000/v1", true),
        Triple("Ollama", "http://localhost:11434/v1", false),
        Triple("FreeLLMAPI", "", true)
    )
    val initialProvider = providerOptions.firstOrNull {
        it.first.lowercase().replace(" ", "-") == store.getSelectedProvider()
    } ?: providerOptions.first()
    var providerName by remember { mutableStateOf(initialProvider.first) }
    var baseUrl by remember { mutableStateOf(store.getProviderUrl(initialProvider.first.lowercase().replace(" ", "-"), initialProvider.second)) }
    var apiKey by remember { mutableStateOf(store.getApiKey(initialProvider.first.lowercase().replace(" ", "-")) ?: "") }
    var modelOptions by remember { mutableStateOf(emptyList<String>()) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var usageLabel by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }

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
    val selected = threads.firstOrNull { it.id == selectedId } ?: threads.first()
    val selectedModel = selected.model

    fun updateSelectedModel(value: String) {
        threads = threads.map { if (it.id == selectedId) it.copy(model = value) else it }
        store.saveSelectedModel(providerName.lowercase().replace(" ", "-"), value)
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
                        client.chatWithImage(baseUrl, apiKey, selectedModel, prompt, mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Text("☰", color = androidx.compose.ui.graphics.Color(0xFF9B762F), style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text("AI Universal Hub", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color(0xFF9B762F))
                    Text(selected.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                TextButton(onClick = { showSettings = true }) { Text("⚙", color = androidx.compose.ui.graphics.Color(0xFF9B762F), style = MaterialTheme.typography.titleLarge) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$providerName • ${selectedModel.ifBlank { if (isArabic) "لم يتم اختيار نموذج" else "No model selected" }}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    val estimatedTokens = selected.messages.sumOf { it.content.length }.let { (it + 3) / 4 }
                    Text((if (isArabic) "السياق تقديريًا: " else "Estimated context: ") + "$estimatedTokens " + (if (isArabic) "توكن" else "tokens"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                TextButton(onClick = {
                    if (baseUrl.isBlank()) status = if (isArabic) "أدخلي رابط المزود في إعدادات AI." else "Enter provider URL in AI settings."
                    else {
                        busy = true
                        scope.launch {
                            try {
                                val raw = withContext(Dispatchers.IO) { client.listModels(baseUrl, apiKey) }
                                val found = client.parseModelIds(raw)
                                modelOptions = found
                                if (found.isNotEmpty() && selectedModel !in found) updateSelectedModel(found.first())
                                status = if (found.isEmpty()) (if (isArabic) "المزود لم يرجع نماذج." else "No models returned.") else (if (isArabic) "تم تحميل ${found.size} نموذج" else "Loaded ${found.size} models")
                            } catch (e: Exception) { status = e.message ?: "Connection failed" }
                            busy = false
                        }
                    }
                }, enabled = !busy) { Text(if (isArabic) "النماذج" else "Models") }
            }
            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = { modelMenuExpanded = !modelMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedModel.ifBlank { if (isArabic) "اختاري النموذج" else "Choose a model" },
                    onValueChange = {}, readOnly = true, enabled = modelOptions.isNotEmpty(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
                )
                ExposedDropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                    modelOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { updateSelectedModel(option); modelMenuExpanded = false })
                    }
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
                                    Text(if (msg.role == "user") (if (isArabic) "أنتِ" else "You") else "AI",
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

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(if (isArabic) "إعدادات مزود الذكاء الاصطناعي" else "AI provider settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var providerExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = !providerExpanded }) {
                        OutlinedTextField(providerName, {}, readOnly = true,
                            label = { Text(if (isArabic) "المزود" else "Provider") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) })
                        ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                            providerOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.first) }, onClick = {
                                    providerName = option.first
                                    val id = option.first.lowercase().replace(" ", "-")
                                    baseUrl = store.getProviderUrl(id, option.second)
                                    apiKey = store.getApiKey(id) ?: ""
                                    modelOptions = emptyList()
                                    providerExpanded = false
                                })
                            }
                        }
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
                    val keyId = providerName.lowercase().replace(" ", "-")
                    store.saveProviderUrl(keyId, baseUrl.trim())
                    if (apiKey.isNotBlank()) store.saveApiKey(keyId, apiKey.trim()) else store.deleteApiKey(keyId)
                    store.saveSelectedProvider(keyId)
                    showSettings = false
                    status = if (isArabic) "تم حفظ رابط المزود والمفتاح على الجهاز. اضغطي تحديث النماذج لاختبار الاتصال." else "Provider settings saved. Refresh models to test the connection."
                }) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }
}
