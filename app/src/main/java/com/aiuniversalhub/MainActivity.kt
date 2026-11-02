package com.aiuniversalhub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.core.content.ContextCompat
import com.aiuniversalhub.background.BackgroundTaskService
import com.aiuniversalhub.chat.ChatHomeScreen
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
    var selectedTool by remember { mutableStateOf<ToolCard?>(null) }

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

    val hubColors = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFD6B56D),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF100D07),
        secondary = androidx.compose.ui.graphics.Color(0xFFD6B56D),
        onSecondary = androidx.compose.ui.graphics.Color(0xFF17120A),
        tertiary = androidx.compose.ui.graphics.Color(0xFFE7C982),
        background = androidx.compose.ui.graphics.Color(0xFF080808),
        onBackground = androidx.compose.ui.graphics.Color(0xFFF5F0F3),
        surface = androidx.compose.ui.graphics.Color(0xFF151216),
        onSurface = androidx.compose.ui.graphics.Color(0xFFF5F0F3),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF28212A),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFE2D7DF)
    )
    var showAppSettings by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = hubColors) {
        Scaffold(
            containerColor = hubColors.background,
            topBar = {
                if (selectedTab != 0) TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF050505),
                        titleContentColor = hubColors.secondary,
                        actionIconContentColor = hubColors.secondary
                    ),
                    title = { Text("AI Universal Hub", color = hubColors.secondary) },
                    actions = {
                        TextButton(onClick = { showAppSettings = true }) {
                            Text("⚙", color = hubColors.secondary, style = MaterialTheme.typography.titleLarge)
                        }
                        TextButton(onClick = { isArabic = !isArabic }) {
                            Text(if (isArabic) "English" else "العربية", color = hubColors.primary)
                        }
                    }
                )
            },
            bottomBar = {
                if (selectedTab != 0) NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF050505), contentColor = hubColors.primary) {
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
            if (selectedTab == 0) {
                MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFFB08A3E),
                    secondary = androidx.compose.ui.graphics.Color(0xFFD6B56D),
                    background = androidx.compose.ui.graphics.Color.White,
                    surface = androidx.compose.ui.graphics.Color.White,
                    onBackground = androidx.compose.ui.graphics.Color(0xFF211A1E),
                    onSurface = androidx.compose.ui.graphics.Color(0xFF211A1E),
                    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF4F0E7),
                    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF463D2D)
                )) {
                    Surface(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.fillMaxSize().padding(padding)) {
                        ChatHomeScreen(isArabic = isArabic)
                    }
                }
            } else LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 1) {
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
                                    else -> OutlinedButton(onClick = { selectedTool = tool }) {
                                        Text(if (isArabic) "فتح إعدادات الأداة" else "Open tool setup")
                                    }
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
                } else {
                    item { ToolLibraryScreen(isArabic = isArabic) }
                    item { RuntimeCenterScreen(isArabic = isArabic) }
                }
            }
        }
        selectedTool?.let { tool ->
            AlertDialog(
                onDismissRequest = { selectedTool = null },
                title = { Text(tool.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isArabic) tool.descriptionAr else tool.descriptionEn)
                        when (tool.id) {
                            "ollama" -> Text(if (isArabic)
                                "للتوصيل: شغّلي Ollama على جهاز أو خادم يمكن للهاتف الوصول إليه، ثم استخدمي عنوان OpenAI-compatible مثل http://HOST:11434/v1 من إعدادات مزود الذكاء الاصطناعي. localhost على الهاتف لن يشير تلقائيًا إلى جهاز الكمبيوتر."
                                else "Connect an Ollama host reachable from this phone, then use its OpenAI-compatible endpoint (for example http://HOST:11434/v1) in AI provider settings. Phone localhost does not point to your computer.")
                            "mcp" -> Text(if (isArabic)
                                "عميل MCP موجود بشكل أولي داخل المشروع، لكن شاشة إضافة الخادم وإدارة الجلسة واستعراض الأدوات لم تكتمل بعد؛ لذلك لا يوجد اتصال فعلي من هذه الشاشة الآن."
                                else "The project has an early MCP client, but server setup, session management, and tool browsing UI are not complete yet; no live connection is available here.")
                            "litellm" -> Text(if (isArabic)
                                "أدخلي عنوان خادم LiteLLM ومفتاحه في إعدادات مزود الذكاء الاصطناعي داخل شاشة المحادثة. مثال العنوان: http://HOST:4000/v1. يجب أن يكون الخادم شغالًا ويمكن للهاتف الوصول إليه."
                                else "Enter your LiteLLM server URL and key in AI provider settings on the chat screen. Example: http://HOST:4000/v1. The server must be running and reachable from this phone.")
                            else -> Text(if (isArabic) "افتحي Termux لإكمال الإعداد." else "Open Termux to finish setup.")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedTool = null
                        if (tool.id == "termux" && termuxInstalled) {
                            context.packageManager.getLaunchIntentForPackage("com.termux")?.let(context::startActivity)
                        } else if (tool.id == "termux" && tool.installUrl != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.installUrl)))
                        } else {
                            selectedTab = 0
                        }
                    }) { Text(if (isArabic) if (tool.id == "termux") "متابعة" else "الانتقال للمحادثة" else if (tool.id == "termux") "Continue" else "Go to chat") }
                },
                dismissButton = {
                    TextButton(onClick = { selectedTool = null }) { Text(if (isArabic) "إغلاق" else "Close") }
                }
            )
        }
        if (showAppSettings) {
            AlertDialog(
                onDismissRequest = { showAppSettings = false },
                containerColor = hubColors.surface,
                titleContentColor = hubColors.secondary,
                textContentColor = hubColors.onSurface,
                title = { Text(if (isArabic) "إعدادات التطبيق" else "App settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isArabic) "المظهر: أسود أنيق مع لمسات ذهبية." else "Theme: elegant black with gold accents.")
                        Text(if (isArabic) "شاشة المحادثة: خلفية بيضاء." else "Chat screen: white background.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAppSettings = false }) {
                        Text(if (isArabic) "تم" else "Done", color = hubColors.primary)
                    }
                }
            )
        }
    }
}
