package com.aiuniversalhub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.core.content.ContextCompat
import com.aiuniversalhub.background.BackgroundTaskService
import com.aiuniversalhub.registry.ToolLibraryScreen
import com.aiuniversalhub.runtime.RuntimeCenterScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class ToolCard(
    val id: String,
    val name: String,
    val descriptionEn: String,
    val descriptionAr: String,
    val stateEn: String,
    val stateAr: String,
    val installUrl: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HubApp() }
    }
}

private fun isPackageInstalled(context: android.content.Context, packageName: String): Boolean {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp() {
    val context = LocalContext.current
    var isArabic by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var termuxInstalled by remember { mutableStateOf(isPackageInstalled(context, "com.termux")) }
    var message by remember { mutableStateOf("") }

    val tools = listOf(
        ToolCard("termux", "Termux", "Android terminal environment", "بيئة أوامر محلية على أندرويد",
            if (termuxInstalled) "Installed" else "Not installed",
            if (termuxInstalled) "مثبّت" else "غير مثبّت",
            "https://f-droid.org/packages/com.termux/"),
        ToolCard("ollama", "Ollama", "Local model runtime; needs a compatible device or host", "تشغيل نماذج محلية؛ يحتاج جهازًا أو مضيفًا متوافقًا",
            "Not connected", "غير متصل"),
        ToolCard("mcp", "MCP", "Connect approved external tool servers", "ربط خوادم الأدوات الخارجية بعد الموافقة",
            "Integration planned", "التكامل قيد التطوير"),
        ToolCard("litellm", "LiteLLM", "Unified gateway for supported AI providers", "بوابة موحدة لمزودي الذكاء الاصطناعي",
            "Integration planned", "التكامل قيد التطوير")
    )

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI Universal Hub") },
                    actions = {
                        TextButton(onClick = { isArabic = !isArabic }) {
                            Text(if (isArabic) "English" else "العربية")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text(if (isArabic) "الرئيسية" else "Home") },
                        icon = { Text("⌂") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text(if (isArabic) "الأدوات" else "Tools") },
                        icon = { Text("⚙") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text(if (isArabic) "مكتبتي" else "My Library") },
                        icon = { Text("▣") }
                    )
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 0) {
                    item {
                        Text(
                            if (isArabic) "مساحة الذكاء الاصطناعي الخاصة بك" else "Your AI workspace",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    item {
                        Text(
                            if (isArabic) "إدارة النماذج والمزودين والأدوات من مكان واحد." else "Manage models, providers and tools in one place."
                        )
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isArabic) "حالة المنصة" else "Platform status", style = MaterialTheme.typography.titleMedium)
                                Text(if (isArabic) "الهيكل الأساسي ومركز الأدوات جاهزان للتطوير." else "App foundation and tools catalog are ready for the next integrations.")
                                Text(if (isArabic) "Termux: " + if (termuxInstalled) "مثبّت" else "غير مثبّت" else "Termux: " + if (termuxInstalled) "Installed" else "Not installed")
                            }
                        }
                    }
                    item {
                        Button(onClick = { selectedTab = 1 }) {
                            Text(if (isArabic) "فتح مركز الأدوات" else "Open Tools Center")
                        }
                    }
                } else if (selectedTab == 1) {
                    item {
                        Text(if (isArabic) "مركز الأدوات" else "Tools Center", style = MaterialTheme.typography.headlineSmall)
                        Text(if (isArabic) "لن يتم تثبيت أو تشغيل أي أداة بصلاحيات خاصة دون موافقتك." else "No tool will be installed or granted special permissions without your approval.")
                    }
                    items(tools.size) { index ->
                        val tool = tools[index]
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tool.name, style = MaterialTheme.typography.titleLarge)
                                Text(if (isArabic) tool.descriptionAr else tool.descriptionEn)
                                Text(
                                    (if (isArabic) "الحالة: " else "Status: ") + if (isArabic) tool.stateAr else tool.stateEn,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                when (tool.id) {
                                    "termux" -> {
                                        if (termuxInstalled) {
                                            Button(onClick = {
                                                val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                                                if (launch != null) context.startActivity(launch)
                                                else message = if (isArabic) "تعذر فتح Termux." else "Could not open Termux."
                                            }) { Text(if (isArabic) "فتح Termux" else "Open Termux") }
                                        } else {
                                            OutlinedButton(onClick = {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.installUrl)))
                                            }) { Text(if (isArabic) "عرض صفحة التثبيت" else "Open install page") }
                                        }
                                    }
                                    "ollama" -> OutlinedButton(onClick = {
                                        message = if (isArabic) "سيتم إضافة فحص الاتصال المحلي في مرحلة لاحقة." else "Local runtime connection checks are planned for a later milestone."
                                    }) { Text(if (isArabic) "تفاصيل الربط" else "Connection details") }
                                    else -> OutlinedButton(onClick = {
                                        message = if (isArabic) "التكامل قيد التطوير وسيظهر هنا عند جاهزيته." else "This integration is under development."
                                    }) { Text(if (isArabic) "التفاصيل" else "Details") }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = {
                            termuxInstalled = isPackageInstalled(context, "com.termux")
                            message = if (isArabic) "تم تحديث حالة Termux." else "Termux status refreshed."
                        }) { Text(if (isArabic) "تحديث حالة الأدوات" else "Refresh tool status") }
                    }
                    if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.secondary) }
                }
            }
        }
    }
}
