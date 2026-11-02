package com.aiuniversalhub.integrations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiuniversalhub.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GitHubAgentScreen(isArabic: Boolean) {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(store.getApiKey("github").orEmpty()) }
    var repositories by remember { mutableStateOf(emptyList<GitHubRepository>()) }
    var selectedRepo by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf(emptyList<String>()) }
    var issues by remember { mutableStateOf(emptyList<org.json.JSONObject>()) }
    var pulls by remember { mutableStateOf(emptyList<org.json.JSONObject>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun runTask(task: suspend () -> Unit) {
        scope.launch {
            busy = true
            status = ""
            try { task() } catch (e: Exception) { status = e.message ?: "GitHub request failed" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isArabic) "وكيل GitHub" else "GitHub Agent", style = MaterialTheme.typography.headlineSmall)
        Text(if (isArabic) "المفتاح يُحفظ مشفّرًا على الجهاز. استخدمي Token بصلاحيات أقل ما يمكن؛ القراءة تحتاج صلاحيات المستودعات، والكتابة لا تتم إلا بإجراء منفصل." else "The token is encrypted on-device. Use least-privilege repository access; write actions require separate explicit actions.")
        OutlinedTextField(token, { token = it }, modifier = Modifier.fillMaxWidth(), label = { Text("GitHub Personal Access Token") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && token.isNotBlank(), onClick = {
                store.saveApiKey("github", token.trim())
                runTask { repositories = withContext(Dispatchers.IO) { GitHubApiClient(token.trim()).listRepositories() }; if (repositories.isNotEmpty()) selectedRepo = repositories.first().fullName }
            }) { Text(if (isArabic) "حفظ وعرض المستودعات" else "Save & load repositories") }
            OutlinedButton(onClick = { store.deleteApiKey("github"); token = ""; repositories = emptyList(); selectedRepo = "" }) { Text(if (isArabic) "مسح المفتاح" else "Clear token") }
        }
        if (repositories.isNotEmpty()) {
            Text(if (isArabic) "المستودع" else "Repository", style = MaterialTheme.typography.titleMedium)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(selectedRepo, {}, readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(), label = { Text("Repository") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    repositories.forEach { repoItem -> DropdownMenuItem(text = { Text(repoItem.fullName) }, onClick = { selectedRepo = repoItem.fullName; expanded = false; branches = emptyList(); issues = emptyList(); pulls = emptyList() }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { runTask { val p=selectedRepo.split("/", limit=2); branches=withContext(Dispatchers.IO){GitHubApiClient(token).listBranches(p[0],p[1])} } }) { Text(if (isArabic) "الفروع" else "Branches") }
                OutlinedButton(enabled = !busy, onClick = { runTask { val p=selectedRepo.split("/", limit=2); issues=withContext(Dispatchers.IO){GitHubApiClient(token).listIssues(p[0],p[1])} } }) { Text("Issues") }
                OutlinedButton(enabled = !busy, onClick = { runTask { val p=selectedRepo.split("/", limit=2); pulls=withContext(Dispatchers.IO){GitHubApiClient(token).listPullRequests(p[0],p[1])} } }) { Text("Pull Requests") }
            }
            branches.forEach { Text("• $it") }
            issues.forEach { Text("#${it.optInt("number")}  ${it.optString("title")}") }
            pulls.forEach { Text("PR #${it.optInt("number")}  ${it.optString("title")}") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
    }
}
