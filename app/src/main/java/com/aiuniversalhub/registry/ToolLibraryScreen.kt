package com.aiuniversalhub.registry

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aiuniversalhub.data.SecureStore
import com.aiuniversalhub.data.ToolAnalyzer
import com.aiuniversalhub.net.AiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolLibraryScreen(isArabic: Boolean) {
    val context = LocalContext.current
    val registry = remember { ToolRegistry(context) }
    val secureStore = remember { SecureStore(context) }
    val client = remember { AiClient() }
    val scope = rememberCoroutineScope()
    var tools by remember { mutableStateOf(registry.all()) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("API tool") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // متغيرات تحليل الملفات
    var showAnalyzeDialog by remember { mutableStateOf(false) }
    var analyzedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var analyzeName by remember { mutableStateOf("") }
    var analyzeUrl by remember { mutableStateOf("") }

    // منتقي الملفات لتحليل الكود
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val extractedModels = ToolAnalyzer.extractModelsFromCode(content)
                        withContext(Dispatchers.Main) {
                            analyzedModels = extractedModels
                            analyzeName = "أداة محللة" // اسم افتراضي
                            analyzeUrl = ""
                            showAnalyzeDialog = true
                        }
                    }
                } catch (e: Exception) {
                    // التعامل مع الخطأ إن لزم الأمر
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isArabic) "مكتبتي: النماذج والأدوات" else "My Models & Tools", style = MaterialTheme.typography.headlineSmall)
        Text(if (isArabic) "عند حفظ مفتاح أداة لها واجهة نماذج متوافقة، يحاول التطبيق اكتشاف نماذجها وحفظها تلقائيًا كمزود مستقل." else "When you save credentials for a tool exposing a compatible model API, the Hub discovers and saves its models as a separate provider.")
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }) { Text(if (isArabic) "＋ إضافة أداة جديدة" else "＋ Add new tool") }
            // زر تحليل ملف الأداة الجديد
            OutlinedButton(onClick = { filePicker.launch("*/*") }) {
                Text(if (isArabic) "📄 تحليل ملف أداة" else "📄 Analyze tool file")
            }
        }

        if (tools.isEmpty()) Text(if (isArabic) "لم تضف أدوات بعد." else "No saved tools yet.")
        tools.forEach { tool ->
            val providerId = "tool-${tool.id}"
            var credential by remember(tool.id) { mutableStateOf(secureStore.getApiKey(providerId) ?: "") }
            var hasCredential by remember(tool.id) { mutableStateOf(!secureStore.getApiKey(providerId).isNullOrBlank()) }
            var models by remember(tool.id) { mutableStateOf(secureStore.getModels(providerId)) }
            var discoveryStatus by remember(tool.id) { mutableStateOf(if (models.isNotEmpty()) (if (isArabic) "تم حفظ ${models.size} نموذج" else "${models.size} models saved") else "") }
            var discovering by remember(tool.id) { mutableStateOf(false) }
            fun discoverModels() {
                if (tool.launchUrl.isBlank()) {
                    discoveryStatus = if (isArabic) "أضيفي Base URL لواجهة API أولًا." else "Add the API base URL first."
                    return
                }
                discovering = true
                discoveryStatus = if (isArabic) "جارٍ التحقق واكتشاف النماذج…" else "Validating and discovering models…"
                scope.launch {
                    try {
                        val found = withContext(Dispatchers.IO) {
                            client.parseModelIds(client.listModels(tool.launchUrl, credential.trim()))
                        }
                        if (found.isEmpty()) {
                            discoveryStatus = if (isArabic) "الاتصال نجح لكن المزود لم يرجع قائمة نماذج." else "Connected, but the provider returned no model list."
                        } else {
                            secureStore.saveProvider(providerId, tool.name, tool.launchUrl, credential.trim())
                            secureStore.saveModels(providerId, found)
                            models = found
                            discoveryStatus = if (isArabic) "تم اكتشاف وحفظ ${found.size} نموذجًا. ستظهر في اختيار المزود داخل المحادثة." else "Discovered and saved ${found.size} models. They are available in the chat provider selector."
                        }
                    } catch (e: Exception) {
                        discoveryStatus = (if (isArabic) "لم يتم اكتشاف نماذج من هذا الرابط: " else "Model discovery unavailable for this endpoint: ") + (e.message ?: "Unknown error")
                    } finally { discovering = false }
                }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tool.name, style = MaterialTheme.typography.titleMedium)
                    Text(tool.kind, color = MaterialTheme.colorScheme.primary)
                    if (tool.notes.isNotBlank()) Text(tool.notes)
                    Text(if (hasCredential) (if (isArabic) "المفتاح: محفوظ ومشفر" else "Credential: saved and encrypted") else (if (isArabic) "المفتاح: غير مضاف" else "Credential: not added"), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "مفتاح API / Token" else "API key / Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (credential.isNotBlank()) {
                                secureStore.saveApiKey(providerId, credential.trim())
                                hasCredential = true
                                discoverModels()
                            }
                        }, enabled = credential.isNotBlank() && !discovering) { Text(if (isArabic) "حفظ واكتشاف النماذج" else "Save & discover models") }
                        OutlinedButton(onClick = {
                            secureStore.deleteApiKey(providerId)
                            secureStore.deleteProvider(providerId)
                            credential = ""; hasCredential = false; models = emptyList()
                            discoveryStatus = if (isArabic) "تم حذف المفتاح وربط المزود." else "Credential and provider link removed."
                        }, enabled = hasCredential) { Text(if (isArabic) "حذف المفتاح" else "Delete key") }
                    }
                    if (discoveryStatus.isNotBlank()) Text(discoveryStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    if (models.isNotEmpty()) {
                        Text(if (isArabic) "النماذج المكتشفة (${models.size})" else "Discovered models (${models.size})", style = MaterialTheme.typography.titleSmall)
                        models.take(30).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (models.size > 30) Text(if (isArabic) "و${models.size - 30} نموذجًا آخر…" else "and ${models.size - 30} more…", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { discoverModels() }, enabled = !discovering && tool.launchUrl.isNotBlank()) {
                            Text(if (discovering) (if (isArabic) "جارٍ الفحص…" else "Checking…") else (if (isArabic) "تحديث النماذج" else "Refresh models"))
                        }
                        if (tool.launchUrl.startsWith("https://") || tool.launchUrl.startsWith("http://")) {
                            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.launchUrl))) }) {
                                Text(if (isArabic) "فتح رابط الأداة" else "Open tool URL")
                            }
                        }
                    }
                    TextButton(onClick = {
                        registry.remove(tool.id)
                        secureStore.deleteApiKey(providerId)
                        secureStore.deleteProvider(providerId)
                        tools = registry.all()
                    }) { Text(if (isArabic) "حذف الأداة والمفتاح" else "Remove tool and key") }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (isArabic) "إضافة أداة جديدة" else "Add a new tool") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(if (isArabic) "الاسم" else "Name") }, singleLine = true)
                    OutlinedTextField(kind, { kind = it }, label = { Text(if (isArabic) "النوع / المزود" else "Type / provider") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text(if (isArabic) "رابط Base API (وليس صفحة الموقع)" else "API base URL (not the website homepage)") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text(if (isArabic) "ملاحظات وإعدادات" else "Notes and settings") })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    registry.save(ToolRegistry.newTool(name, kind, url, notes))
                    tools = registry.all()
                    name = ""; url = ""; notes = ""; kind = "API tool"; showAdd = false
                }) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }

    // نافذة تأكيد النماذج المستخرجة من الملف
    if (showAnalyzeDialog) {
        AlertDialog(
            onDismissRequest = { showAnalyzeDialog = false },
            title = { Text(if (isArabic) "تم تحليل الملف!" else "File analyzed!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (analyzedModels.isEmpty()) {
                        Text(if (isArabic) "لم يتم العثور على أسماء نماذج معروفة داخل الكود." else "No known model names were found in the code.")
                    } else {
                        Text(if (isArabic) "تم استخراج ${analyzedModels.size} نموذج من الكود:" else "Extracted ${analyzedModels.size} models from code:")
                        analyzedModels.take(10).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (analyzedModels.size > 10) Text("...", style = MaterialTheme.typography.bodySmall)
                        
                        HorizontalDivider()
                        Text(if (isArabic) "أدخلي رابط الـ API (اختياري) لحفظها كمزود:" else "Enter API URL (optional) to save as provider:")
                        OutlinedTextField(analyzeName, { analyzeName = it }, label = { Text(if (isArabic) "اسم الأداة" else "Tool Name") }, singleLine = true)
                        OutlinedTextField(analyzeUrl, { analyzeUrl = it }, label = { Text("Base URL") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                if (analyzedModels.isNotEmpty()) {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val newTool = ToolRegistry.newTool(analyzeName.ifBlank { "Analyzed Tool" }, "Analyzed Code", analyzeUrl, "Extracted from file")
                            registry.save(newTool)
                            val providerId = "tool-${newTool.id}"
                            // حفظ النماذج المكتشفة
                            secureStore.saveProvider(providerId, newTool.name, analyzeUrl, "")
                            secureStore.saveModels(providerId, analyzedModels)
                            
                            withContext(Dispatchers.Main) {
                                tools = registry.all()
                                showAnalyzeDialog = false
                            }
                        }
                    }) { Text(if (isArabic) "موافقة وحفظ" else "Approve & Save") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnalyzeDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") }
            }
        )
    }
}
