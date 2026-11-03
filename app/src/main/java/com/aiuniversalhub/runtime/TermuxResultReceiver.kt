package com.aiuniversalhub.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra(EXTRA_RESULT_BUNDLE) ?: return
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(context, PREFS, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val receivedAt = System.currentTimeMillis()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val conversationId = intent.getIntExtra(EXTRA_CONVERSATION_ID, -1)
        val stdout = result.getString(EXTRA_STDOUT).orEmpty()
        val stderr = result.getString(EXTRA_STDERR).orEmpty()
        val exitCode = result.getInt(EXTRA_EXIT_CODE, -1)
        val error = result.getInt(EXTRA_ERROR, 0)
        val errorMessage = result.getString(EXTRA_ERROR_MESSAGE).orEmpty()

        synchronized(RESULTS_LOCK) {
            val history = try { JSONArray(prefs.getString(KEY_RESULTS, "[]")) } catch (_: Exception) { JSONArray() }
            history.put(JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("label", label)
                put("conversation_id", conversationId)
                put("stdout", stdout)
                put("stderr", stderr)
                put("exit_code", exitCode)
                put("error", error)
                put("error_message", errorMessage)
                put("received_at", receivedAt)
            })
            val bounded = JSONArray()
            for (i in (history.length() - MAX_RESULTS).coerceAtLeast(0) until history.length()) {
                bounded.put(history.getJSONObject(i))
            }
            prefs.edit()
                .putString(KEY_RESULTS, bounded.toString())
                // Keep the latest-result fields for the Runtime Center's status panel.
                .putString("label", label)
                .putInt("conversation_id", conversationId)
                .putString("stdout", stdout)
                .putString("stderr", stderr)
                .putInt("exit_code", exitCode)
                .putInt("error", error)
                .putString("error_message", errorMessage)
                .putString("stdout_original_length", result.getString(EXTRA_STDOUT_LENGTH).orEmpty())
                .putString("stderr_original_length", result.getString(EXTRA_STDERR_LENGTH).orEmpty())
                .putLong("received_at", receivedAt)
                .apply()
        }
    }

    companion object {
        private val RESULTS_LOCK = Any()
        private const val KEY_RESULTS = "results_history"
        private const val MAX_RESULTS = 50
        const val EXTRA_LABEL = "agent_label"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val PREFS = "termux_bridge_results_secure"
        const val EXTRA_RESULT_BUNDLE = "result"
        const val EXTRA_STDOUT = "stdout"
        const val EXTRA_STDERR = "stderr"
        const val EXTRA_EXIT_CODE = "exitCode"
        const val EXTRA_ERROR = "err"
        const val EXTRA_ERROR_MESSAGE = "errmsg"
        const val EXTRA_STDOUT_LENGTH = "stdout_original_length"
        const val EXTRA_STDERR_LENGTH = "stderr_original_length"
    }
}
