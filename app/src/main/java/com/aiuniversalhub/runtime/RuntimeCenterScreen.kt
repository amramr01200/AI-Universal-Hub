package com.aiuniversalhub.runtime

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RuntimeCenterScreen(isArabic: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isArabic) "بيئة التشغيل والتحديثات" else "Runtime & Updates", style = MaterialTheme.typography.headlineSmall)
        Text(if (isArabic) "كتالوج المكونات الأساسية المقترحة. التثبيت والتحديث الفعليان يحتاجان ربط محرك الحزم ببيئة تشغيل متوافقة." else "Curated core components. Actual installation and updates require connecting the package engine to a compatible runtime.")
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (isArabic) "حالة المحرك" else "Engine status", style = MaterialTheme.typography.titleMedium)
                Text(if (isArabic) "كتالوج فقط — لم يتم تثبيت Python أو Node.js أو غيرهما تلقائيًا بعد." else "Catalog only — Python, Node.js and other packages are not automatically installed yet.")
                Text(if (isArabic) "لن ندّعي وجود تحديث قبل قراءة الإصدار المثبت والإصدار المتاح من المصدر الرسمي." else "The app will not claim an update exists until installed and available versions are checked against an official source.")
            }
        }
        RuntimeCatalog.components.forEach { component ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(component.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(component.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (isArabic) component.purposeAr else component.purposeEn)
                    Text(if (isArabic) component.noteAr else component.noteEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text(if (isArabic) "أمر التثبيت المقترح:" else "Suggested install command:", style = MaterialTheme.typography.labelMedium)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(component.installCommand, modifier = Modifier.fillMaxWidth().padding(9.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text(if (isArabic) "ملاحظة: Docker الكامل يحتاج مضيف Linux يدعم الحاويات أو خدمة بعيدة؛ Android غير المروّت لا يوفر Docker daemon تقليديًا." else "Note: Full Docker requires a Linux host with container support or a remote service; a non-root Android device does not provide a conventional Docker daemon.")
    }
}
