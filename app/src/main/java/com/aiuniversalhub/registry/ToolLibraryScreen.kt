package com.aiuniversalhub.registry

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ToolLibraryScreen(isArabic: Boolean) {
    val context = LocalContext.current
    val registry = remember { ToolRegistry(context) }
    var tools by remember { mutableStateOf(registry.all()) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("AI tool") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isArabic) "مكتبتي: النماذج والأدوات" else "My Models & Tools", style = MaterialTheme.typography.headlineSmall)
        Text(if (isArabic) "احفظ بطاقة كل أداة وإعداداتها ورابط تشغيلها هنا. التسجيل لا يعني تثبيت البرنامج تلقائيًا." else "Save each tool's profile, settings and launch link here. Registering does not install software automatically.")
        Button(onClick = { showAdd = true }) { Text(if (isArabic) "إضافة أداة أو نموذج" else "Add tool or model") }
        if (tools.isEmpty()) {
            Text(if (isArabic) "لم تضف أدوات بعد." else "No saved tools yet.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tools, key = { it.id }) { tool ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tool.name, style = MaterialTheme.typography.titleMedium)
                        Text(tool.kind, color = MaterialTheme.colorScheme.primary)
                        if (tool.notes.isNotBlank()) Text(tool.notes)
                        Text((if (isArabic) "الحالة: محفوظة في المكتبة" else "Status: saved in library"), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tool.launchUrl.startsWith("https://") || tool.launchUrl.startsWith("http://")) {
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.launchUrl)))
                                }) { Text(if (isArabic) "فتح" else "Open") }
                            }
                            TextButton(onClick = {
                                registry.remove(tool.id)
                                tools = registry.all()
                            }) { Text(if (isArabic) "حذف البطاقة" else "Remove") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (isArabic) "إضافة إلى مكتبتي" else "Add to my library") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(if (isArabic) "الاسم" else "Name") }, singleLine = true)
                    OutlinedTextField(kind, { kind = it }, label = { Text(if (isArabic) "النوع: نموذج / أداة" else "Type: model / tool") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text(if (isArabic) "رابط التشغيل (اختياري)" else "Launch URL (optional)") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text(if (isArabic) "ملاحظات وإعدادات" else "Notes and settings") })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    registry.save(ToolRegistry.newTool(name, kind, url, notes))
                    tools = registry.all()
                    name = ""; url = ""; notes = ""; kind = "AI tool"; showAdd = false
                }) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }
}