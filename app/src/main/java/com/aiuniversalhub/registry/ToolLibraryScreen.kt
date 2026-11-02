package com.aiuniversalhub.registry

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aiuniversalhub.data.SecureStore

@Composable
fun ToolLibraryScreen(isArabic: Boolean) {
    val context = LocalContext.current
    val registry = remember { ToolRegistry(context) }
    val secureStore = remember { SecureStore(context) }
    var tools by remember { mutableStateOf(registry.all()) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("API tool") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isArabic) "مكتبتي: النماذج والأدوات" else "My Models & Tools", style = MaterialTheme.typography.headlineSmall)
        Text(if (isArabic) "مفاتيح كل أداة تُحفظ مشفرة على الجهاز ولا تُكتب في ملفات المشروع أو GitHub." else "Each tool credential is encrypted on this device and is never written to project files or GitHub.")
        Button(onClick = { showAdd = true }) { Text(if (isArabic) "＋ إضافة أداة جديدة" else "＋ Add new tool") }
        if (tools.isEmpty()) Text(if (isArabic) "لم تضف أدوات بعد." else "No saved tools yet.")
        tools.forEach { tool ->
            var credential by remember(tool.id) { mutableStateOf(secureStore.getApiKey("tool-${tool.id}") ?: "") }
            var hasCredential by remember(tool.id) { mutableStateOf(!secureStore.getApiKey("tool-${tool.id}").isNullOrBlank()) }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tool.name, style = MaterialTheme.typography.titleMedium)
                    Text(tool.kind, color = MaterialTheme.colorScheme.primary)
                    if (tool.notes.isNotBlank()) Text(tool.notes)
                    Text(if (hasCredential) (if (isArabic) "المفتاح: محفوظ ومشفر" else "Credential: saved and encrypted") else (if (isArabic) "المفتاح: غير مضاف" else "Credential: not added"), style = MaterialTheme.typography.labelMedium, color = if (hasCredential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
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
                                secureStore.saveApiKey("tool-${tool.id}", credential.trim())
                                hasCredential = true
                            }
                        }, enabled = credential.isNotBlank()) { Text(if (isArabic) "حفظ المفتاح" else "Save key") }
                        OutlinedButton(onClick = {
                            secureStore.deleteApiKey("tool-${tool.id}")
                            credential = ""
                            hasCredential = false
                        }, enabled = hasCredential) { Text(if (isArabic) "حذف المفتاح" else "Delete key") }
                    }
                    if (tool.launchUrl.startsWith("https://") || tool.launchUrl.startsWith("http://")) {
                        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.launchUrl))) }) {
                            Text(if (isArabic) "فتح رابط الأداة" else "Open tool URL")
                        }
                    }
                    TextButton(onClick = {
                        registry.remove(tool.id)
                        secureStore.deleteApiKey("tool-${tool.id}")
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
                    OutlinedTextField(url, { url = it }, label = { Text(if (isArabic) "رابط API أو التشغيل (اختياري)" else "API or launch URL (optional)") }, singleLine = true)
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
}
