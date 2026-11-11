package com.aiuniversalhub.integrations

import android.content.Context
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aiuniversalhub.data.SecureStore
import com.aiuniversalhub.runtime.TermuxBridgeAdapter
import com.aiuniversalhub.runtime.TermuxResultReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.util.UUID

private data class ClaudeBridgeResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errorCode: Int,
    val errorMessage: String
)

private data class ClaudeChatLine(val role: String, val text: String)

private fun b64(value: String): String =
    Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

private fun readClaudeBridgeResult(context: Context, label: String): ClaudeBridgeResult? {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    val prefs = EncryptedSharedPreferences.create(
        context,
        TermuxResultReceiver.PREFS,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    val history = try {
        JSONArray(prefs.getString("results_history", "[]"))
    } catch (_: Exception) {
        JSONArray()
    }
    for (index in history.length() - 1 downTo 0) {
        val item = history.optJSONObject(index) ?: continue
        if (item.optString("label") == label) {
            return ClaudeBridgeResult(
                stdout = item.optString("stdout"),
                stderr = item.optString("stderr"),
                exitCode = item.optInt("exit_code", -1),
                errorCode = item.optInt("error", 0),
                errorMessage = item.optString("error_message")
            )
        }
    }
    return null
}

private suspend fun awaitClaudeBridgeResult(
    context: Context,
    label: String,
    timeoutMs: Long
): ClaudeBridgeResult? = withTimeoutOrNull(timeoutMs) {
    while (true) {
        val result = withContext(Dispatchers.IO) {
            readClaudeBridgeResult(context, label)
        }
        if (result != null) return@withTimeoutOrNull result
        delay(750)
    }
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeCodeScreen(isArabic: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }
    val scope = rememberCoroutineScope()
    val sessionPrefs = remember {
        context.getSharedPreferences("claude_code_session", Context.MODE_PRIVATE)
    }

    var baseUrl by remember {
        mutableStateOf(store.getProviderUrl("claude-code", "http://127.0.0.1:3001"))
    }
    var apiKey by remember { mutableStateOf(store.getApiKey("claude-code").orEmpty()) }
    var workingDirectory by remember {
        mutableStateOf("/home/claudeuser/AI-Universal-Hub")
    }
    var prompt by remember { mutableStateOf("") }
    var allowShell by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var termuxInstalled by remember { mutableStateOf(TermuxBridgeAdapter.isInstalled(context)) }
    var runCommandGranted by remember { mutableStateOf(TermuxBridgeAdapter.canRunCommands(context)) }
    val chatLines = remember { mutableStateListOf<ClaudeChatLine>() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        runCommandGranted = granted
        status = if (granted) {
            if (isArabic) "تم منح إذن تشغيل الأوامر. اضغطي تجهيز Claude Code." else "Permission granted. Tap Set up Claude Code."
        } else {
            if (isArabic) "لم يتم منح إذن RUN_COMMAND. راجعي إعدادات Termux." else "RUN_COMMAND permission was not granted."
        }
    }

    fun setupClaude() {
        if (!termuxInstalled) {
            status = if (isArabic) "ثبّتي Termux أولًا." else "Install Termux first."
            return
        }
        if (!runCommandGranted) {
            permissionLauncher.launch("com.termux.permission.RUN_COMMAND")
            return
        }
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanKey = apiKey.trim()
        if (!(cleanBase.startsWith("http://") || cleanBase.startsWith("https://"))) {
            status = if (isArabic) "رابط FreeLLMAPI غير صحيح." else "Invalid FreeLLMAPI URL."
            return
        }
        if (cleanKey.isBlank()) {
            status = if (isArabic) "أدخلي المفتاح الموحد أولًا." else "Enter the unified API key first."
            return
        }

        store.saveProviderUrl("claude-code", cleanBase)
        store.saveApiKey("claude-code", cleanKey)
        busy = true
        status = if (isArabic) "جارٍ تجهيز Debian وتثبيت Claude Code..." else "Preparing Debian and installing Claude Code..."
        scope.launch {
            try {
                val keyEncoded = b64(cleanKey)
                val baseEncoded = b64(cleanBase)
                val rootScript = """
                    set -e
                    apt-get update -y
                    apt-get install -y ca-certificates curl git
                    id claudeuser >/dev/null 2>&1 || useradd --create-home --shell /bin/bash claudeuser
                    install -d -o claudeuser -g claudeuser -m 700 /home/claudeuser/.config/claude-code-free
                    install -d -o claudeuser -g claudeuser -m 700 /home/claudeuser/.local/bin
                """.trimIndent()
                val userScript = """
                    set -e
                    export PATH="${'HOME/.local/bin:/usr/local/bin:/usr/bin:/bin"}HOME/.local/bin:/usr/local/bin:/usr/bin:/bin"
                    install -d -m 700 "${'HOME/.config/claude-code-free"}HOME/.config/claude-code-free"
                    printf '%s' '$keyEncoded' | base64 -d > "${'HOME/.config/claude-code-free/token"}HOME/.config/claude-code-free/token"
                    printf '%s' '$baseEncoded' | base64 -d > "${'HOME/.config/claude-code-free/base-url"}HOME/.config/claude-code-free/base-url"
                    chmod 600 "${'HOME/.config/claude-code-free/token" "${'$' }HOME/.config/claude-code-free/base-url"}HOME/.config/claude-code-free/token" "${'$' }HOME/.config/claude-code-free/base-url"
                    if [ ! -x "${'HOME/.local/bin/claude" ]; then}HOME/.local/bin/claude" ]; then
                      curl -fsSL https://claude.ai/install.sh | bash
                    fi
                    if [ ! -d "${'HOME/AI-Universal-Hub/.git" ]; then}HOME/AI-Universal-Hub/.git" ]; then
                      git clone --single-branch --branch main https://github.com/amramr01200/AI-Universal-Hub.git "${'HOME/AI-Universal-Hub"}HOME/AI-Universal-Hub"
                    fi
                    base_url="$(cat "${'HOME/.config/claude-code-free/base-url")"}HOME/.config/claude-code-free/base-url")"
                    token="$(cat "${'HOME/.config/claude-code-free/token")"}HOME/.config/claude-code-free/token")"
                    code="$(curl -sS -o /dev/null -w '%{http_code}' "${'base_url/v1/models" -H "Authorization: Bearer ${'$' }token" || true)"}base_url/v1/models" -H "Authorization: Bearer ${'token" || true)"}token" || true)"
                    if [ "${'code" != "200" ]; then}code" != "200" ]; then
                      echo "FreeLLMAPI check failed: HTTP ${'code. Check that the server is running and the unified key is valid."}code. Check that the server is running and the unified key is valid."
                      exit 1
                    fi
                    "${'HOME/.local/bin/claude" --version}HOME/.local/bin/claude" --version
                """.trimIndent()
                val rootEncoded = b64(rootScript)
                val userEncoded = b64(userScript)
                val command =
                    "proot-distro list-installed | grep -qi debian || proot-distro install debian; " +
                    "proot-distro login debian -- bash -lc 'eval \"\\$(printf %s $rootEncoded | base64 -d)\"' && " +
                    "proot-distro login debian --user claudeuser -- bash -lc 'eval \"\$(printf %s $userEncoded | base64 -d)\"'"
                val label = "claude-setup:" + UUID.randomUUID()
                val submitted = withContext(Dispatchers.IO) {
                    TermuxBridgeAdapter.sendCustomApprovedCommand(context, command, label, 1)
                }
                if (!submitted.submitted) {
                    status = submitted.message
                    return@launch
                }
                val result = awaitClaudeBridgeResult(context, label, 15 * 60 * 1000L)
                if (result == null) {
                    status = if (isArabic) "انتهت مهلة التثبيت. افتحي Termux وتحققي من آخر خطأ." else "Setup timed out. Check the last error in Termux."
                } else if (result.exitCode == 0 && result.errorCode == 0) {
                    status = if (isArabic) "تم تجهيز Claude Code والاتصال بـ FreeLLMAPI بنجاح." else "Claude Code installed and FreeLLMAPI connection verified."
                } else {
                    status = (result.errorMessage + "\n" + result.stderr + "\n" + result.stdout).trim().takeLast(2500)
                        .ifBlank { "Setup failed (exit ${result.exitCode})." }
                }
            } catch (e: Exception) {
                status = e.message ?: "Claude Code setup failed."
            } finally {
                busy = false
            }
        }
    }

    fun sendPrompt() {
        val cleanPrompt = prompt.trim()
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanKey = apiKey.trim().ifBlank { store.getApiKey("claude-code").orEmpty() }
        if (cleanPrompt.isBlank() || busy) return
        if (!termuxInstalled) {
            status = if (isArabic) "Termux غير مثبت." else "Termux is not installed."
            return
        }
        if (!runCommandGranted) {
            permissionLauncher.launch("com.termux.permission.RUN_COMMAND")
            return
        }
        if (cleanKey.isBlank()) {
            status = if (isArabic) "احفظي المفتاح الموحد وجهّزي Claude Code أولًا." else "Save the unified key and set up Claude Code first."
            return
        }
        if (!workingDirectory.startsWith("/")) {
            status = if (isArabic) "مسار المشروع يجب أن يبدأ بـ / داخل Debian." else "The project path must be absolute inside Debian."
            return
        }

        store.saveProviderUrl("claude-code", cleanBase)
        store.saveApiKey("claude-code", cleanKey)
        val savedSession = sessionPrefs.getString("session_id", "").orEmpty()
        val sessionId = savedSession.ifBlank { UUID.randomUUID().toString() }
        val sessionFlag = if (savedSession.isBlank()) "--session-id" else "--resume"
        val promptEncoded = b64(cleanPrompt)
        val directoryEncoded = b64(workingDirectory.trim())
        val tools = if (allowShell) "Read,Glob,Grep,Edit,Write,Bash" else "Read,Glob,Grep,Edit,Write"
        val runScript = """
            set -e
            export PATH="${'HOME/.local/bin:/usr/local/bin:/usr/bin:/bin"}HOME/.local/bin:/usr/local/bin:/usr/bin:/bin"
            export ANTHROPIC_BASE_URL="$(cat "${'HOME/.config/claude-code-free/base-url")"}HOME/.config/claude-code-free/base-url")"
            export ANTHROPIC_AUTH_TOKEN="$(cat "${'HOME/.config/claude-code-free/token")"}HOME/.config/claude-code-free/token")"
            unset ANTHROPIC_API_KEY
            cd "$(printf '%s' '$directoryEncoded' | base64 -d)"
            PROMPT="$(printf '%s' '$promptEncoded' | base64 -d)"
            exec "${'HOME/.local/bin/claude" -p --output-format text --permission-mode acceptEdits --allowedTools "$tools" $sessionFlag "$sessionId" "${'$' }PROMPT"}HOME/.local/bin/claude" -p --output-format text --permission-mode acceptEdits --allowedTools "$tools" $sessionFlag "$sessionId" "${'PROMPT"}PROMPT"
        """.trimIndent()
        val encodedScript = b64(runScript)
        val command =
            "proot-distro login debian --user claudeuser -- bash -lc 'eval \"\\$(printf %s $encodedScript | base64 -d)\"'"
        val label = "claude-chat:" + UUID.randomUUID()
        chatLines.add(ClaudeChatLine("user", cleanPrompt))
        prompt = ""
        busy = true
        scope.launch {
            try {
                val submitted = withContext(Dispatchers.IO) {
                    TermuxBridgeAdapter.sendCustomApprovedCommand(context, command, label, 1)
                }
                if (!submitted.submitted) {
                    chatLines.add(ClaudeChatLine("assistant", submitted.message))
                    status = submitted.message
                    return@launch
                }
                status = if (isArabic) "Claude Code يعمل على طلبك..." else "Claude Code is working on your request..."
                val result = awaitClaudeBridgeResult(context, label, 10 * 60 * 1000L)
                if (result == null) {
                    chatLines.add(ClaudeChatLine("assistant", if (isArabic) "لم يصل رد من Termux في الوقت المحدد." else "No response arrived from Termux before timeout."))
                    status = if (isArabic) "انتهت مهلة الانتظار." else "Timed out waiting for Termux."
                } else {
                    val output = listOf(result.stdout, result.stderr, result.errorMessage)
                        .filter { it.isNotBlank() }.joinToString("\n").trim()
                    if (result.exitCode == 0 && result.errorCode == 0) {
                        sessionPrefs.edit().putString("session_id", sessionId).apply()
                        chatLines.add(ClaudeChatLine("assistant", output.ifBlank { if (isArabic) "اكتمل التنفيذ دون نص." else "Completed with no text output." }))
                        status = if (isArabic) "اكتمل طلب Claude Code." else "Claude Code request completed."
                    } else {
                        chatLines.add(ClaudeChatLine("assistant", output.ifBlank { "Claude Code failed (exit ${result.exitCode})." }))
                        status = if (isArabic) "تعذر تنفيذ الطلب. راجعي رسالة الخطأ." else "The request failed. Review the error message."
                    }
                }
            } catch (e: Exception) {
                chatLines.add(ClaudeChatLine("assistant", e.message ?: "Claude Code request failed."))
                status = e.message ?: "Claude Code request failed."
            } finally {
                busy = false
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF9C762E),
            secondary = Color(0xFFD6B56D),
            background = Color.White,
            surface = Color(0xFFFFFEFC),
            onBackground = Color(0xFF211A1E),
            onSurface = Color(0xFF211A1E),
            surfaceVariant = Color(0xFFF4F0E7),
            onSurfaceVariant = Color(0xFF463D2D)
        )
    ) {
        Column(
            modifier = modifier.fillMaxSize().background(Color.White).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5EE)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (isArabic) "اتصال Claude Code" else "Claude Code connection", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("FreeLLMAPI Base URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "المفتاح الموحد" else "Unified API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = workingDirectory,
                        onValueChange = { workingDirectory = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "مجلد المشروع داخل Debian" else "Project directory inside Debian") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy,
                            onClick = { setupClaude() },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (busy) (if (isArabic) "جارٍ التجهيز..." else "Setting up...") else (if (isArabic) "حفظ المفتاح وتجهيز Claude" else "Save key & set up Claude")) }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                termuxInstalled = TermuxBridgeAdapter.isInstalled(context)
                                runCommandGranted = TermuxBridgeAdapter.canRunCommands(context)
                                status = if (isArabic) "تم تحديث حالة Termux." else "Termux status refreshed."
                            }
                        ) { Text(if (isArabic) "فحص" else "Check") }
                    }
                    if (termuxInstalled && !runCommandGranted) {
                        OutlinedButton(
                            onClick = { permissionLauncher.launch("com.termux.permission.RUN_COMMAND") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isArabic) "منح إذن تشغيل الأوامر" else "Grant command permission") }
                    }
                    Text(
                        if (isArabic) "المفتاح يُحفظ مشفّرًا داخل التطبيق، ثم يُنقل إلى ملف محمي داخل Debian لاستخدام Claude Code. لا تشاركيه في المحادثة."
                        else "The key is encrypted in the app, then copied to a permission-protected Debian file for Claude Code. Never share it in chat.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = allowShell, onCheckedChange = { allowShell = it })
                Text(if (isArabic) "السماح لكلود بتشغيل أوامر الطرفية داخل مجلد المشروع" else "Allow Claude to run shell commands in the project folder")
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatLines) { line ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (line.role == "user") Color(0xFFF1EEE7) else Color(0xFFFAFAFA)
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(if (line.role == "user") (if (isArabic) "أنتِ" else "You") else "Claude Code", style = MaterialTheme.typography.labelMedium)
                            Text(line.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Text(status, color = if (status.contains("failed", true) || status.contains("فشل") || status.contains("تعذر")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(if (isArabic) "اكتبي مهمة Claude Code..." else "Ask Claude Code to work on the project...") },
                    minLines = 2,
                    maxLines = 5
                )
                Button(
                    enabled = !busy && prompt.isNotBlank(),
                    onClick = { sendPrompt() },
                    modifier = Modifier.heightIn(min = 56.dp)
                ) { Text(if (busy) "…" else if (isArabic) "إرسال" else "Send") }
            }
            TextButton(
                enabled = !busy,
                onClick = {
                    sessionPrefs.edit().remove("session_id").apply()
                    chatLines.clear()
                    status = if (isArabic) "بدأت جلسة Claude Code جديدة." else "Started a new Claude Code session."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isArabic) "جلسة جديدة ومسح المحادثة" else "New session & clear chat") }
        }
    }
}
