package com.aiuniversalhub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class ToolItem(val name: String, val detail: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HubApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var termuxInstalled by remember {
        mutableStateOf(runCatching {
            context.packageManager.getPackageInfo("com.termux", PackageManager.PackageInfoFlags.of(0))
        }.isSuccess)
    }
    val tools = listOf(
        ToolItem("Termux", "Local Android command environment", if (termuxInstalled) "Detected" else "Not installed"),
        ToolItem("Ollama", "Local model runtime · requires compatible host/runtime", "Not connected"),
        ToolItem("MCP", "Connect external tool servers", "Coming next"),
        ToolItem("LiteLLM", "Unified provider gateway", "Coming next")
    )
    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(title = { Text("AI Universal Hub") }, actions = {
                TextButton(onClick = { tab = 0 }) { Text("العربية / EN") }
            })
        }, bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Home") }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Tools") }, icon = {})
            }
        }) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    item { Text("Your AI workspace", style = MaterialTheme.typography.headlineSmall) }
                    item { Text("One place to manage models, providers and tools.") }
                    item { Card { Column(Modifier.padding(16.dp)) {
                        Text("Foundation status", style = MaterialTheme.typography.titleMedium)
                        Text("App shell ready · provider and chat modules are next")
                    } } }
                    item { Button(onClick = { tab = 1 }) { Text("Open Tools Center") } }
                } else {
                    item { Text("Tools Center", style = MaterialTheme.typography.headlineSmall) }
                    item { Text("Discover and manage compatible tools. Installation requires explicit permission.") }
                    items(tools) { tool ->
                        Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tool.name, style = MaterialTheme.typography.titleMedium)
                            Text(tool.detail, style = MaterialTheme.typography.bodyMedium)
                            Text(tool.status, color = MaterialTheme.colorScheme.primary)
                            if (tool.name == "Termux" && termuxInstalled) {
                                Button(onClick = {
                                    val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                                    if (launch != null) context.startActivity(launch)
                                }) { Text("Open Termux") }
                            } else if (tool.name == "Termux") {
                                OutlinedButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                                    context.startActivity(intent)
                                }) { Text("Termux information") }
                            }
                        } }
                    }
                    item { OutlinedButton(onClick = {
                        termuxInstalled = runCatching {
                            context.packageManager.getPackageInfo("com.termux", PackageManager.PackageInfoFlags.of(0))
                        }.isSuccess
                    }) { Text("Refresh detection") } }
                }
            }
        }
    }
}