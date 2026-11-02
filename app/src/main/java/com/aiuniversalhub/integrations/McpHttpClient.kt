package com.aiuniversalhub.integrations

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * MCP Streamable HTTP client. Server responses and tool definitions are untrusted.
 * Tool calls require an explicit approval argument from the UI.
 */
class McpHttpClient(private val endpoint: String, private val bearerToken: String = "") {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()
    private var requestId = 0
    private var sessionId: String? = null

    private fun rpc(method: String, params: JSONObject = JSONObject()): JSONObject {
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
            "Enter a valid MCP HTTP endpoint."
        }
        val payload = JSONObject().put("jsonrpc", "2.0")
            .put("id", ++requestId).put("method", method).put("params", params)
        val builder = Request.Builder().url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (bearerToken.isNotBlank()) builder.header("Authorization", "Bearer $bearerToken")
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        return http.newCall(builder.build()).execute().use { response ->
            if (response.code == 404 && sessionId != null) {
                sessionId = null
                error("MCP session expired. Reconnect to the server.")
            }
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                error("MCP HTTP ${response.code}: ${body.take(240)}")
            }
            response.header("Mcp-Session-Id")?.let { sessionId = it }
            val body = response.body?.string().orEmpty()
            val jsonText = if (body.trimStart().startsWith("data:") || body.contains("\nevent:")) {
                body.lineSequence().filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .lastOrNull { it.startsWith("{") } ?: error("MCP server returned no JSON-RPC result.")
            } else body
            val envelope = JSONObject(jsonText)
            if (envelope.has("error")) {
                val err = envelope.optJSONObject("error")
                error(err?.optString("message")?.takeIf { it.isNotBlank() } ?: "MCP request failed.")
            }
            envelope.optJSONObject("result") ?: JSONObject()
        }
    }

    fun initialize(): JSONObject {
        val result = rpc("initialize", JSONObject()
            .put("protocolVersion", "2025-03-26")
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "AI Universal Hub").put("version", "0.1.0")))
        // The initialized notification is intentionally sent as a notification (no request id).
        val notification = JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized")
        val builder = Request.Builder().url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .post(notification.toString().toRequestBody("application/json".toMediaType()))
        if (bearerToken.isNotBlank()) builder.header("Authorization", "Bearer $bearerToken")
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 202) error("MCP initialized notification failed: HTTP ${response.code}")
        }
        return result
    }

    fun listTools(): JSONArray = rpc("tools/list").optJSONArray("tools") ?: JSONArray()

    fun connectAndListTools(): JSONArray {
        initialize()
        return listTools()
    }

    fun callTool(name: String, arguments: JSONObject, userApproved: Boolean): JSONObject {
        require(userApproved) { "Approval is required before executing an MCP tool." }
        require(name.isNotBlank()) { "MCP tool name is required." }
        return rpc("tools/call", JSONObject().put("name", name).put("arguments", arguments))
    }
}
