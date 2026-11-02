package com.aiuniversalhub.chat

import android.content.Intent
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
    var providerName by remember { mutableStateOf("OpenAI-compatible") }
    var baseUrl by remember { mutableStateOf(store.getProviderUrl("openai-compatible", "https://api.openai.com/v1")) }
    var apiKey by remember { mutableStateOf(store.getApiKey("openai-compatible") ?: "") }
    var modelOptions by remember { mutableStateOf(emptyList<String>()) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
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
                        client.chat(baseUrl, apiKey, selectedModel, prompt)
                    }
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

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isArabic) "محادثاتك" else "Your chats", style = MaterialTheme.typography.headlineSmall)
                Text("$providerName • ${selectedModel.ifBlank { if (isArabic) "لم يتم اختيار نموذج" else "No model selected" }}",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { showSettings = true }) { Text(if (isArabic) "إعدادات AI" else "AI settings") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val id = nextId++
                threads = listOf(ChatThread(id, if (isArabic) "محادثة جديدة" else "New chat")) + threads
                selectedId = id
                pendingImage = null
                status = ""
            }) { Text(if (isArabic) "＋ شات جديد" else "＋ New chat") }
            OutlinedButton(onClick = {
                if (baseUrl.isBlank()) status = if (isArabic) "أدخلي رابط المزود في إعدادات AI." else "Enter the provider URL in AI settings."
                else {
                    busy = true
                    scope.launch {
                        try {
                            val raw = withContext(Dispatchers.IO) { client.listModels(baseUrl, apiKey) }
                            val found = client.parseModelIds(raw)
                            modelOptions = found
                            if (found.isNotEmpty() && selectedModel !in found) updateSelectedModel(found.first())
                            status = if (found.isEmpty()) (if (isArabic) "المزود لم يرجع نماذج متاحة." else "No models were returned by the provider.")
                                else (if (isArabic) "تم تحميل ${found.size} نموذج." else "Loaded ${found.size} models.")
                        } catch (e: Exception) { status = e.message ?: "Connection failed" }
                        busy = false
                    }
                }
            }, enabled = !busy) { Text(if (isArabic) "تحديث النماذج" else "Refresh models") }
        }
        ExposedDropdownMenuBox(
            expanded = modelMenuExpanded,
            onExpandedChange = { modelMenuExpanded = !modelMenuExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedModel.ifBlank { if (isArabic) "اختاري النموذج لهذه المحادثة" else "Select a model for this chat" },
                onValueChange = {}, readOnly = true, enabled = modelOptions.isNotEmpty(),
                label = { Text(if (isArabic) "النموذج المستخدم في المحادثة" else "Model used in this conversation") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
            )
            ExposedDropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                modelOptions.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        updateSelectedModel(option)
                        modelMenuExpanded = false
                    })
                }
            }
        }
        Text(if (isArabic) "المحادثات السابقة" else "Recent conversations", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().height(88.dp)) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(threads, key = { it.id }) { thread ->
                    ListItem(
                        headlineContent = { Text(thread.title, maxLines = 1) },
                        supportingContent = { Text(thread.messages.lastOrNull()?.content ?: if (isArabic) "لا توجد رسائل بعد" else "No messages yet", maxLines = 1) },
                        modifier = Modifier.clickable { selectedId = thread.id; pendingImage = null },
                        trailingContent = { if (thread.id == selectedId) Text("●", color = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }
        HorizontalDivider()
        Text(selected.title, style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages = selected.messages) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (msg.role == "user") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (msg.role == "user") (if (isArabic) "أنتِ" else "You") else "AI", style = MaterialTheme.typography.labelMedium)
                        Text(msg.content)
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (pendingImage != null) {
            Text(if (isArabic) "تم اختيار صورة للإرفاق" else "Image selected for attachment", color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (isArabic) "اكتبي رسالتك..." else "Message AI...") },
                enabled = !busy, maxLines = 4
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy) { Text("＋") }
                OutlinedButton(onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isArabic) "ar-EG" else "en-US")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, if (isArabic) "تحدثي الآن" else "Speak now")
                        }
                        speechInput.launch(intent)
                    } catch (_: Exception) {
                        status = if (isArabic) "الإملاء الصوتي غير متاح على هذا الجهاز." else "Speech input is unavailable on this device."
                    }
                }, enabled = !busy) { Text("🎙") }
            }
            Button(enabled = !busy && (draft.isNotBlank() || pendingImage != null), onClick = { sendMessage() }) {
                Text(if (isArabic) "إرسال" else "Send")
            }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
