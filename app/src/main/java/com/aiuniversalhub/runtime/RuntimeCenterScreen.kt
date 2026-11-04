package com.aiuniversalhub.runtime

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.aiuniversalhub.security.ApprovalAuditStore
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aiuniversalhub.data.SecureStore
import com.aiuniversalhub.net.AiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RuntimeCenterScreen(isArabic: Boolean, activeConversationId: Int = -1) {
    val context = LocalContext.current
    val store = remember { TermuxAgentStore(context) }
    val secureStore = remember { SecureStore(context) }
    val aiClient = remember { AiClient() }
    val scope = rememberCoroutineScope()

    var profiles by remember { mutableStateOf(store.list()) }
    var profileName by remember { mutableStateOf("") }
    var conversationId by remember { mutableStateOf("") }
    var runtimeId by remember { mutableStateOf("termux") }
    var workDir by remember { mutableStateOf("/data/data/com.termux/files/home") }
    var termuxInstalled by remember { mutableStateOf(TermuxBridgeAdapter.isInstalled(context)) }
    var runCommandGranted by remember { mutableStateOf(TermuxBridgeAdapter.canRunCommands(context)) }
    var status by remember { mutableStateOf("") }
    
    var isScanning by remember { mutableStateOf(false) }
    var runningComponent by remember { mutableStateOf<String?>(null) }
    var pendingApproval by remember { mutableStateOf<RuntimeComponent?>(null) }
    val audit = remember { ApprovalAuditStore(context) }
    var bridgeOutput by remember { mutableStateOf("") }
    var bridgeError by remember { mutableStateOf("") }
    var bridgeMeta by remember { mutableStateOf("") }

    val runCommandPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        runCommandGranted = granted
        status = if (granted) (if (isArabic) "تم منح إذن تشغيل الأوامر." else "RUN_COMMAND permission granted.") else (if (isArabic) "لم يُمنح الإذن. افتحي إعدادات التطبيق وامنحي إذن RUN_COMMAND." else "Permission was not granted. Open app settings and grant RUN_COMMAND.")
    }

    fun refreshBridgeResult() {
        try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(context, TermuxResultReceiver.PREFS, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            bridgeOutput = prefs.getString("stdout", "").orEmpty()
            bridgeError = prefs.getString("stderr", "").orEmpty()
            bridgeMeta = "Chat #" + prefs.getInt("conversation_id", -1) + " • Exit: " + prefs.getInt("exit_code", -1) + " • " + prefs.getString("label", "")
            val internalError = prefs.getString("error_message", "").orEmpty()
            if (internalError.isNotBlank()) bridgeError = internalError + "\n" + bridgeError
        } catch (ex: Exception) { bridgeMeta = ex.message ?: "Could not read result" }
    }

    fun runDiagnostic(id: String) {
        val result = TermuxBridgeAdapter.sendDiagnostic(context, id, activeConversationId)
        status = if (result.submitted) {
            if (isArabic) "تم إرسال طلب الفحص؛ اضغطي تحديث النتائج بعد لحظات." else result.message
        } else {
            result.message
        }
    }

    // دالة البحث الشامل والمتوازي (تفحص كل الأدوات مرة واحدة)
    fun scanAndAutoAddTools() {
        isScanning = true
        status = if (isArabic) "جاري المسح الشامل لجميع الأدوات..." else "Comprehensive scan for all tools..."
        scope.launch {
            val found = mutableListOf<Triple<String, String, String>>()
            
            // قائمة الأدوات والمنافذ الخاصة بها (تشمل الأدوات التي طلبتها)
            val knownTools = mapOf(
                "Ollama" to "http://127.0.0.1:11434/v1",
                "LM Studio" to "http://127.0.0.1:1234/v1",
                "FreeLLMAPI" to "http://127.0.0.1:3001/v1",
                "OmniRoute" to "http://127.0.0.1:20128/v1",
                "Hermes Agent" to "http://127.0.0.1:8642/v1",
                "OpenCode (Local)" to "http://127.0.0.1:4010/v1",
                "LocalAI" to "http://127.0.0.1:8080/v1",
                "Jan.ai" to "http://127.0.0.1:1337/v1",
                "Text Gen WebUI" to "http://127.0.0.1:5000/v1",
                "Custom Python API" to "http://127.0.0.1:8000/v1"
            )
            
            // مسح متوازي (في نفس الوقت) لتوفير الوقت ومنع التعليق
            withContext(Dispatchers.IO) {
                val deferreds = knownTools.map { (name, url) ->
                    async {
                        try {
                            if (aiClient.quickScan(url)) {
                                val id = "local-" + name.lowercase().replace(" ", "-")
                                Triple(name, url, id)
                            } else null
                        } catch (e: Exception) { null }
                    }
                }
                val results = deferreds.awaitAll().filterNotNull()
                found.addAll(results)
            }
            
            if (found.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    found.forEach { (name, url, id) ->
                        secureStore.saveProvider(id, name, url, "")
                        
                        // أوامر التشغيل الافتراضية لكل أداة (لكي يعمل التشغيل التلقائي)
                        val defaultCmd = when(name) {
                            "Ollama" -> "ollama serve &"
                            "LM Studio" -> "lms server start &"
                            "FreeLLMAPI" -> "cd ~/freellmapi && npm run dev &"
                            "OmniRoute" -> "npx -y omniroute &"
                            "Hermes Agent" -> "hermes gateway &"
                            "OpenCode (Local)" -> "opencode-llm-proxy &"
                            else -> ""
                        }
                        if (defaultCmd.isNotBlank()) secureStore.saveProviderStartCommand(id, defaultCmd)
                        
                        // جلب النماذج وحفظها
                        try {
                            val raw = aiClient.listModels(url, "")
                            val models = aiClient.parseModelIds(raw)
                            secureStore.saveModels(id, models)
                        } catch (_: Exception) {}
                    }
                }
                withContext(Dispatchers.Main) {
                    status = if (isArabic) "تمت إضافة ${found.size} أداة ونماذجها بنجاح!" else "Added ${found.size} tools successfully!"
                }
            } else {
                withContext(Dispatchers.Main) {
                    status = if (isArabic) "لم يتم العثور على أدوات. تأكدي من تشغيلها في Termux." else "No tools found. Make sure they are running in Termux."
                }
            }
            isScanning = false
        }
    }

    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(if (isArabic) "بيئة التشغيل والحزم" else "Runtime & Packages", style = MaterialTheme.typography.headlineSmall)
            Text(if (isArabic) "إدارة ملفات ربط الوكلاء بالمحادثات. حفظ الربط لا يعني أن Termux متصل ثنائي الاتجاه؛ يلزم إعداد الجسر على الجهاز." else "Manage agent profiles linked to chat IDs. Saving a profile does not create a live two-way Termux connection; the device bridge must also be configured.")
        }
        
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isArabic) "البحث التلقائي الشامل (كل الأدوات)" else "Comprehensive Auto-Scan (All Tools)", style = MaterialTheme.typography.titleMedium)
                    Text(if (isArabic) "سيقوم التطبيق بفحص 10 منافذ شائعة في وقت واحد، وإضافة جميع الأدوات ونماذجها تلقائياً." else "The app will scan 10 common ports simultaneously, adding all tools and models automatically.", style = MaterialTheme.typography.bodySmall)
                    
                    Button(
                        enabled = !isScanning && termuxInstalled, 
                        onClick = { scanAndAutoAddTools() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isScanning) (if (isArabic) "جاري المسح المتوازي..." else "Parallel scanning...") else (if (isArabic) "بحث وإضافة تلقائية للجميع" else "Auto-Scan & Add All"))
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isArabic) "حالة Termux" else "Termux status", style = MaterialTheme.typography.titleMedium)
                    Text(if (termuxInstalled) (if (isArabic) "Termux مثبت" else "Termux installed") else (if (isArabic) "Termux غير مثبت" else "Termux not installed"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { termuxInstalled = TermuxBridgeAdapter.isInstalled(context); status = "" }) { Text(if (isArabic) "فحص" else "Check") }
                        if (termuxInstalled) OutlinedButton(onClick = {
                            context.packageManager.getLaunchIntentForPackage(TermuxBridgeAdapter.TERMUX_PACKAGE)?.let(context::startActivity)
                                ?: run { status = if (isArabic) "تعذر فتح Termux" else "Could not open Termux" }
                        }) { Text(if (isArabic) "فتح Termux للإعداد" else "Open Termux setup") }
                    }
                    if (termuxInstalled && !runCommandGranted) {
                        OutlinedButton(onClick = { runCommandPermissionLauncher.launch("com.termux.permission.RUN_COMMAND") }) {
                            Text(if (isArabic) "طلب إذن RUN_COMMAND" else "Request RUN_COMMAND permission")
                        }
                    }
                    Text(if (isArabic) "فعّلي السماح بتشغيل الأوامر من تطبيقات خارجية داخل Termux. حالة الإذن: " + (if (runCommandGranted) "ممنوح" else "غير ممنوح") else "Enable external-app command execution in Termux. Permission: " + (if (runCommandGranted) "granted" else "not granted"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.primary) }

        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isArabic) "اختبار الجسر ثنائي الاتجاه" else "Two-way bridge test", style = MaterialTheme.typography.titleMedium)
                    Text(if (isArabic) "المحادثة النشطة: #$activeConversationId. نتائج الفحص تُضاف تلقائيًا لنفس المحادثة عند الرجوع إليها." else "Active conversation: #$activeConversationId. Diagnostic results are automatically imported into that chat when it resumes.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(enabled = termuxInstalled, onClick = { runDiagnostic("pwd") }) { Text("pwd") }
                        OutlinedButton(enabled = termuxInstalled, onClick = { runDiagnostic("date") }) { Text("date") }
                        OutlinedButton(enabled = termuxInstalled, onClick = { runDiagnostic("uname") }) { Text("uname") }
                        OutlinedButton(enabled = termuxInstalled, onClick = { runDiagnostic("list_home") }) { Text("ls") }
                    }
                    Button(onClick = { refreshBridgeResult() }) { Text(if (isArabic) "تحديث النتائج" else "Refresh results") }
                    if (bridgeMeta.isNotBlank()) Text(bridgeMeta, style = MaterialTheme.typography.labelMedium)
                    if (bridgeOutput.isNotBlank()) Text(bridgeOutput, style = MaterialTheme.typography.bodySmall)
                    if (bridgeError.isNotBlank()) Text(bridgeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isArabic) "ربط وكيل بمحادثة" else "Link an agent to a conversation", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(profileName, { profileName = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isArabic) "اسم الوكيل" else "Agent name") }, singleLine = true)
                    OutlinedTextField(conversationId, { conversationId = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isArabic) "رقم المحادثة" else "Conversation ID") }, singleLine = true)
                    OutlinedTextField(runtimeId, { runtimeId = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isArabic) "معرّف بيئة التشغيل" else "Runtime ID") }, singleLine = true)
                    OutlinedTextField(workDir, { workDir = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (isArabic) "مجلد العمل" else "Working directory") }, singleLine = true)
                    Button(enabled = profileName.isNotBlank() && (conversationId.toIntOrNull() ?: 0) > 0 && runtimeId.isNotBlank() && workDir.startsWith("/"), onClick = {
                        val profile = TermuxAgentProfile(UUID.randomUUID().toString(), profileName.trim(), conversationId.toInt(), runtimeId.trim(), workDir.trim())
                        store.save(profile)
                        profiles = store.list()
                        profileName = ""
                        status = if (isArabic) "تم حفظ ملف الربط محليًا." else "Agent profile saved locally."
                    }) { Text(if (isArabic) "حفظ الربط" else "Save link") }
                }
            }
        }

        if (profiles.isNotEmpty()) {
            item { Text(if (isArabic) "ملفات الوكلاء المحفوظة" else "Saved agent profiles", style = MaterialTheme.typography.titleMedium) }
            items(profiles, key = { it.id }) { profile ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                        Text("Chat #${profile.conversationId} • ${profile.runtimeId}")
                        Text(profile.workingDirectory, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { store.delete(profile.id); profiles = store.list() }) { Text(if (isArabic) "حذف الملف" else "Delete profile") }
                    }
                }
            }
        }
        
        item { Text(if (isArabic) "كتالوج الحزم" else "Package catalog", style = MaterialTheme.typography.titleLarge) }
        items(RuntimeCatalog.components, key = { it.id }) { component ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(component.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(component.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (isArabic) component.purposeAr else component.purposeEn)
                    Text(if (isArabic) component.noteAr else component.noteEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text(component.installCommand, style = MaterialTheme.typography.bodySmall)
                    Button(enabled = termuxInstalled && runningComponent == null, onClick = { pendingApproval = component }) {
                        Text(if (component.id == "pip") (if (isArabic) "فحص pip" else "Check pip") else if (isArabic) "مراجعة وموافقة" else "Review & approve")
                    }
                }
            }
        }
        item { Text(if (isArabic) "إرسال الأمر لا يثبت اكتمال التنفيذ؛ النتيجة الحالية تُراجع من إشعارات ومخرجات Termux." else "Command submission is not proof of completion; inspect Termux notifications and output.", style = MaterialTheme.typography.bodySmall) }
    }

    if (pendingApproval != null) {
        val selected = pendingApproval!!
        AlertDialog(
            onDismissRequest = { pendingApproval = null },
            title = { Text(if (isArabic) "تأكيد تنفيذ الأمر" else "Confirm command execution") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isArabic) "راجعي الأمر قبل الموافقة:" else "Review the command before approval:")
                    Text(selected.installCommand)
                    Text(if (isArabic) "سينفذ داخل Termux بصلاحياته." else "It will execute inside Termux with its own permissions.")
                } 
            },
            confirmButton = { 
                TextButton(onClick = {
                    pendingApproval = null
                    runningComponent = selected.id
                    val result = TermuxBridgeAdapter.sendApprovedCommand(context, selected.id, activeConversationId)
                    status = result.message
                    audit.record("termux_command", selected.id + ": " + selected.installCommand, if (result.submitted) "submitted" else "failed")
                    runningComponent = null
                }) { Text(if (isArabic) "موافقة وتنفيذ" else "Approve & run") } 
            },
            dismissButton = { 
                TextButton(onClick = {
                    audit.record("termux_command", selected.id + ": " + selected.installCommand, "rejected")
                    pendingApproval = null
                }) { Text(if (isArabic) "رفض" else "Deny") } 
            }
        )
    }
}
// END OF FILE
