package com.aiuniversalhub.integrations

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubRepository(val fullName: String, val private: Boolean, val defaultBranch: String)
data class GitHubFile(val path: String, val sha: String, val text: String)

class GitHubApiClient(private val token: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(path: String): Request.Builder {
        require(token.isNotBlank()) { "Add a GitHub token in encrypted settings first." }
        return Request.Builder().url("https://api.github.com$path")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer $token")
    }

    private fun execute(request: Request): String = http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) error("GitHub HTTP ${response.code}: ${body.take(300)}")
        body
    }

    fun listRepositories(): List<GitHubRepository> {
        val array = JSONArray(execute(request("/user/repos?per_page=100&sort=updated").get().build()))
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            GitHubRepository(item.optString("full_name"), item.optBoolean("private"), item.optString("default_branch"))
        }
    }

    fun readFile(owner: String, repo: String, path: String, ref: String = "main"): GitHubFile {
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val encodedRef = java.net.URLEncoder.encode(ref, "UTF-8")
        val obj = JSONObject(execute(request("/repos/$owner/$repo/contents/$encodedPath?ref=$encodedRef").get().build()))
        val encoded = obj.optString("content").replace("\n", "")
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        return GitHubFile(path, obj.optString("sha"), bytes.toString(Charsets.UTF_8))
    }

    /** Lists branch names for the selected repository. */
    fun listBranches(owner: String, repo: String): List<String> {
        val array = JSONArray(execute(request("/repos/$owner/$repo/branches?per_page=100").get().build()))
        return (0 until array.length()).map { array.getJSONObject(it).optString("name") }
    }

    /** Lists open issues; pull requests are excluded by GitHub's issues endpoint only when filtered client-side. */
    fun listIssues(owner: String, repo: String, state: String = "open"): List<JSONObject> {
        require(state in setOf("open", "closed", "all"))
        val array = JSONArray(execute(request("/repos/$owner/$repo/issues?state=$state&per_page=100").get().build()))
        return (0 until array.length()).map { array.getJSONObject(it) }.filter { !it.has("pull_request") }
    }

    /** Lists pull requests and their branch/state metadata. */
    fun listPullRequests(owner: String, repo: String, state: String = "open"): List<JSONObject> {
        require(state in setOf("open", "closed", "all"))
        val array = JSONArray(execute(request("/repos/$owner/$repo/pulls?state=$state&per_page=100").get().build()))
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    /** Creates an issue only when the caller explicitly invokes this method. */
    fun createIssue(owner: String, repo: String, title: String, bodyText: String = ""): String {
        require(title.isNotBlank()) { "Issue title is required." }
        val body = JSONObject().put("title", title).put("body", bodyText)
        val call = request("/repos/$owner/$repo/issues")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return JSONObject(execute(call)).optString("html_url")
    }

    fun writeFile(owner: String, repo: String, path: String, content: String, message: String, branch: String, existingSha: String? = null): String {
        require(message.isNotBlank()) { "A commit message is required." }
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        if (!existingSha.isNullOrBlank()) body.put("sha", existingSha)
        val request = request("/repos/$owner/$repo/contents/$path")
            .put(body.toString().toRequestBody("application/json".toMediaType())).build()
        return JSONObject(execute(request)).optJSONObject("commit")?.optString("sha").orEmpty()
    }

    /** Creates a branch from a verified source branch. */
    fun createBranch(owner: String, repo: String, newBranch: String, sourceBranch: String): String {
        require(newBranch.isNotBlank() && sourceBranch.isNotBlank())
        val source = JSONObject(execute(request("/repos/$owner/$repo/git/ref/heads/" + java.net.URLEncoder.encode(sourceBranch, "UTF-8").replace("+", "%20")).get().build()))
        val sha = source.optJSONObject("object")?.optString("sha").orEmpty()
        require(sha.isNotBlank()) { "Could not resolve source branch commit." }
        val body = JSONObject().put("ref", "refs/heads/$newBranch").put("sha", sha)
        val call = request("/repos/$owner/$repo/git/refs")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return JSONObject(execute(call)).optString("ref")
    }

    /** Opens a pull request after the caller has reviewed the proposed branches. */
    fun createPullRequest(owner: String, repo: String, title: String, head: String, base: String, bodyText: String = ""): String {
        require(title.isNotBlank() && head.isNotBlank() && base.isNotBlank())
        require(head != base) { "Head and base branches must differ." }
        val body = JSONObject().put("title", title).put("head", head).put("base", base).put("body", bodyText)
        val call = request("/repos/$owner/$repo/pulls")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return JSONObject(execute(call)).optString("html_url")
    }

    /** Submits a pull-request review; event must be APPROVE, REQUEST_CHANGES, or COMMENT. */
    fun submitPullRequestReview(owner: String, repo: String, number: Int, bodyText: String, event: String): String {
        require(number > 0 && event in setOf("APPROVE", "REQUEST_CHANGES", "COMMENT"))
        val body = JSONObject().put("body", bodyText).put("event", event)
        val call = request("/repos/$owner/$repo/pulls/$number/reviews")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return JSONObject(execute(call)).optString("html_url")
    }
}
