package com.aiuniversalhub.integrations

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * MCP Streamable HTTP JSON-RPC adapter. Server URLs and tool outputs are untrusted.
 * This adapter deliberately does not execute a tool unless the caller passes explicit approval.
 */
class McpHttpClient(private val endpoint: String, private val bearerToken: String = "") {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()
    private var requestId = 0

    private fun rpc(method: String, params: JSONObject = JSONObject()): JSONObject {
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) { "Enter a valid MCP HTTP endpoint." }
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ++requestId)
            .put("method", method)
            .put("params", params)
        val builder = Request.Builder().url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (bearerToken.isNotBlank()) builder.header("Authorization", "Bearer $bearerToken")
        return http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("MCP HTTP ${response.code}: ${text.take(240)}")
            val jsonText = text.lineSequence().lastOrNull { it.startsWith("data:") }
                ?.removePrefix("data:")?.trim()?.takeIf { it.startsWith("{") } ?: text
            val result = JSONObject(jsonText)
            if (result.has("error")) error(result.optJSONObject("error")?.optString("message") ?: "MCP request failed")
            result.optJSONObject("result") ?: JSONObject()
        }
    }

    fun initialize(): JSONObject = rpc("initialize", JSONObject()
        .put("protocolVersion", "2025-03-26")
        .put("capabilities", JSONObject())
        .put("clientInfo", JSONObject().put("name", "AI Universal Hub").put("version", "0.1.0")))

    fun listTools(): JSONArray = rpc("tools/list").optJSONArray("tools") ?: JSONArray()

    fun callTool(name: String, arguments: JSONObject, userApproved: Boolean): JSONObject {
        require(userApproved) { "Approval is required before executing an MCP tool." }
        require(name.isNotBlank()) { "MCP tool name is required." }
        return rpc("tools/call", JSONObject().put("name", name).put("arguments", arguments))
    }
}
