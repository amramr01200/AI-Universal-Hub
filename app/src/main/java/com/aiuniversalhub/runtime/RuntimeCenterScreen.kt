package com.aiuniversalhub.runtime

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun RuntimeCenterScreen(isArabic: Boolean) {
    val context = LocalContext.current
    var termuxInstalled by remember { mutableStateOf(TermuxBridgeAdapter.isInstalled(context)) }
    var status by remember { mutableStateOf("") }
    var runningComponent by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isArabic) "بيئة التشغيل والحزم" else "Runtime & Packages", style = MaterialTheme.typography.headlineSmall)
        Text(if (isArabic) "يمكن إرسال أوامر الحزم المعتمدة إلى Termux بعد تثبيته ومنح إذن RUN_COMMAND وتفعيل تشغيل الأوامر الخارجية من إعدادات Termux." else "Approved package commands can be sent to Termux after installation, granting RUN_COMMAND permission, and enabling external command execution in Termux settings.")
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (isArabic) "حالة Termux" else "Termux status", style = MaterialTheme.typography.titleMedium)
                Text(if (termuxInstalled) (if (isArabic) "Termux مثبت" else "Termux installed") else (if (isArabic) "Termux غير مثبت" else "Termux not installed"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { termuxInstalled = TermuxBridgeAdapter.isInstalled(context); status = "" }) {
                        Text(if (isArabic) "فحص" else "Check")
                    }
                    if (termuxInstalled) {
                        OutlinedButton(onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage(TermuxBridgeAdapter.TERMUX_PACKAGE)
                            if (intent != null) context.startActivity(intent)
                            else status = if (isArabic) "تعذر فتح Termux" else "Could not open Termux"
                        }) { Text(if (isArabic) "فتح Termux للإعداد" else "Open Termux setup") }
                    }
                }
                Text(if (isArabic) "مهم: من تطبيق Termux فعّلي السماح بتشغيل الأوامر من تطبيقات خارجية، وراجعي إذن RUN_COMMAND من إعدادات Android." else "Important: enable external-app command execution in Termux and grant RUN_COMMAND in Android app permissions.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (status.isNotBlank()) {
            Card {
                Text(status, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        RuntimeCatalog.components.forEach { component ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(component.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(component.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (isArabic) component.purposeAr else component.purposeEn)
                    Text(if (isArabic) component.noteAr else component.noteEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text(component.installCommand, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
                    Button(
                        enabled = termuxInstalled && runningComponent == null,
                        onClick = {
                            runningComponent = component.id
                            val result = TermuxBridgeAdapter.sendApprovedCommand(context, component.id)
                            status = if (isArabic) {
                                (if (result.submitted) "تم إرسال الأمر: " else "تعذر الإرسال: ") + result.message
                            } else result.message
                            runningComponent = null
                        }
                    ) {
                        Text(if (component.id == "pip") (if (isArabic) "فحص pip" else "Check pip") else if (isArabic) "تنفيذ في Termux" else "Run in Termux")
                    }
                }
            }
        }
        Text(if (isArabic) "الإرسال لا يعني اكتمال التثبيت؛ Termux ينفذ الأمر في الخلفية، وتظهر نتيجة التنفيذ في إشعاراته/مخرجاته. لا يدعم Android تثبيت حزم Termux مباشرة دون بيئته." else "Submission is not proof of installation. Termux runs the command in the background and reports output through its notifications/output. Android cannot install Termux packages without its runtime.")
    }
}
