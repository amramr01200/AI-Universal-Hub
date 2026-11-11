package com.aiuniversalhub.runtime

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Claude Code-first runtime screen.
 * Generic model scanners and unrelated package catalogs are intentionally not shown here.
 */
@Composable
fun RuntimeCenterScreen(isArabic: Boolean, activeConversationId: Int = -1) {
    val context = LocalContext.current
    var termuxInstalled by remember { mutableStateOf(TermuxBridgeAdapter.isInstalled(context)) }
    var runCommandGranted by remember { mutableStateOf(TermuxBridgeAdapter.canRunCommands(context)) }
    var status by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        runCommandGranted = granted
        status = if (granted) {
            if (isArabic) "تم منح إذن تشغيل الأوامر." else "RUN_COMMAND permission granted."
        } else {
            if (isArabic) "لم يُمنح الإذن. راجعي إعدادات Termux والتطبيق." else "Permission not granted. Check Termux and app settings."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Claude Code", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (isArabic)
                            "مساحة البرمجة الأساسية. يتم تشغيل Claude Code الرسمي داخل Ubuntu على Termux، مع بقاء جلسة الطرفية مرئية وتأكيدات الصلاحيات مفعّلة."
                        else
                            "The primary coding workspace. The official Claude Code CLI runs inside Ubuntu under Termux, with a visible terminal and permission confirmations enabled.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        enabled = termuxInstalled && runCommandGranted,
                        onClick = {
                            status = ClaudeCodeLauncher.openSetup(context).message
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isArabic) "تثبيت وإعداد Claude Code" else "Install & configure Claude Code")
                    }
                    OutlinedButton(
                        enabled = termuxInstalled && runCommandGranted,
                        onClick = {
                            status = ClaudeCodeLauncher.openClaudeCode(context).message
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isArabic) "تشغيل Claude Code" else "Launch Claude Code")
                    }
                    Text(
                        if (isArabic)
                            "التشغيل على Android تجريبي عبر Ubuntu (proot)، وليس نظامًا مدعومًا رسميًا من Anthropic."
                        else
                            "Android execution is experimental through Ubuntu (proot), not an officially supported Anthropic platform.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(if (isArabic) "حالة بيئة التشغيل" else "Runtime status", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (termuxInstalled) {
                            if (isArabic) "Termux مثبت" else "Termux installed"
                        } else {
                            if (isArabic) "Termux غير مثبت" else "Termux not installed"
                        }
                    )
                    Text(
                        if (runCommandGranted) {
                            if (isArabic) "إذن تشغيل الأوامر: ممنوح" else "RUN_COMMAND permission: granted"
                        } else {
                            if (isArabic) "إذن تشغيل الأوامر: غير ممنوح" else "RUN_COMMAND permission: not granted"
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            termuxInstalled = TermuxBridgeAdapter.isInstalled(context)
                            runCommandGranted = TermuxBridgeAdapter.canRunCommands(context)
                            status = ""
                        }) {
                            Text(if (isArabic) "تحديث الحالة" else "Refresh status")
                        }
                        if (termuxInstalled) {
                            OutlinedButton(onClick = {
                                context.packageManager.getLaunchIntentForPackage(TermuxBridgeAdapter.TERMUX_PACKAGE)
                                    ?.let(context::startActivity)
                                    ?: run { status = if (isArabic) "تعذر فتح Termux" else "Could not open Termux" }
                            }) {
                                Text(if (isArabic) "فتح Termux" else "Open Termux")
                            }
                        }
                    }
                    if (termuxInstalled && !runCommandGranted) {
                        Button(onClick = {
                            permissionLauncher.launch("com.termux.permission.RUN_COMMAND")
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isArabic) "طلب إذن RUN_COMMAND" else "Request RUN_COMMAND permission")
                        }
                    }
                    Text(
                        if (isArabic)
                            "داخل Termux فعّلي السماح بتشغيل الأوامر من التطبيقات الخارجية. لا تشاركي مفاتيح API في المحادثة أو GitHub."
                        else
                            "In Termux, enable command execution from external apps. Never paste API keys into chat or GitHub.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (status.isNotBlank()) {
            item { Text(status, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
