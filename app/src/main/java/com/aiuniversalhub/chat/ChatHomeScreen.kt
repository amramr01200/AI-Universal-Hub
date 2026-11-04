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
       
