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
import com.aiuniversalhub.integrations.McpHttpClient
import com.aiuniversalhub.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
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


private data class ExternalProvider(
    val name: String,
    val descriptionAr: String,
    val endpoint: String,
    val catalogUrl: String,
    val keyUrl: String
)

private val externalProviders = listOf(
    ExternalProvider("OpenRouter", "بوابة واحدة لموديلات من شركات متعددة، وبها موديلات مجانية محدودة حسب التوفر.", "https://openrouter.ai/api/v1", "https://openrouter.ai/models", "https://openrouter.ai/settings/keys"),
    ExternalProvider("OpenAI", "واجهة OpenAI الرسمية؛ استخدام API منفصل عن اشتراك ChatGPT.", "https://api.openai.com/v1", "https://platform.openai.com/docs/models", "https://platform.openai.com/api-keys"),
    ExternalProvider("Google Gemini", "نماذج Gemini عبر Google AI Studio؛ راجعي شروط الحصة المجانية.", "https://generativelanguage.googleapis.com/v1beta/openai", "https://ai.google.dev/gemini-api/docs/models", "https://aistudio.google.com/app/apikey"),
    ExternalProvider("Groq", "استضافة نماذج سريعة؛ حدود الاستخدام والخطط تتغير حسب الحساب.", "https://api.groq.com/openai/v1", "https://console.groq.com/docs/models", "https://console.groq.com/keys"),
    ExternalProvider("DeepSeek", "نماذج DeepSeek عبر API؛ راجعي الأسعار والرصيد قبل الاستخدام.", "https://api.deepseek.com/v1", "https://api-docs.deepseek.com/", "https://platform.deepseek.com/api_keys"),
    ExternalProvider("Mistral AI", "نماذج Mistral وخيارات API للمطورين.", "https://api.mistral.ai/v1", "https://docs.mistral.ai/getting-started/models/", "https://console.mistral.ai/api-keys/"),
    ExternalProvider("Together AI", "كتالوج نماذج مستضافة من عدة عائلات؛ الأسعار حسب النموذج.", "https://api.together.xyz/v1", "https://www.together.ai/models", "https://api.together.ai/settings/api-keys")
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
    val secureStore = remember { SecureStore(context) }
    var isArabic by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var termuxInstalled by remember { mutableStateOf(isPackageInstalled(context, "com.termux")) }
    var message by remember { mutableStateOf("") }
    var selectedTool by remember { mutableStateOf<ToolCard?>(null) }
    var showAddNetwork by remember { mutableStateOf(false) }
    var networkName by remember { mutableStateOf("") }
    var networkEndpoint by remember { mutableStateOf("") }
    var networkKey by remember { mutableStateOf("") }
    var savedNetworks by remember { mutableStateOf(secureStore.listProviders()) }

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
    var showHubMenu by remember { mutableStateOf(false) }
    var requestChatAiSettings by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = hubColors) {
        Scaffold(
            containerColor = hubColors.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF050505),
                        titleContentColor = hubColors.secondary,
                        actionIconContentColor = hubColors.secondary
                    ),
                    title = { Text("AI Universal Hub", color = hubColors.secondary) },
                    actions = {
                        Box {
                            IconButton(onClick = { showHubMenu = true }) {
                                Text("⚙", color = hubColors.secondary, style = MaterialTheme.typography.titleLarge)
                            }
                            DropdownMenu(expanded = showHubMenu, onDismissRequest = { showHubMenu = false }) {
                                DropdownMenuItem(text = { Text(if (isArabic) "مكتبتي" else "My Library") }, onClick = { selectedTab = 2; showHubMenu = false })
                                DropdownMenuItem(text = { Text(if (isArabic) "الشبكات" else "Networks") }, onClick = { selectedTab = 3; showHubMenu = false })
                                DropdownMenuItem(text = { Text(if (isArabic) "إعدادات الذكاء الاصطناعي" else "AI settings") }, onClick = { selectedTab = 0; requestChatAiSettings = true; showHubMenu = false })
                                DropdownMenuItem(text = { Text(if (isArabic) "تغيير اللغة" else "Change language") }, onClick = { isArabic = !isArabic; showHubMenu = false })
                                DropdownMenuItem(text = { Text(if (isArabic) "مركز الأدوات" else "Tools Center") }, onClick = { selectedTab = 1; showHubMenu = false })
                            }
                        }
                    }
                )
            },
            bottomBar = if (selectedTab == 0) null else {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF050505), contentColor = hubColors.primary) {
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text(if (isArabic) "مكتبتي" else "My Library") },
                        icon = { Text("▣") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        label = { Text(if (isArabic) "الشبكات" else "Providers") },
                        icon = { Text("◎") }
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
                        ChatHomeScreen(isArabic = isArabic, openAiSettings = requestChatAiSettings, onAiSettingsConsumed = { requestChatAiSettings = false })
                    }
                }
            } else SelectionContainer {
                LazyColumn(
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
                } else if (selectedTab == 2) {
                    item { ToolLibraryScreen(isArabic = isArabic) }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isArabic) "تحديث بيئات التشغيل عبر Termux" else "Runtime updates through Termux", style = MaterialTheme.typography.titleMedium)
                                Text(if (isArabic) "إدارة وتحديث Python وNode وGit وباقي الحزم تتم من داخل Termux فقط. لن تظهر قائمة تحديثات منفصلة داخل Hub." else "Manage and update Python, Node, Git and other packages in Termux only. Hub does not provide a separate package update list.")
                                OutlinedButton(onClick = {
                                    val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                                    if (launch != null) context.startActivity(launch)
                                    else message = if (isArabic) "Termux غير مثبت." else "Termux is not installed."
                                }) { Text(if (isArabic) "فتح Termux" else "Open Termux") }
                            }
                        }
                    }
                } else {
                    item {
                        Text(if (isArabic) "شبكات ومزوّدو الخدمات الخارجية" else "External AI provider networks",
                            style = MaterialTheme.typography.headlineSmall, color = hubColors.secondary)
                        Text(if (isArabic)
                            "استكشفي كتالوجات النماذج الرسمية وافتحي صفحة إنشاء مفتاح API لدى المزود. لا نعرض أي رصيد أو حصة على أنها متاحة إلا إذا أكدها المزود."
                            else "Browse official model catalogs and open each provider's API-key page. Quotas and credits are shown only when confirmed by the provider.")
                    }
                    item {
                        Button(onClick = {
                            networkName = ""
                            networkEndpoint = ""
                            networkKey = ""
                            showAddNetwork = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isArabic) "＋ إضافة شبكة جديدة" else "＋ Add network")
                        }
                    }
                    items(externalProviders.size + savedNetworks.size) { index ->
                        if (index >= externalProviders.size) {
                            val saved = savedNetworks[index - externalProviders.size]
                            Card {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(saved.name, style = MaterialTheme.typography.titleLarge, color = hubColors.secondary)
                                    Text((if (isArabic) "عنوان API: " else "API endpoint: ") + saved.baseUrl,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(if (isArabic) "شبكة مضافة ومحفوظة على هذا الجهاز" else "Custom network saved on this device",
                                        style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            selectedTab = 0
                                            message = if (isArabic) "افتحي ترس الإعدادات في المحادثة لاختيار الشبكة وتحميل موديلاتها." else "Open chat AI settings to select this network and load its models."
                                        }) { Text(if (isArabic) "استخدام الشبكة" else "Use network") }
                                        TextButton(onClick = {
                                            secureStore.deleteProvider(saved.id)
                                            savedNetworks = secureStore.listProviders()
                                            message = if (isArabic) "تم حذف الشبكة المحفوظة." else "Saved network deleted."
                                        }) { Text(if (isArabic) "حذف" else "Delete") }
                                    }
                                }
                            }
                        } else {
                        val provider = externalProviders[index]
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(provider.name, style = MaterialTheme.typography.titleLarge, color = hubColors.secondary)
                                Text(if (isArabic) provider.descriptionAr else provider.name)
                                Text((if (isArabic) "عنوان API: " else "API endpoint: ") + provider.endpoint,
                                    style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.catalogUrl)))
                                    }) { Text(if (isArabic) "كتالوج النماذج" else "Model catalog") }
                                    Button(onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl)))
                                    }) { Text(if (isArabic) "صفحة المفتاح" else "API key") }
                                }
                            }
                        }
                        }
                    }
                    item {
                        Text(if (isArabic)
                            "مهم: المفتاح يُنشأ داخل حسابك لدى المزود ثم يُضاف من إعدادات مزود الذكاء الاصطناعي في المحادثة. لا تشاركيه مع أي شخص. بعض الموديلات مجانية بحدود، وبعضها يحتاج رصيدًا مدفوعًا."
                            else "Important: create the key in your provider account, then add it in AI provider settings in chat. Never share it. Some models have limited free tiers; others require paid credits.")
                    }
                }
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
                            "mcp" -> {
                                var endpoint by remember { mutableStateOf(secureStore.getProviderUrl("mcp-server")) }
                                var token by remember { mutableStateOf(secureStore.getApiKey("mcp-server").orEmpty()) }
                                var mcpTools by remember { mutableStateOf(emptyList<String>()) }
                                var mcpStatus by remember { mutableStateOf("") }
                                var mcpBusy by remember { mutableStateOf(false) }
                                val mcpScope = rememberCoroutineScope()
                                OutlinedTextField(endpoint, { endpoint = it }, label = { Text(if (isArabic) "عنوان خادم MCP عبر HTTP(S)" else "MCP HTTP(S) endpoint") }, singleLine = true)
                                OutlinedTextField(token, { token = it }, label = { Text(if (isArabic) "رمز الدخول (اختياري)" else "Bearer token (optional)") }, singleLine = true)
                                Button(enabled = !mcpBusy && endpoint.isNotBlank(), onClick = {
                                    mcpBusy = true
                                    mcpStatus = ""
                                    secureStore.saveProviderUrl("mcp-server", endpoint.trim())
                                    if (token.isBlank()) secureStore.deleteApiKey("mcp-server") else secureStore.saveApiKey("mcp-server", token.trim())
                                    mcpScope.launch {
                                        try {
                                            val found = withContext(Dispatchers.IO) {
                                                McpHttpClient(endpoint.trim(), token.trim()).connectAndListTools()
                                            }
                                            mcpTools = (0 until found.length()).mapNotNull { i ->
                                                found.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                                            }
                                            mcpStatus = if (isArabic) "تم الاتصال. الأدوات المكتشفة: ${mcpTools.size}" else "Connected. Discovered tools: ${mcpTools.size}"
                                        } catch (e: Exception) {
                                            mcpStatus = e.message ?: "MCP connection failed"
                                        } finally { mcpBusy = false }
                                    }
                                }) { Text(if (mcpBusy) (if (isArabic) "جارٍ الاتصال…" else "Connecting…") else (if (isArabic) "اتصال واكتشاف الأدوات" else "Connect and discover tools")) }
                                if (mcpStatus.isNotBlank()) Text(mcpStatus, color = MaterialTheme.colorScheme.secondary)
                                mcpTools.forEach { name -> Text("• $name", style = MaterialTheme.typography.bodySmall) }
                                Text(if (isArabic) "اكتشاف الأدوات لا ينفذها. تشغيل أي أداة يحتاج موافقة صريحة منفصلة." else "Discovery does not execute tools. Every tool call requires separate explicit approval.", style = MaterialTheme.typography.bodySmall)
                            }
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
        if (showAddNetwork) {
            AlertDialog(
                onDismissRequest = { showAddNetwork = false },
                title = { Text(if (isArabic) "إضافة شبكة / مزوّد جديد" else "Add network / provider") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(networkName, { networkName = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isArabic) "اسم الشبكة" else "Network name") }, singleLine = true)
                        OutlinedTextField(networkEndpoint, { networkEndpoint = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Base URL (مثال: https://host/v1)") }, singleLine = true)
                        OutlinedTextField(networkKey, { networkKey = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isArabic) "مفتاح API (اختياري)" else "API key (optional)") },
                            singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Text(if (isArabic) "سيُحفظ الرابط والمفتاح بشكل مشفّر على الجهاز. بعد الحفظ اختاري الشبكة من إعدادات الذكاء الاصطناعي لتحميل الموديلات." else "The endpoint and key are encrypted on-device. After saving, select this network in AI settings to load its models.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val name = networkName.trim()
                        val url = networkEndpoint.trim()
                        val idBase = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                        val id = if (idBase.isBlank()) "network-" + java.util.UUID.randomUUID().toString().take(8) else idBase
                        if (name.isBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) {
                            message = if (isArabic) "أدخلي اسم الشبكة ورابط API صحيحًا يبدأ بـ http:// أو https://" else "Enter a network name and a valid http(s) API URL."
                        } else if (savedNetworks.any { it.id == id }) {
                            message = if (isArabic) "اسم الشبكة مستخدم بالفعل." else "That network name is already in use."
                        } else {
                            secureStore.saveProvider(id, name, url, networkKey)
                            savedNetworks = secureStore.listProviders()
                            networkName = ""; networkEndpoint = ""; networkKey = ""
                            showAddNetwork = false
                            message = if (isArabic) "تم حفظ الشبكة. افتحي المحادثة ثم إعدادات مزوّد الذكاء الاصطناعي لاكتشاف الموديلات." else "Network saved. Open chat AI provider settings to discover its models."
                        }
                    }) { Text(if (isArabic) "حفظ الشبكة" else "Save network") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddNetwork = false }) { Text(if (isArabic) "إلغاء" else "Cancel") }
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
                        OutlinedButton(
                            onClick = { selectedTab = 1; showAppSettings = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isArabic) "مركز الأدوات" else "Tools Center") }
                        OutlinedButton(
                            onClick = { selectedTab = 0; showAppSettings = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isArabic) "العودة للمحادثة" else "Back to chat") }
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
