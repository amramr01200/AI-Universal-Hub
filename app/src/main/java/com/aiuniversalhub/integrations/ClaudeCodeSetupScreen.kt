package com.aiuniversalhub.integrations

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val CLAUDE_SETUP_COMMAND =
    "pkg update -y && pkg install -y curl proot-distro && " +
    "curl -fsSL -o \"\$HOME/setup-claude-code-termux.sh\" " +
    "\"https://raw.githubusercontent.com/amramr01200/AI-Universal-Hub/feature/claude-code-freellmapi-termux/scripts/setup-claude-code-termux.sh\" && " +
    "bash \"\$HOME/setup-claude-code-termux.sh\""

@Composable
fun ClaudeCodeSetupScreen(isArabic: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (isArabic) "Claude Code عبر FreeLLMAPI" else "Claude Code through FreeLLMAPI",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            if (isArabic)
                "يستخدم هذا الإعداد المفتاح الموحّد الموجود عندك بالفعل. لا تنشئي مفتاحًا ثانيًا ولا ترسلي المفتاح في المحادثة."
            else
                "This setup uses the unified key you already created. Do not create another key or paste the key into this chat."
        )
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isArabic) "طريقة التشغيل" else "How it works", style = MaterialTheme.typography.titleMedium)
                Text(if (isArabic)
                    "يُثبّت Claude Code داخل Debian باستخدام proot-distro في Termux. يتصل بخادم FreeLLMAPI المحلي على 127.0.0.1:3001 عبر Anthropic Messages API."
                    else
                    "Claude Code is installed inside Debian using proot-distro in Termux. It connects to your local FreeLLMAPI server at 127.0.0.1:3001 through the Anthropic Messages API.")
                Text(if (isArabic)
                    "بعد التثبيت، شغّلي FreeLLMAPI أولًا، ثم افتحي Debian واكتبي claude-free. ستدخلين المفتاح مرة واحدة في مطالبة مخفية."
                    else
                    "After setup, start FreeLLMAPI first, open Debian, and run claude-free. You enter the key once at a hidden prompt.")
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isArabic) "أمر الإعداد في Termux" else "Termux setup command", style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(CLAUDE_SETUP_COMMAND, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Claude Code setup", CLAUDE_SETUP_COMMAND))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isArabic) "نسخ أمر الإعداد" else "Copy setup command") }
                OutlinedButton(
                    onClick = {
                        val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                        if (launch != null) context.startActivity(launch)
                        else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/")))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isArabic) "فتح Termux" else "Open Termux") }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (isArabic) "ملاحظات مهمة" else "Important notes", style = MaterialTheme.typography.titleMedium)
                Text(if (isArabic)
                    "• يجب أن يكون FreeLLMAPI شغالًا على الهاتف والمنفذ 3001 متاحًا.\n• عنوان Claude Code هو أصل الخادم بدون /v1؛ البرنامج يضيف /v1/messages تلقائيًا.\n• المفتاح يُحفظ داخل Debian بصلاحية قراءة للمالك فقط، وليس داخل ملفات المشروع أو Git.\n• Claude Code يفتح في جلسة Termux منفصلة، وليس داخل نافذة دردشة Hub.\n• الموديلات والحدود تعتمد على مزوّديك وإعدادات تبويب Anthropic في FreeLLMAPI."
                    else
                    "• FreeLLMAPI must be running on this phone with port 3001 available.\n• Claude Code uses the server origin without /v1; it appends /v1/messages itself.\n• The key is stored in Debian with owner-only file permissions, never in project files or Git.\n• Claude Code opens in a separate Termux session, not embedded in the Hub chat.\n• Models and quotas depend on your providers and the Anthropic tab settings in FreeLLMAPI.")
            }
        }
    }
}
