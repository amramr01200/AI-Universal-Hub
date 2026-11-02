package com.aiuniversalhub.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
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
private data class ChatThread(val id: Int, val title: String, val messages: List<ChatMessage> = emptyList())

private fun loadChatThreads(context: android.content.Context): List<ChatThread> {
    return try {
        val raw = context.getSharedPreferences("hub_chats", android.content.Context.MODE_PRIVATE).getString("threads", null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val messagesJson = obj.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesJson.length()).map { i ->
                val m = messagesJson.getJSONObject(i)
                ChatMessage(m.optString("role"), m.optString("content"))
            }
            ChatThread(obj.optInt("id"), obj.optString("title"), messages)
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveChatThreads(context: android.content.Context, threads: List<ChatThread>) {
    val array = JSONArray()
    threads.forEach { thread ->
        val messages = JSONArray()
        thread.messages.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        array.put(JSONObject().put("id", thread.id).put("title", thread.title).put("messages", messages))
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
        mutableStateOf(loadChatThreads(context).ifEmpty { listOf(ChatThread(1, if (isArabic) "محادثة جديدة" else "New chat")) })
    }
    var selectedId by remember { mutableIntStateOf(1) }
    var nextId by remember { mutableIntStateOf(2) }
    var draft by remember { mutableStateOf("") }
    val providerOptions = listOf(
        "OpenAI-compatible" to "https://api.openai.com/v1",
        "OpenRouter" to "https://openrouter.ai/api/v1",
        "FreeModel" to "https://freemodel.in/v1",
        "LiteLLM" to "http://localhost:4000/v1",
        "Ollama" to "http://localhost:11434/v1",
        "FreeLLMAPI" to ""
    )
    var providerName by remember { mutableStateOf("OpenAI-compatible") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf(store.getApiKey("openai-compatible") ?: "") }
    var model by remember { mutableStateOf("gpt-4o-mini") }
    var modelOptions by remember { mutableStateOf(listOf("gpt-4o-mini")) }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(threads) { saveChatThreads(context, threads) }
    LaunchedEffect(threads) { if (threads.none { it.id == selectedId }) selectedId = threads.first().id }
    val selected = threads.firstOrNull { it.id == selectedId } ?: threads.first()
    val messages = selected.messages

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(if (isArabic) "محادثاتك" else "Your chats", style = MaterialTheme.typography.headlineSmall)
                Text("$providerName • $model", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { showSettings = true }) { Text(if (isArabic) "إعدادات AI" else "AI settings") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val id = nextId++
                threads = listOf(ChatThread(id, if (isArabic) "محادثة جديدة" else "New chat")) + threads
                selectedId = id
                status = ""
            }) { Text(if (isArabic) "＋ شات جديد" else "＋ New chat") }
            TextButton(onClick = {
                if (apiKey.isBlank()) status = if (isArabic) "أضيفي مفتاح API من إعدادات AI أولاً." else "Add an API key in AI settings first."
                else {
                    busy = true
                    scope.launch {
                        try {
                            val raw = withContext(Dispatchers.IO) { client.listModels(baseUrl, apiKey) }
                            val arr = JSONObject(raw).optJSONArray("data")
                            val found = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it)?.optString("id") }.filter { it.isNotBlank() }
                            if (found.isNotEmpty()) { modelOptions = found; if (model !in found) model = found.first() }
                            status = if (found.isEmpty()) (if (isArabic) "لم يرجع المزود قائمة نماذج." else "Provider returned no models.") else (if (isArabic) "تم تحديث النماذج." else "Models refreshed.")
                        } catch (e: Exception) { status = e.message ?: "Connection failed" }
                        busy = false
                    }
                }
            }, enabled = !busy) { Text(if (isArabic) "تحديث النماذج" else "Refresh models") }
        }
        Text(if (isArabic) "المحادثات السابقة" else "Recent conversations", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().height(100.dp)) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(threads, key = { it.id }) { thread ->
                    ListItem(
                        headlineContent = { Text(thread.title, maxLines = 1) },
                        supportingContent = { Text(thread.messages.lastOrNull()?.content ?: if (isArabic) "لا توجد رسائل بعد" else "No messages yet", maxLines = 1) },
                        modifier = Modifier.clickable { selectedId = thread.id },
                        trailingContent = { if (thread.id == selectedId) Text("●", color = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }
        HorizontalDivider()
        Text(selected.title, style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (isArabic) "اكتبي رسالتك..." else "Message AI...") },
                enabled = !busy, maxLines = 4
            )
            Button(enabled = draft.isNotBlank() && !busy, onClick = {
                val prompt = draft.trim()
                draft = ""
                val current = threads.first { it.id == selectedId }
                val updated = current.copy(
                    title = if (current.messages.isEmpty()) prompt.take(36) else current.title,
                    messages = current.messages + ChatMessage("user", prompt)
                )
                threads = threads.map { if (it.id == selectedId) updated else it }
                if (apiKey.isBlank()) {
                    status = if (isArabic) "الشات جاهز، لكن يلزم إضافة مفتاح API للاتصال بالنموذج." else "Chat UI is ready; add an API key to connect to the model."
                } else {
                    busy = true
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) { client.chat(baseUrl, apiKey, model, prompt) }
                            val answer = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                                ?: response
                            threads = threads.map { t -> if (t.id == selectedId) t.copy(messages = t.messages + ChatMessage("assistant", answer)) else t }
                            status = ""
                        } catch (e: Exception) {
                            status = e.message ?: "Request failed"
                        }
                        busy = false
                    }
                }
            }) { Text(if (isArabic) "إرسال" else "Send") }
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
                        OutlinedTextField(providerName, { }, readOnly = true, label = { Text(if (isArabic) "المزود" else "Provider") }, modifier = Modifier.menuAnchor().fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) })
                        ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                            providerOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.first) }, onClick = {
                                    providerName = option.first
                                    if (option.second.isNotBlank()) baseUrl = option.second
                                    apiKey = store.getApiKey(option.first.lowercase().replace(" ", "-")) ?: ""
                                    providerExpanded = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, singleLine = true)
                    Text(if (isArabic) "عنوان API" else "API endpoint")
                    Text(if (isArabic) "يمكن تعديل العنوان ليتوافق مع المزود أو الخادم المحلي." else "You can customize this URL for your provider or local host.", style = MaterialTheme.typography.bodySmall)
                    Text(if (isArabic) "النموذج" else "Model")
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(model, { model = it }, readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            modelOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { model = option; expanded = false }) }
                        }
                    }
                    Text(if (isArabic) "يُحفظ المفتاح مشفرًا على الجهاز. المحادثات حاليًا مؤقتة داخل الجلسة." else "The key is encrypted on-device. Chats are currently session-only.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val keyId = providerName.lowercase().replace(" ", "-")
                    if (apiKey.isNotBlank()) store.saveApiKey(keyId, apiKey.trim()) else store.deleteApiKey(keyId)
                    showSettings = false
                    status = if (isArabic) "تم حفظ إعدادات المزود على الجهاز." else "Provider settings saved on this device."
                }) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }
}
